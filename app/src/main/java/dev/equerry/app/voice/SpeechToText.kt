package dev.equerry.app.voice

import kotlinx.coroutines.flow.Flow

/** A single event from system speech-to-text during one listening session. */
sealed interface SttEvent {
    /** Interim hypothesis while the user is still speaking. */
    data class Partial(val text: String) : SttEvent

    /** The final recognized utterance. Terminal — the flow completes after this. */
    data class Final(val text: String) : SttEvent

    /** The recognizer detected the end of speech — used to auto-stop and auto-send (turn_control). */
    data object EndOfSpeech : SttEvent

    /** Recognition failed. Terminal — the flow completes after this. */
    data class Error(val error: SttError) : SttEvent
}

/** Why speech recognition failed. Collapses every SpeechRecognizer error onto a small, UI-routable set (c-5). */
sealed interface SttError {
    /** No speech matched (ERROR_NO_MATCH). */
    data object NoMatch : SttError

    /** Speech input timed out (ERROR_SPEECH_TIMEOUT). */
    data object Timeout : SttError

    /** The recognizer was busy (ERROR_RECOGNIZER_BUSY). */
    data object Busy : SttError

    /** RECORD_AUDIO not granted (ERROR_INSUFFICIENT_PERMISSIONS). */
    data object PermissionDenied : SttError

    /** Recognizer unavailable for any other reason (client/network/server/audio). */
    data object Unavailable : SttError
}

/**
 * System speech-to-text seam. [listen] emits [SttEvent]s for one listening session and completes
 * when recognition ends (final result or error).
 */
interface SpeechToText {
    fun listen(): Flow<SttEvent>
}
