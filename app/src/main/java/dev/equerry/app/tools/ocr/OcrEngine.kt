package dev.equerry.app.tools.ocr

import android.graphics.Bitmap

/**
 * On-device OCR over a screenshot bitmap — the single OCR entry point for the screen-context
 * fallback. Implementations recognise text fully on-device; the bitmap is transient and never
 * persisted. Returns "" when nothing is recognised or the engine is unavailable, so callers can
 * degrade gracefully (a blank result routes to the blank-screen path, never a crash).
 */
interface OcrEngine {
    suspend fun recognise(bitmap: Bitmap): String
}
