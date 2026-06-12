package dev.equerry.app.voice

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.drivers.ChatHttpClient
import dev.equerry.app.providers.drivers.SpeechRequestBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.coroutines.resume

/** Synthesizes one utterance's audio bytes via the remote speech endpoint. */
interface SpeechSynthesizer {
    /** @throws Throwable on any transport/HTTP failure — surfaced via the TTS onError callback. */
    suspend fun synthesize(text: String): ByteArray
}

/** Plays back synthesized audio clips, gapless and in submission order. */
interface ClipPlayer {
    /** Ready the player; false if it cannot be initialized. */
    suspend fun init(): Boolean

    /** Queue a clip to play after any already-queued clips. */
    fun enqueue(clip: ByteArray)

    /** Suspend until every queued clip has finished playing (or returns at once if nothing is queued). */
    suspend fun awaitDone()

    fun stop()
    fun release()
}

/**
 * Remote text-to-speech: POST each utterance to the provider's speech endpoint and play the returned
 * audio clip through a [ClipPlayer]. A single FIFO worker consumes [speak] calls in submission order,
 * so clips are POSTed per utterance (honouring both phase-05 speak timings — whole-reply is one
 * utterance, sentence-by-sentence is many) and enqueued strictly in order even though the POSTs run
 * concurrently with the caller. [awaitDone] drains the worker (a barrier command) then waits for
 * playback to finish — this is what gates the mic re-arm so STT never captures Equerry's own voice.
 *
 * Failures never throw into the caller (c-5/c-6): a [speak] before/without a Ready [init] is a no-op,
 * and a transport failure is reported through [onError] as a [RemoteAudioError] (derived from status
 * /exception type only — r-03). The OkHttp / ExoPlayer machinery lives behind the [synth]/[player]
 * seams (production wired via [fromProfile]); tests drive fakes. Mirrors [SystemTextToSpeech].
 */
class RemoteTextToSpeech(
    private val synth: SpeechSynthesizer,
    private val player: ClipPlayer,
    private val scope: CoroutineScope,
    private val onError: (RemoteAudioError) -> Unit = {},
) : TextToSpeech {

    private val channel = Channel<Cmd>(Channel.UNLIMITED)
    private var worker: Job? = null

    @Volatile
    private var ready = false

    override suspend fun init(): TtsInitResult {
        ready = player.init()
        if (ready && worker == null) {
            worker = scope.launch {
                for (cmd in channel) {
                    when (cmd) {
                        is Cmd.Speak -> {
                            val clip = try {
                                synth.synthesize(cmd.text)
                            } catch (c: CancellationException) {
                                throw c
                            } catch (t: Throwable) {
                                onError(mapError(t))
                                null
                            }
                            clip?.let { player.enqueue(it) }
                        }
                        is Cmd.Barrier -> cmd.done.complete(Unit)
                    }
                }
            }
        }
        return if (ready) TtsInitResult.Ready else TtsInitResult.Failed
    }

    override fun speak(utterance: String) {
        if (!ready) return
        channel.trySend(Cmd.Speak(utterance))
    }

    override suspend fun speakSentences(sentences: Flow<String>) {
        // Sentences are POSTed (and so played) in the order they stream in.
        sentences.collect { speak(it) }
    }

    override suspend fun awaitDone() {
        if (!ready) return
        // Barrier completes only after the worker has processed every prior Speak (FIFO), so all
        // clips are enqueued before we wait on playback to drain.
        val barrier = CompletableDeferred<Unit>()
        channel.trySend(Cmd.Barrier(barrier))
        barrier.await()
        player.awaitDone()
    }

    override fun stop() = player.stop()

    override fun shutdown() {
        worker?.cancel()
        worker = null
        channel.close()
        player.release()
    }

    private fun mapError(t: Throwable): RemoteAudioError =
        if (t is RemoteAudioHttpException) RemoteAudioError.forStatus(t.code) else RemoteAudioError.forException(t)

    private sealed interface Cmd {
        data class Speak(val text: String) : Cmd
        data class Barrier(val done: CompletableDeferred<Unit>) : Cmd
    }

    companion object {
        /**
         * Production factory: OkHttp speech POST + ExoPlayer playback. The HTTP/audio seams are
         * framework-bound and verified at integration; the worker/ordering core is unit-tested.
         */
        fun fromProfile(
            context: Context,
            profile: ProviderProfile,
            key: String,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            client: OkHttpClient = ChatHttpClient.create(),
            onError: (RemoteAudioError) -> Unit = {},
        ): RemoteTextToSpeech = RemoteTextToSpeech(
            synth = OkHttpSpeechSynthesizer(client, profile.baseUrl, profile.model, profile.ttsVoice, key),
            player = ExoClipPlayer(context.applicationContext),
            scope = scope,
            onError = onError,
        )
    }
}

