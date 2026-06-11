/**
 * Screen-context: capturing what's on the current screen (Assist text + optional transient
 * screenshot) and deciding how to answer a "what's on this screen?" query. The pure
 * [ScreenContextPlanner] picks the route — image to a multimodal VISION provider, Assist/OCR text
 * to VISION or CHAT, or a guidance path — without performing any I/O. Screen pixels are transient
 * and never persisted (spec §07: r-01 Assist-API-only, screenshot never persisted).
 */
package dev.equerry.app.screencontext
