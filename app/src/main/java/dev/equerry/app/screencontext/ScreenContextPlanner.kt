package dev.equerry.app.screencontext

/**
 * Pure routing for a screen-context query. Given what was captured ([ScreenContext]) and the current
 * slot mappings, it decides which [ScreenQueryPlan] to run — no I/O, no provider calls. Encodes the
 * locked decisions `vision_capability` (image when the VISION model is image-capable and a
 * screenshot arrived, else the text path) and `blank_screen` (no capture -> honest note).
 */
object ScreenContextPlanner {

    /**
     * @param visionMapped       whether a provider is mapped to the VISION slot
     * @param visionImageCapable whether that VISION provider can accept an image (best-effort)
     * @param chatMapped         whether a provider is mapped to the CHAT slot
     */
    fun plan(
        context: ScreenContext,
        visionMapped: Boolean,
        visionImageCapable: Boolean,
        chatMapped: Boolean,
    ): ScreenQueryPlan = when {
        // Nothing to route to: the user must configure a provider first.
        !visionMapped && !chatMapped -> ScreenQueryPlan.Unconfigured

        // A provider is mapped, but the screen yielded nothing to send or OCR.
        !context.hasText && !context.hasScreenshot -> ScreenQueryPlan.BlankScreen

        // VISION wins when mapped: image to a capable model, otherwise the text path.
        visionMapped ->
            if (visionImageCapable && context.hasScreenshot) ScreenQueryPlan.ImageToVision
            else ScreenQueryPlan.TextToVision

        // No VISION provider, but CHAT is mapped: OCR the screenshot, send the text to CHAT.
        else -> ScreenQueryPlan.OcrThenChat
    }
}