/** Framework-bound synthesizer: OkHttp POST to `audio/speech`, returning the raw mp3 bytes. Integration-edge. */
internal class OkHttpSpeechSynthesizer(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val model: String,
    private val voice: String?,
    private val key: String,
) : SpeechSynthesizer {

    override suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val built = SpeechRequestBuilder.build(model, text, voice, key)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/" + built.path)
            .post(built.body.toRequestBody(JSON_MEDIA))
            .apply {
                built.headers.forEach { (name, value) ->
                    if (!name.equals("Content-Type", ignoreCase = true)) header(name, value)
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RemoteAudioHttpException(response.code)
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

/**
 * Framework-bound player: a Media3 [ExoPlayer] playing a gapless playlist of clip temp files. All
 * player access is marshalled onto the main looper (ExoPlayer is single-threaded). Integration-edge.
 */
private class ExoClipPlayer(private val context: Context) : ClipPlayer {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var player: ExoPlayer? = null
    private val tempFiles = mutableListOf<File>()

    override suspend fun init(): Boolean = withContext(Dispatchers.Main) {
        player = ExoPlayer.Builder(context).build().apply { playWhenReady = true }
        true
    }

    override fun enqueue(clip: ByteArray) {
        val file = File.createTempFile("tts", ".mp3", context.cacheDir).apply { writeBytes(clip) }
        synchronized(tempFiles) { tempFiles.add(file) }
        handler.post {
            val p = player ?: return@post
            // Once the queue has drained the player sits at STATE_ENDED on the last item; appending a
            // new item does NOT make ExoPlayer advance to it, so a continuous session would only ever
            // play its first utterance. Seek to the freshly-appended item before (re)preparing so each
            // turn's clip actually plays (and awaitDone then waits on real playback, not a stale ENDED).
            val drained = p.playbackState == Player.STATE_ENDED || p.playbackState == Player.STATE_IDLE
            p.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            if (drained) p.seekTo(p.mediaItemCount - 1, 0L)
            p.playWhenReady = true
            p.prepare()
        }
    }

    override suspend fun awaitDone() {
        if (player == null) return
        suspendCancellableCoroutine { cont ->
            handler.post {
                val p = player
                if (p == null || p.playbackState == Player.STATE_ENDED || p.mediaItemCount == 0) {
                    if (cont.isActive) cont.resume(Unit)
                    return@post
                }
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            p.removeListener(this)
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // A decode/playback failure transitions to STATE_IDLE, never STATE_ENDED — so
                        // without this the turn would hang forever in Speaking and the mic never re-arms
                        // (the "freeze"). Treat an error as done so the loop settles and re-arms.
                        p.removeListener(this)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                cont.invokeOnCancellation { handler.post { p.removeListener(listener) } }
                p.addListener(listener)
            }
        }
    }

    override fun stop() {
        handler.post {
            player?.stop()
            player?.clearMediaItems()
        }
    }

    override fun release() {
        handler.post {
            player?.release()
            player = null
        }
        synchronized(tempFiles) {
            tempFiles.forEach { it.delete() }
            tempFiles.clear()
        }
    }
}
