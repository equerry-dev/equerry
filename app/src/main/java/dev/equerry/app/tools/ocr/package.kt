/**
 * On-device OCR: the [OcrEngine] seam and its FOSS Tesseract implementation. This is the
 * screen-context fallback engine — when no VISION provider is mapped, the assist screenshot is
 * OCR'd here and the recognised text is sent to the CHAT provider (spec decision `fallback_engine`).
 * The interface is the only OCR entry point so callers (and tests) depend on the seam, never the
 * native engine. OCR runs fully on-device; no screen pixels leave the device.
 */
package dev.equerry.app.tools.ocr
