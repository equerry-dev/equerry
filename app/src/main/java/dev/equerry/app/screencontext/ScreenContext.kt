package dev.equerry.app.screencontext

import dev.equerry.app.providers.drivers.ChatImage

/**
 * What was captured from the current screen for a screen-context query: the Assist-extracted [text]
 * (may be blank) and the transient [screenshot] (null when none arrived). Both come only from the
 * Assist API (r-01); the screenshot is request-only payload and is never persisted.
 */
data class ScreenContext(
    val text: String,
    val screenshot: ChatImage?,
) {
    val hasText: Boolean get() = text.isNotBlank()
    val hasScreenshot: Boolean get() = screenshot != null
}

/**
 * The route a screen-context query should take, chosen by [ScreenContextPlanner]. The planner only
 * decides the route; the caller (ChatViewModel, t-7) resolves the actual text (Assist text, or OCR
 * of the screenshot when Assist text is blank) and performs the send.
 */
sealed interface ScreenQueryPlan {
    /** Send the screenshot image to the multimodal VISION provider. */
    data object ImageToVision : ScreenQueryPlan

    /** Send the screen's text to the VISION provider (no screenshot, or a text-only VISION model). */
    data object TextToVision : ScreenQueryPlan

    /** No VISION provider mapped: OCR the screenshot and send the recognised text to CHAT. */
    data object OcrThenChat : ScreenQueryPlan

    /** Nothing usable was captured (no text and no screenshot): show the blank-screen note. */
    data object BlankScreen : ScreenQueryPlan

    /** Neither a VISION nor a CHAT provider is mapped: show the unconfigured guidance. */
    data object Unconfigured : ScreenQueryPlan
}
