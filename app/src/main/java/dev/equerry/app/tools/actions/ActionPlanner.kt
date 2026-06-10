package dev.equerry.app.tools.actions

import dev.equerry.app.providers.drivers.ChatToken
import dev.equerry.app.tools.ToolSpecs
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure mapper from parsed [ChatToken.ToolCall]s to typed [PlannedAction]s, preserving type and order
 * for multi-action turns. A call with bad JSON, an unknown tool, or a missing/invalid required arg
 * becomes a [PlannedAction.Malformed] entry — never a thrown exception (so one bad call can't sink a
 * whole multi-action turn, feeding c-5/c-6).
 */
object ActionPlanner {

    private val json = Json { ignoreUnknownKeys = true }

    fun plan(calls: List<ChatToken.ToolCall>): List<PlannedAction> = calls.map(::toAction)

    private fun toAction(call: ChatToken.ToolCall): PlannedAction {
        val args = runCatching { json.parseToJsonElement(call.argsJson).jsonObject }.getOrNull()
            ?: return malformed(call, "arguments were not a JSON object")
        return when (call.name) {
            ToolSpecs.SET_TIMER -> {
                val seconds = args.int("seconds") ?: return malformed(call, "missing or non-integer 'seconds'")
                PlannedAction.Staged.Timer(seconds, args.str("label"), call.id)
            }
            ToolSpecs.SET_ALARM -> {
                val hour = args.int("hour") ?: return malformed(call, "missing or non-integer 'hour'")
                val minute = args.int("minute") ?: return malformed(call, "missing or non-integer 'minute'")
                PlannedAction.Staged.Alarm(hour, minute, args.str("label"), call.id)
            }
            ToolSpecs.CREATE_CALENDAR_EVENT -> {
                val title = args.str("title") ?: return malformed(call, "missing 'title'")
                val begin = args.str("start")?.let(::parseToMillis) ?: return malformed(call, "missing or unparseable 'start'")
                val end = args.str("end")?.let(::parseToMillis)
                PlannedAction.Handoff.Calendar(title, begin, end, args.str("location"), call.id)
            }
            ToolSpecs.DRAFT_EMAIL -> {
                val to = args.str("to") ?: return malformed(call, "missing 'to'")
                PlannedAction.Handoff.Email(to, args.str("subject"), args.str("body"), call.id)
            }
            ToolSpecs.DRAFT_SMS -> {
                val body = args.str("body") ?: return malformed(call, "missing 'body'")
                PlannedAction.Handoff.Sms(args.str("to"), body, call.id)
            }
            else -> malformed(call, "unknown tool")
        }
    }

    private fun malformed(call: ChatToken.ToolCall, reason: String) =
        PlannedAction.Malformed(call.name, reason, call.id)

    /** ISO-8601 -> epoch millis. Offset/instant forms keep their zone; a naive local datetime is
     *  interpreted in the device's zone. Unparseable input returns null (-> Malformed upstream). */
    private fun parseToMillis(s: String): Long? = runCatching {
        runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(s).toEpochMilli() }
            .getOrElse { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
}
