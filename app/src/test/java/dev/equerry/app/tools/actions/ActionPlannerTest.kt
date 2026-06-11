package dev.equerry.app.tools.actions

import dev.equerry.app.providers.drivers.ChatToken
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPlannerTest {

    private fun call(name: String, args: String, id: String? = null) = ChatToken.ToolCall(name, args, id)

    @Test
    fun timer_call_becomes_a_single_staged_timer_not_fired() {
        val plan = ActionPlanner.plan(listOf(call("set_timer", """{"seconds":300}""", "c1")))
        assertEquals(1, plan.size)
        val a = plan.single()
        assertTrue("timer must be Staged (a 'Start now?' confirm), not fired", a is PlannedAction.Staged.Timer)
        a as PlannedAction.Staged.Timer
        assertEquals(300, a.seconds)
        assertEquals("c1", a.callId)
    }

    @Test
    fun email_call_threads_recipient_subject_and_body() {
        val plan = ActionPlanner.plan(listOf(call("draft_email", """{"to":"a@b.com","subject":"Hi","body":"There"}""")))
        val a = plan.single() as PlannedAction.Handoff.Email
        assertEquals("a@b.com", a.to)
        assertEquals("Hi", a.subject)
        assertEquals("There", a.body)
    }

    @Test
    fun multiple_calls_preserve_type_and_order() {
        val plan = ActionPlanner.plan(
            listOf(
                call("set_timer", """{"seconds":60}"""),
                call("draft_email", """{"to":"a@b.com"}"""),
                call("set_alarm", """{"hour":7,"minute":30}"""),
            ),
        )
        assertEquals(3, plan.size)
        assertTrue(plan[0] is PlannedAction.Staged.Timer)
        assertTrue(plan[1] is PlannedAction.Handoff.Email)
        assertTrue(plan[2] is PlannedAction.Staged.Alarm)
    }

    @Test
    fun missing_required_arg_yields_malformed_not_an_exception() {
        val plan = ActionPlanner.plan(listOf(call("set_timer", """{"label":"tea"}""")))
        assertTrue(plan.single() is PlannedAction.Malformed)
    }

    @Test
    fun unknown_tool_yields_malformed() {
        assertTrue(ActionPlanner.plan(listOf(call("launch_rocket", "{}"))).single() is PlannedAction.Malformed)
    }

    @Test
    fun non_json_args_yield_malformed_not_an_exception() {
        assertTrue(ActionPlanner.plan(listOf(call("set_timer", "not json"))).single() is PlannedAction.Malformed)
    }

    @Test
    fun calendar_start_iso_is_converted_to_epoch_millis_in_the_device_zone() {
        val plan = ActionPlanner.plan(listOf(call("create_calendar_event", """{"title":"Standup","start":"2026-06-11T14:00"}""")))
        val a = plan.single() as PlannedAction.Handoff.Calendar
        val expected = LocalDateTime.parse("2026-06-11T14:00").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals("Standup", a.title)
        assertEquals(expected, a.beginMillis)
    }
}
