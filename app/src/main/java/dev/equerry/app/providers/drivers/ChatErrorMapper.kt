package dev.equerry.app.providers.drivers

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.UnknownHostException

/**
 * Turns transport/HTTP/parse outcomes into a [ChatError]. Deliberately maps by status code and
 * exception *type* only — it never copies an exception message or response body into the result,
 * so an API key that happens to appear in some upstream string can never reach the UI (r-03).
 */
object ChatErrorMapper {

    /** Maps a non-success HTTP status. 401/403 -> [ChatError.Auth]; any other code -> [ChatError.Http]. */
    fun fromHttpStatus(code: Int): ChatError = when (code) {
        401, 403 -> ChatError.Auth
        else -> ChatError.Http(code)
    }

    /** Maps a thrown exception. Connectivity/IO -> [ChatError.Network]; parse failure -> [ChatError.Malformed]. */
    fun fromThrowable(t: Throwable): ChatError = when (t) {
        is UnknownHostException -> ChatError.Network
        is IOException -> ChatError.Network
        is SerializationException -> ChatError.Malformed
        else -> ChatError.Malformed
    }
}
