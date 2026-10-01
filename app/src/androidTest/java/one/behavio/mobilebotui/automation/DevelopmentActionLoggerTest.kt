package one.behavio.mobilebotui.automation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DevelopmentActionLoggerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun writesReusableCorrelatedJsonEventsAndRedactsPayloadFields() {
        val file = File(context.filesDir, DevelopmentActionLogger.FILE_NAME)
        file.delete()
        val logger = DevelopmentActionLogger.createForTest(
            context = context,
            clock = { Instant.parse("2026-09-07T21:15:00Z") },
        )
        val correlation = ActionCorrelation(traceId = "trace-agent-shopping", actionId = "price-check")

        logger.record(
            DevelopmentActions.PHONE_ACTION_STARTED,
            mapOf(
                "agentId" to "agent-shopping",
                "automationId" to "automation-1",
                "runId" to "run-1",
                "requestedAction" to "observe",
            ),
            correlation,
        )
        logger.record(
            DevelopmentActions.PHONE_ACTION_FINISHED,
            mapOf(
                "agentId" to "agent-shopping",
                "automationId" to "automation-1",
                "runId" to "run-1",
                "requestedAction" to "observe",
                "ok" to true,
                "state" to "READY",
                "durationMillis" to 12,
            ),
            correlation,
        )
        logger.record(
            DevelopmentActions.PHONE_ACTION_STARTED,
            mapOf(
                "agentId" to "agent-shopping",
                "automationId" to "automation-1",
                "runId" to "run-1",
                "requestedAction" to "observe",
                "prompt" to "must-not-be-written",
                "ownerProfile" to "also-must-not-be-written",
            ),
            correlation,
        )

        val events = file.readLines().map(::JSONObject)
        assertEquals(3, events.size)
        assertNotEquals(events[0].getString("eventId"), events[1].getString("eventId"))
        assertTrue(events.all { it.getString("traceId") == "trace-agent-shopping" })
        assertTrue(events.all { it.getString("actionId") == "price-check" })
        assertTrue(events.all { it.getJSONObject("metadata").getString("agentId") == "agent-shopping" })
        assertTrue(events.all { it.getString("timestamp") == "2026-09-07T21:15:00Z" })
        assertEquals("[redacted]", events[2].getJSONObject("metadata").getString("prompt"))
        assertEquals("[redacted]", events[2].getJSONObject("metadata").getString("ownerProfile"))
    }

    @Test
    fun everyDeclaredAndroidEventPropagatesIdentityAndCorrelationFields() {
        val file = File(context.filesDir, DevelopmentActionLogger.FILE_NAME)
        file.delete()
        val logger = DevelopmentActionLogger.createForTest(
            context = context,
            clock = { Instant.parse("2026-09-07T21:16:00Z") },
        )
        val correlation = ActionCorrelation(
            traceId = "trace-all-events",
            actionId = "action-all-events",
            parentTraceId = "trace-parent",
            parentActionId = "action-parent",
        )

        assertEquals(DevelopmentActions.ALL, DevelopmentActions.REQUIRED_METADATA.keys)
        DevelopmentActions.ALL.forEach { action ->
            val required: Map<String, Any?> = DevelopmentActions.REQUIRED_METADATA.getValue(action)
                .associateWith { null }
            val metadata = required + if ("agentId" in required) {
                mapOf("agentId" to "agent-test")
            } else {
                emptyMap()
            }
            logger.record(
                action,
                metadata,
                correlation,
            )
            val missingField = required.keys.first()
            assertTrue(
                runCatching {
                    logger.record(action, metadata - missingField, correlation)
                }.exceptionOrNull() is IllegalArgumentException,
            )
        }

        val events = file.readLines().map(::JSONObject)
        assertEquals(DevelopmentActions.ALL.size, events.size)
        assertEquals(DevelopmentActions.ALL, events.map { it.getString("action") }.toSet())
        assertEquals(events.size, events.map { it.getString("eventId") }.toSet().size)
        assertTrue(events.all { it.getString("traceId") == "trace-all-events" })
        assertTrue(events.all { it.getString("actionId") == "action-all-events" })
        assertTrue(events.all { it.getString("parentTraceId") == "trace-parent" })
        assertTrue(events.all { it.getString("parentActionId") == "action-parent" })
        assertTrue(events.all { it.getString("component") == "android" })
        assertTrue(events.filter { it.getJSONObject("metadata").has("agentId") }.all {
            it.getJSONObject("metadata").getString("agentId") == "agent-test"
        })
        assertEquals(events.size, events.map { it.getLong("sequence") }.toSet().size)
    }
}
