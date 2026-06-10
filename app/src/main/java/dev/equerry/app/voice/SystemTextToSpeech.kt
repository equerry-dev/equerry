package dev.equerry.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTextToSpeech
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

/** Framework-bound [TtsEngine] over a real [AndroidTextToSpeech]. Verified at integration (t-10). */
private class AndroidTtsEngine(
    private val tts: AndroidTextToSpeech,
) : TtsEngine {
    override fun speak(text: String) {
        tts.speak(text, AndroidTextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
