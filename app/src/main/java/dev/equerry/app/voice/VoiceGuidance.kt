package dev.equerry.app.voice

/**
 * A single user-facing guidance message for a voice-flow failure. Always key-free (r-03):
 * [message] is built here or copied from an already-redacted ChatError message, never from a raw
 * exception/response. [canOpenSettings] signals the UI may offer a path to app settings.
 */
data class VoiceGuidance(val message: String, val canOpenSettings: Boolean = false)

/**
 * Builds the one guidance surface for every voice failure mode (c-5), so the wording stays
 * consistent and the mic-denied message is identical to the in-session/settings path ([MicPermission]).
 */
object VoiceGuidanceFactory {

    /** RECORD_AUDIO not granted — reuses the single locked mic_permission message. */
    fun micDenied(): VoiceGuidance =
        VoiceGuidance(MicPermission.deniedState.guidance, canOpenSettings = MicPermission.deniedState.openSettings)

    /** No provider mapped to the CHAT slot — nothing to send the question to. */
    fun noChatProvider(): VoiceGuidance =
        VoiceGuidance("No chat provider configured. Add one and map it to the CHAT slot to talk to Equerry.")

    /** Speech recognition failed. PermissionDenied collapses to the shared mic-denied guidance. */
    fun sttError(error: SttError): VoiceGuidance = when (error) {
        SttError.NoMatch -> VoiceGuidance("Didn't catch that — try speaking again.")
        SttError.Timeout -> VoiceGuidance("I didn't hear anything — try speaking again.")
        SttError.Busy -> VoiceGuidance("The microphone is busy. Try again in a moment.")
        SttError.PermissionDenied -> micDenied()
        SttError.Unavailable -> VoiceGuidance("Speech recognition isn't available on this device.")
    }

    /** TTS engine missing or failed to init — the reply is shown but cannot be spoken. */
    fun ttsUnavailable(): VoiceGuidance =
        VoiceGuidance("Couldn't speak the reply aloud — showing it instead.")

    /** The provider reply failed mid-stream. [message] is the already-redacted ChatError message. */
    fun replyError(message: String): VoiceGuidance = VoiceGuidance(message)
}
