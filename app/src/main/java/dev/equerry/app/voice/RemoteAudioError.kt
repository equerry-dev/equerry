package dev.equerry.app.voice

import java.io.IOException

/**
 * Why a remote STT/TTS call failed, on a small UI-routable set. Derived ONLY from the HTTP status
 * code or the exception type — never by copying a response body or an exception message — so an
 * upstream error that echoes the API key can never reach a guidance surface (r-03).
 */
sealed interface RemoteAudioError {
    /** The provider rejected the credentials (401 / 403). */
    data object Auth : RemoteAudioError

    /** The provider couldn't be reached at all (transport-level IO failure). */
    data object Network : RemoteAudioError

    /** Any other non-success response or unexpected failure. */
    data object Unavailable : RemoteAudioError

    companion object {
        /** Map a response status code to a routable error. Body is intentionally never consulted. */
        fun forStatus(code: Int): RemoteAudioError = when (code) {
            401, 403 -> Auth
            else -> Unavailable
        }

        /** Map a thrown cause by TYPE only. IO failures are network; everything else is unavailable. */
        fun forException(cause: Throwable): RemoteAudioError =
            if (cause is IOException) Network else Unavailable
    }
}
