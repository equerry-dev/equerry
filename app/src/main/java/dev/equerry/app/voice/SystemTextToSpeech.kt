package dev.equerry.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Minimal seam over the [AndroidTextToSpeech] calls this feature uses, so [SystemTextToSpeech]'s
 * readiness gating is unit-testable against a fake. The real engine wrapper is framework-bound.
 */
interface TtsEngine {
    fun speak(text: String)

    /** Suspend until the engine has finished speaking everything queued. */
    suspend fun awaitDone()

    fun stop()
    fun shutdown()
}

/**
 * System TTS exposed through the [TextToSpeech] seam. The engine is obtained via [engineFactory]
 * (which reports init status through the supplied callback and returns null when no engine exists),
 * so tests drive every init outcome with a fake; production uses [fromContext]. [speak] is gated on
 * a Ready init, so a failed/missing engine never throws (c-5).
 */
class SystemTextToSpeech(
    private val engineFactory: (onInit: (status: Int) -> Unit) -> TtsEngine?,
) : TextToSpeech {

    private var engine: TtsEngine? = null

    @Volatile
    private var ready = false

    override suspend fun init(): TtsInitResult = suspendCancellableCoroutine { cont ->
        var settled = false
        val created = engineFactory { status ->
            if (settled) return@engineFactory
            settled = true
            ready = status == AndroidTextToSpeech.SUCCESS
            cont.resume(if (ready) TtsInitResult.Ready else TtsInitResult.Failed)
        }
        if (created == null) {
            if (!settled) {
                settled = true
                cont.resume(TtsInitResult.MissingEngine)
            }
            return@suspendCancellableCoroutine
        }
        engine = created
        cont.invokeOnCancellation { created.shutdown() }
    }

    override fun speak(utterance: String) {
        if (!ready) return
        engine?.speak(utterance)
    }

    override suspend fun speakSentences(sentences: Flow<String>) {
        sentences.collect { speak(it) }
    }

    override suspend fun awaitDone() {
        if (!ready) return
        engine?.awaitDone()
    }

    override fun stop() {
        engine?.stop()
    }

    override fun shutdown() {
        ready = false
        engine?.shutdown()
        engine = null
    }

    companion object {
        /** Production factory: a [TtsEngine] backed by a real [AndroidTextToSpeech]. */
        fun fromContext(context: Context): SystemTextToSpeech = SystemTextToSpeech { onInit ->
            AndroidTtsEngine(AndroidTextToSpeech(context.applicationContext) { status -> onInit(status) })
        }
    }
}

/**
 * Framework-bound [TtsEngine] over a real [AndroidTextToSpeech]. Tracks queued utterances via an
 * [UtteranceProgressListener] so [awaitDone] can suspend until playback actually finishes. Verified
 * at integration (manual, t-10).
 */
private class AndroidTtsEngine(
    private val tts: AndroidTextToSpeech,
) : TtsEngine {

    private val lock = Any()
    private val pending = mutableSetOf<String>()
    private var awaiter: CompletableDeferred<Unit>? = null

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = settle(utteranceId)

            @Deprecated("Deprecated in API 21, still required by the abstract class")
            override fun onError(utteranceId: String?) = settle(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = settle(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = settle(utteranceId)
        })
    }

    /** Drop a finished/stopped utterance; complete the awaiter once nothing is left to speak. */
    private fun settle(id: String?) {
        val toComplete: CompletableDeferred<Unit>?
        synchronized(lock) {
            if (id != null) pending.remove(id)
            toComplete = if (pending.isEmpty()) awaiter else null
            if (pending.isEmpty()) awaiter = null
        }
        toComplete?.complete(Unit)
    }

    override fun speak(text: String) {
        val id = UUID.randomUUID().toString()
        synchronized(lock) { pending.add(id) }
        tts.speak(text, AndroidTextToSpeech.QUEUE_ADD, null, id)
    }

    override suspend fun awaitDone() {
        val deferred: CompletableDeferred<Unit>
        synchronized(lock) {
            if (pending.isEmpty()) return
            deferred = awaiter ?: CompletableDeferred<Unit>().also { awaiter = it }
        }
        deferred.await()
    }

    override fun stop() {
        tts.stop()
        // stop() cancels queued speech; release any awaiter and clear the queue.
        val toComplete: CompletableDeferred<Unit>?
        synchronized(lock) {
            pending.clear()
            toComplete = awaiter
            awaiter = null
        }
        toComplete?.complete(Unit)
    }

    override fun shutdown() {
        stop()
        tts.shutdown()
    }
}
