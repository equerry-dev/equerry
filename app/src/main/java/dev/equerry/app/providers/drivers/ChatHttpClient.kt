package dev.equerry.app.providers.drivers

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * The single shared OkHttpClient for every chat driver. Logging is installed and LIVE — the
 * request line and header names are logged so traffic is debuggable — but it operates at
 * [HttpLoggingInterceptor.Level.HEADERS] (request/response bodies are never logged) and redacts the
 * Authorization / x-api-key headers. Combined with builders that keep keys in headers only (never in
 * the URL or query string), an API key can never reach the log sink or a URL (r-03).
 */
object ChatHttpClient {

    fun create(
        logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(redactingLogger(logger))
            // Bound every request so a stalled backend (a self-hosted model mid-swap or OOM) fails in
            // finite time instead of hanging a voice turn. readTimeout is generous so a cold model load
            // (e.g. swapping the vision model in) still completes; connect fails fast when the box is down.
            // No callTimeout: a long streamed reply is legitimate and must not be cut off.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    /**
     * The configured redacting logging interceptor. Exposed (internal) so tests can drive it with a
     * capturing sink and prove both that logging is live and that the key never appears.
     */
    internal fun redactingLogger(
        logger: HttpLoggingInterceptor.Logger,
    ): HttpLoggingInterceptor =
        HttpLoggingInterceptor(logger).apply {
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
            redactHeader("x-api-key")
        }
}
