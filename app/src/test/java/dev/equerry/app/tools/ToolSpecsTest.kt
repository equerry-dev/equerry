package dev.equerry.app.tools

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolSpecsTest {

    @Test
    fun exposes_exactly_the_five_locked_tools_in_order() {
        // tool_scope is locked at five — add, rename, or drop one and this fails.
        assertEquals(
            listOf("set_timer", "set_alarm", "create_calendar_event", "draft_email", "draft_sms"),
            ToolSpecs.ALL.map { it.name },
        )
    }

    @Test
    fun each_tool_is_an_object_schema_with_its_required_params() {
        val required = ToolSpecs.ALL.associate { spec ->
            spec.name to spec.parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        }
        assertEquals(listOf("seconds"), required["set_timer"])
        assertEquals(listOf("hour", "minute"), required["set_alarm"])
        assertEquals(listOf("title", "start"), required["create_calendar_event"])
        assertEquals(listOf("to"), required["draft_email"])
        assertEquals(listOf("body"), required["draft_sms"])

        ToolSpecs.ALL.forEach { spec ->
            assertEquals("object", spec.parameters["type"]!!.jsonPrimitive.content)
        }
    }
}
