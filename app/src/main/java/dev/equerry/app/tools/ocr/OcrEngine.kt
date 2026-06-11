package dev.equerry.app.tools.ocr

import dev.equerry.app.providers.drivers.ChatImage

/**
 * On-device OCR over a captured screen image — the single OCR entry point for the screen-context
 * fallback. Takes the transient [ChatImage] (bytes + mime) captured from the screen so callers stay
 * free of Android bitmap decoding; implementations decode and recognise fully on-device, and the
 * bytes are never persisted. Returns "" when nothing is recognised or the engine is unavailable, so
 * callers degrade gracefully (a blank result routes to the blank-screen path, never a crash).
 */
interface OcrEngine {
    suspend fun recognise(image: ChatImage): String
}
