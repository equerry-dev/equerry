package dev.equerry.app.providers.drivers

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ChatHttpClientTest {

    private val key = "sk-secret-ABCDEF1234567890"

    /** Drives the interceptor over a canned 200 response, returning everything it logged. */
    private fun captureLog(request: Request): String {
        val captured = StringBuilder()
        val interceptor = ChatHttpClient.redactingLogger { captured.appendLine(it) }
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("ok".toResponseBody("text/plain".toMediaType()))
            .build()
        interceptor.intercept(FakeChain(request, response))
        return captured.toString()
    }

    @Test
    fun key_never_in_logged_request() {
        val request = Request.Builder()
            .url("https://api.example.com/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("x-api-key", key)
            .build()
        val log = captureLog(request)
        assertFalse("redacted headers must not leak the key", log.contains(key))
    }

    @Test
    fun logging_is_live() {
        val request = Request.Builder()
            .url("https://api.example.com/v1/chat/completions")
            .header("Authorization", "Bearer $key")
            .build()
        val log = captureLog(request)
        // A non-key request field is observable -> logging is genuinely installed, not silently off.
        assertTrue("expected the request URL in the log (logging must be live)", log.contains("api.example.com"))
        // ...while the key itself is still masked.
        assertFalse(log.contains(key))
    }

    /** Minimal Interceptor.Chain: HttpLoggingInterceptor only needs request(), connection(), proceed(). */
    private class FakeChain(
        private val request: Request,
        private val response: Response,
    ) : Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response = response
        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException("not used by the logger")
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
