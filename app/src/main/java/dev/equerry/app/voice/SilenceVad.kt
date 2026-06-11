package dev.equerry.app.voice

/**
 * Pure, frame-by-frame trailing-silence detector for remote STT capture. Remote transcribe
 * endpoints are record-then-POST with no system VAD, so this reproduces the hands-free
 * auto-stop-on-silence that phase 05 locked (no tap-to-stop): once speech has started, a run of
 * [silenceFrames] consecutive sub-[amplitudeThreshold] frames returns shouldStop=true at exactly
 * the window boundary.
 *
 * Stateful and single-use: feed each captured frame's amplitude (e.g. peak |sample| of a PCM frame)
 * to [onFrame] in order; the first `true` means "stop recording and POST the clip". Leading silence
 * before the first loud frame never trips it (we wait for the user to actually start speaking).
 *
 * Defaults assume ~16 kHz mono in ~100 ms frames: 8 silent frames ≈ 0.8 s of trailing silence.
 */
class SilenceVad(
    private val amplitudeThreshold: Int = DEFAULT_AMPLITUDE_THRESHOLD,
    private val silenceFrames: Int = DEFAULT_SILENCE_FRAMES,
) {
    private var speechStarted = false
    private var consecutiveSilent = 0

    /**
     * Feed one frame's amplitude. Returns true the moment a trailing-silence run reaches the
     * configured window (and on every frame after, while silence persists). A loud frame resets the
     * run, so a single mid-speech dip never trips it.
     */
    fun onFrame(amplitude: Int): Boolean {
        val isSilent = amplitude < amplitudeThreshold
        if (!speechStarted) {
            if (!isSilent) speechStarted = true
            // Nothing can stop the turn until the user has actually started speaking.
            return false
        }
        consecutiveSilent = if (isSilent) consecutiveSilent + 1 else 0
        return consecutiveSilent >= silenceFrames
    }

    companion object {
        const val DEFAULT_AMPLITUDE_THRESHOLD = 1500
        const val DEFAULT_SILENCE_FRAMES = 8
    }
}
