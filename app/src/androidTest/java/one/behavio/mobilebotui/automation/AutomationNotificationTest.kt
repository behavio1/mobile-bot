package one.behavio.mobilebotui.automation

import android.Manifest
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import one.behavio.mobilebotui.workspace.WorkspaceAgent
import one.behavio.mobilebotui.workspace.WorkspaceAutomation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomationNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val system = context.getSystemService(NotificationManager::class.java)

    @Before
    fun prepare() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        system.cancelAll()
        context.getSharedPreferences("automation-notifications", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun cleanup() {
        system.cancelAll()
    }

    @Test
    fun groupsAllWaitingAutomationNamesPerAgent() {
        val agents = listOf(
            WorkspaceAgent("agent-a", "Zakupy", "Z", ""),
            WorkspaceAgent("agent-b", "Mail", "M", ""),
        )
        val manager = AutomationNotificationManager(context)
        manager.sync(
            agents,
            listOf(
                waiting("a-1", "agent-a", "Najtańszy kabel USB-C"),
                waiting("a-2", "agent-a", "Cena kawy"),
                waiting("b-1", "agent-b", "Odpowiedź dla Anny"),
            ),
        )

        val active = awaitNotificationCount(2)
        assertEquals(2, active.size)
        val shopping = active.single { it.notification.extras.getString("android.title")?.startsWith("Zakupy") == true }
        val lines = shopping.notification.extras.getCharSequenceArray("android.textLines").orEmpty().map(CharSequence::toString)
        assertTrue(lines.contains("Najtańszy kabel USB-C"))
        assertTrue(lines.contains("Cena kawy"))

        manager.sync(
            agents,
            listOf(
                waiting("a-1", "agent-a", "Najtańszy kabel USB-C"),
                waiting("a-2", "agent-a", "Cena kawy"),
                waiting("a-3", "agent-a", "Cena słuchawek"),
                waiting("b-1", "agent-b", "Odpowiedź dla Anny"),
            ),
        )
        assertEquals(2, awaitNotificationCount(2).size)
    }

    private fun waiting(id: String, agentId: String, name: String) = WorkspaceAutomation(
        id = id,
        agentId = agentId,
        conversationId = "conversation-$id",
        name = name,
        intervalMinutes = 15,
        status = "active",
        nextRunAt = "2026-09-07T10:15:00.000Z",
        lastRunAt = null,
        lastOutcome = null,
        lastSummary = null,
        scheduledAt = "2026-09-07T10:15:00.000Z",
        startedAt = null,
        delayMillis = null,
        waitingSince = "2026-09-07T10:15:00.000Z",
        deferredReason = "WAITING_FOR_UNLOCK",
        skippedOccurrences = 0,
    )

    private fun awaitNotificationCount(expected: Int): List<android.service.notification.StatusBarNotification> {
        repeat(30) {
            val active = system.activeNotifications.toList()
            if (active.size == expected) return active
            Thread.sleep(100)
        }
        return system.activeNotifications.toList()
    }
}
