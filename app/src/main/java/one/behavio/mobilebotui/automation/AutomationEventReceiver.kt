package one.behavio.mobilebotui.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutomationEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DevelopmentActionLogger.get(context).record(
            DevelopmentActions.SYSTEM_EVENT_RECEIVED,
            mapOf(
                "broadcastAction" to intent.action,
                "agentId" to intent.getStringExtra(EXTRA_AGENT_ID),
            ),
        )
        when (intent.action) {
            ACTION_NOTIFICATION_DISMISSED -> {
                intent.getStringExtra(EXTRA_AGENT_ID)?.let {
                    AutomationNotificationManager(context).markDismissed(it)
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_USER_PRESENT,
            -> {
                AutomationBackgroundCoordinator.schedule(context)
                AutomationBackgroundCoordinator.reconcileNow(context, intent.action ?: "system")
            }
        }
    }

    companion object {
        const val ACTION_NOTIFICATION_DISMISSED = "one.behavio.mobilebotui.NOTIFICATION_DISMISSED"
        const val EXTRA_AGENT_ID = "agent_id"
    }
}
