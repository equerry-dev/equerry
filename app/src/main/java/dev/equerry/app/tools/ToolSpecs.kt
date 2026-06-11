package dev.equerry.app.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * One callable tool the model may invoke. [parameters] is a JSON-Schema `object` describing the
 * arguments; [dev.equerry.app.providers.drivers.ChatRequestBuilder] translates it into each
 * provider's tools wire shape. The exposed set is fixed at exactly five (tool_scope locked decision).
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/** Single source of truth for the action tools Equerry exposes. Exactly five (tool_scope). */
object ToolSpecs {

    const val SET_TIMER = "set_timer"
    const val SET_ALARM = "set_alarm"
    const val CREATE_CALENDAR_EVENT = "create_calendar_event"
    const val DRAFT_EMAIL = "draft_email"
    const val DRAFT_SMS = "draft_sms"

    val ALL: List<ToolSpec> = listOf(
        ToolSpec(
            name = SET_TIMER,
            description = "Start a countdown timer on the device clock.",
            parameters = schema(required = listOf("seconds")) {
                stringOrInt("seconds", "integer", "Timer length in seconds.")
                stringOrInt("label", "string", "Optional label for the timer.")
            },
        ),
        ToolSpec(
            name = SET_ALARM,
            description = "Set an alarm for a specific time of day.",
            parameters = schema(required = listOf("hour", "minute")) {
                stringOrInt("hour", "integer", "Hour in 24-hour time (0-23).")
                stringOrInt("minute", "integer", "Minute (0-59).")
                stringOrInt("label", "string", "Optional label for the alarm.")
            },
        ),
        ToolSpec(
            name = CREATE_CALENDAR_EVENT,
            description = "Create a calendar event, opening the calendar app pre-filled for the user to save.",
            parameters = schema(required = listOf("title", "start")) {
                stringOrInt("title", "string", "Event title.")
                stringOrInt("start", "string", "Start time, ISO-8601 (e.g. 2026-06-11T14:00).")
                stringOrInt("end", "string", "Optional end time, ISO-8601.")
                stringOrInt("location", "string", "Optional location.")
            },
        ),
        ToolSpec(
            name = DRAFT_EMAIL,
            description = "Compose an email, opening the mail app pre-filled. The user reviews and sends it.",
            parameters = schema(required = listOf("to")) {
                stringOrInt("to", "string", "Recipient email address.")
                stringOrInt("subject", "string", "Optional subject line.")
                stringOrInt("body", "string", "Optional message body.")
            },
        ),
        ToolSpec(
            name = DRAFT_SMS,
            description = "Compose a text message, opening the SMS app pre-filled. The user reviews and sends it.",
            parameters = schema(required = listOf("body")) {
                stringOrInt("to", "string", "Optional recipient phone number.")
                stringOrInt("body", "string", "Message text.")
            },
        ),
    )

    private fun JsonObjectBuilder.stringOrInt(name: String, type: String, description: String) {
        putJsonObject(name) {
            put("type", type)
            put("description", description)
        }
    }

    private fun schema(required: List<String>, properties: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties", properties)
            putJsonArray("required") { required.forEach { add(it) } }
        }
}
