package one.behavio.mobilebotui.automation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import one.behavio.mobilebotui.MainActivity
import one.behavio.mobilebotui.workspace.WorkspaceAgent
import one.behavio.mobilebotui.workspace.WorkspaceAutomation
import java.util.UUID
import one.behavio.mobilebotui.R

class AutomationNotificationManager(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val notifications = NotificationManagerCompat.from(context)
    private val actionLog = DevelopmentActionLogger.get(context)

    fun sync(agents: List<WorkspaceAgent>, automations: List<WorkspaceAutomation>) {
        val correlation = ActionCorrelation()
        val startedAtNanos = System.nanoTime()
        ensureChannel()
        val agentsById = agents.associateBy { it.id }
        val waitingByAgent = automations
            .filter { it.deferredReason == "WAITING_FOR_UNLOCK" && it.waitingSince != null }
            .groupBy { it.agentId }
        val previousAgents = preferences.getStringSet(KEY_ACTIVE_AGENTS, emptySet()).orEmpty()
        val allAgentIds = previousAgents + waitingByAgent.keys
        actionLog.record(
            DevelopmentActions.NOTIFICATION_SYNC_STARTED,
            mapOf(
                "agentCount" to waitingByAgent.size,
                "waitingAutomationCount" to waitingByAgent.values.sumOf { it.size },
            ),
            correlation,
        )
        var postedCount = 0
        var cancelledCount = 0
        var suppressedCount = 0

        for (agentId in allAgentIds) {
            val waiting = waitingByAgent[agentId].orEmpty().sortedBy { it.name.lowercase() }
            if (waiting.isEmpty()) {
                val episodeId = preferences.getString(keyEpisode(agentId), null)
                notifications.cancel(notificationId(agentId))
                preferences.edit()
                    .remove(keyEpisode(agentId))
                    .remove(keyDismissed(agentId))
                    .apply()
                actionLog.record(
                    DevelopmentActions.NOTIFICATION_CANCELLED,
                    mapOf(
                        "agentId" to agentId,
                        "episodeId" to episodeId,
                        "notificationId" to notificationId(agentId),
                        "reason" to "waiting_list_empty",
                    ),
                    childCorrelation(correlation),
                )
                cancelledCount += 1
                continue
            }

            val isNewEpisode = !preferences.contains(keyEpisode(agentId))
            if (isNewEpisode) {
                preferences.edit()
                    .putString(keyEpisode(agentId), UUID.randomUUID().toString())
                    .putBoolean(keyDismissed(agentId), false)
                    .apply()
            }
            if (!preferences.getBoolean(keyDismissed(agentId), false)) {
                val posted = postAgentNotification(
                    agent = agentsById[agentId] ?: WorkspaceAgent(agentId, context.getString(R.string.agent_fallback_name), "A", ""),
                    waiting = waiting,
                    isNewEpisode = isNewEpisode,
                    parentCorrelation = correlation,
                )
                if (posted) postedCount += 1 else suppressedCount += 1
            } else {
                suppressedCount += 1
            }
        }
        preferences.edit().putStringSet(KEY_ACTIVE_AGENTS, waitingByAgent.keys).apply()
        actionLog.record(
            DevelopmentActions.NOTIFICATION_SYNC_COMPLETED,
            mapOf(
                "agentCount" to waitingByAgent.size,
                "waitingAutomationCount" to waitingByAgent.values.sumOf { it.size },
                "postedCount" to postedCount,
                "cancelledCount" to cancelledCount,
                "suppressedCount" to suppressedCount,
                "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
            ),
            correlation,
        )
    }

    fun markDismissed(agentId: String) {
        if (preferences.contains(keyEpisode(agentId))) {
            val episodeId = preferences.getString(keyEpisode(agentId), null)
            preferences.edit().putBoolean(keyDismissed(agentId), true).apply()
            actionLog.record(
                DevelopmentActions.NOTIFICATION_DISMISSED,
                mapOf(
                    "agentId" to agentId,
                    "episodeId" to episodeId,
                    "notificationId" to notificationId(agentId),
                ),
            )
        }
    }

    private fun postAgentNotification(
        agent: WorkspaceAgent,
        waiting: List<WorkspaceAutomation>,
        isNewEpisode: Boolean,
        parentCorrelation: ActionCorrelation,
    ): Boolean {
        val episodeId = preferences.getString(keyEpisode(agent.id), null)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            actionLog.record(
                DevelopmentActions.NOTIFICATION_PERMISSION_BLOCKED,
                mapOf(
                    "agentId" to agent.id,
                    "automationCount" to waiting.size,
                    "automationIds" to waiting.map { it.id },
                    "episodeId" to episodeId,
                    "notificationId" to notificationId(agent.id),
                ),
                childCorrelation(parentCorrelation),
            )
            return false
        }

        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_WAITING_AGENT_ID, agent.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(agent.id),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            notificationId(agent.id),
            Intent(context, AutomationEventReceiver::class.java)
                .setAction(AutomationEventReceiver.ACTION_NOTIFICATION_DISMISSED)
                .putExtra(AutomationEventReceiver.EXTRA_AGENT_ID, agent.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val names = waiting.map { it.name }
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(context.getString(R.string.notification_waiting_title, agent.name))
            .setSummaryText(context.resources.getQuantityString(R.plurals.automation_count, waiting.size, waiting.size))
        names.forEach(style::addLine)
        val preview = names.joinToString(", ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(one.behavio.mobilebotui.R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_waiting_title, agent.name))
            .setContentText(preview)
            .setStyle(style)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, context.getString(R.string.notification_view_waiting), contentIntent)
            .build()
        notifications.notify(notificationId(agent.id), notification)
        actionLog.record(
            DevelopmentActions.NOTIFICATION_POSTED,
            mapOf(
                "agentId" to agent.id,
                "automationCount" to waiting.size,
                "automationIds" to waiting.map { it.id },
                "episodeId" to episodeId,
                "notificationId" to notificationId(agent.id),
                "mode" to if (isNewEpisode) "created" else "updated",
            ),
            childCorrelation(parentCorrelation),
        )
        return true
    }

    private fun childCorrelation(parent: ActionCorrelation) = ActionCorrelation(
        traceId = parent.traceId,
        parentTraceId = parent.parentTraceId,
        parentActionId = parent.actionId,
    )

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_waiting),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_waiting_description)
            },
        )
    }

    private fun notificationId(agentId: String): Int = 40_000 + (agentId.hashCode() and 0x3fff)
    private fun keyEpisode(agentId: String) = "episode:$agentId"
    private fun keyDismissed(agentId: String) = "dismissed:$agentId"

    companion object {
        private const val CHANNEL_ID = "waiting_automations"
        private const val PREFERENCES = "automation-notifications"
        private const val KEY_ACTIVE_AGENTS = "active-agents"
        const val EXTRA_WAITING_AGENT_ID = "waiting_agent_id"
    }
}
