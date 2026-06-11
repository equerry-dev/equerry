package dev.equerry.app.screencontext

import dev.equerry.app.providers.drivers.ChatImage
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenContextPlannerTest {

    private val shot = ChatImage(byteArrayOf(1, 2, 3), "image/png")
    private fun ctx(text: String = "hello screen", screenshot: ChatImage? = shot) =
        ScreenContext(text, screenshot)

    @Test
    fun vision_image_capable_with_a_screenshot_sends_the_image() {
        val plan = ScreenContextPlanner.plan(ctx(), visionMapped = true, visionImageCapable = true, chatMapped = false)
        assertEquals(ScreenQueryPlan.ImageToVision, plan)
    }

    @Test
    fun vision_without_a_screenshot_falls_back_to_the_text_path() {
        val plan = ScreenContextPlanner.plan(
            ctx(screenshot = null), visionMapped = true, visionImageCapable = true, chatMapped = false,
        )
        assertEquals(ScreenQueryPlan.TextToVision, plan)
    }

    @Test
    fun text_only_vision_model_uses_the_text_path_even_with_a_screenshot() {
        val plan = ScreenContextPlanner.plan(ctx(), visionMapped = true, visionImageCapable = false, chatMapped = true)
        assertEquals(ScreenQueryPlan.TextToVision, plan)
    }

    @Test
    fun no_vision_but_chat_mapped_routes_through_ocr_then_chat() {
        val plan = ScreenContextPlanner.plan(ctx(), visionMapped = false, visionImageCapable = false, chatMapped = true)
        assertEquals(ScreenQueryPlan.OcrThenChat, plan)
    }

    @Test
    fun no_text_and_no_screenshot_is_a_blank_screen() {
        val plan = ScreenContextPlanner.plan(
            ctx(text = "  ", screenshot = null), visionMapped = true, visionImageCapable = true, chatMapped = true,
        )
        assertEquals(ScreenQueryPlan.BlankScreen, plan)
    }

    @Test
    fun nothing_mapped_is_unconfigured_even_with_a_capture() {
        val plan = ScreenContextPlanner.plan(ctx(), visionMapped = false, visionImageCapable = false, chatMapped = false)
        assertEquals(ScreenQueryPlan.Unconfigured, plan)
    }
}
