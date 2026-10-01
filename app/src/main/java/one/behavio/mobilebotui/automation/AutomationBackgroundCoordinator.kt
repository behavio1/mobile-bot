package one.behavio.mobilebotui.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import one.behavio.mobilebotui.workspace.WorkspaceClient
import java.util.concurrent.TimeUnit

object AutomationBackgroundCoordinator {
    private const val PERIODIC_WORK = "mobile-bot-automation-reconcile-periodic"
    private const val IMMEDIATE_WORK = "mobile-bot-automation-reconcile-immediate"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<AutomationReconcileWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        DevelopmentActionLogger.get(context).record(
            DevelopmentActions.SCHEDULER_PERIODIC_SCHEDULED,
            mapOf("workId" to request.id.toString(), "intervalMinutes" to 15, "policy" to "UPDATE"),
            ActionCorrelation(traceId = request.id.toString()),
        )
    }

    fun reconcileNow(context: Context, trigger: String = "manual") {
        val request = OneTimeWorkRequestBuilder<AutomationReconcileWorker>()
            .setInputData(workDataOf("trigger" to trigger))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        DevelopmentActionLogger.get(context).record(
            DevelopmentActions.SCHEDULER_RECONCILE_ENQUEUED,
            mapOf("trigger" to trigger, "workId" to request.id.toString(), "policy" to "REPLACE"),
            ActionCorrelation(traceId = request.id.toString()),
        )
    }

    suspend fun reconcile(
        context: Context,
        trigger: String,
        workId: String,
        attempt: Int,
    ): Boolean {
        val startedAtNanos = System.nanoTime()
        val correlation = ActionCorrelation(traceId = workId, actionId = "$workId:$attempt")
        return runCatching {
        DevelopmentActionLogger.get(context).record(
            DevelopmentActions.SCHEDULER_RECONCILE_STARTED,
            mapOf("trigger" to trigger, "workId" to workId, "attempt" to attempt),
            correlation,
        )
        val client = WorkspaceClient(actionLog = DevelopmentActionLogger.get(context))
        client.reconcileAutomations(workId)
        val snapshot = client.workspace(workId)
        AutomationNotificationManager(context).sync(snapshot.agents, snapshot.automations)
        DevelopmentActionLogger.get(context).record(
            DevelopmentActions.SCHEDULER_RECONCILE_COMPLETED,
            mapOf(
                "trigger" to trigger,
                "workId" to workId,
                "automationCount" to snapshot.automations.size,
                "waitingCount" to snapshot.automations.count { it.waitingSince != null },
                "attempt" to attempt,
                "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
            ),
            correlation,
        )
    }.onFailure { error ->
        DevelopmentActionLogger.get(context).record(
            DevelopmentActions.SCHEDULER_RECONCILE_FAILED,
            mapOf(
                "trigger" to trigger,
                "workId" to workId,
                "attempt" to attempt,
                "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
                "errorType" to error.javaClass.simpleName,
                "errorCode" to reconcileErrorCode(error),
            ),
            correlation,
        )
        }.isSuccess
    }

    private fun reconcileErrorCode(error: Throwable): String = when (error) {
        is one.behavio.mobilebotui.workspace.WorkspaceApiException -> error.code
        is java.net.SocketTimeoutException -> "host_timeout"
        is java.net.ConnectException -> "host_unreachable"
        else -> "reconcile_failed"
    }
}

class AutomationReconcileWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val trigger = inputData.getString("trigger") ?: if (runAttemptCount == 0) "periodic" else "retry"
        val succeeded = AutomationBackgroundCoordinator.reconcile(
            applicationContext,
            trigger,
            id.toString(),
            runAttemptCount,
        )
        return if (succeeded) Result.success() else Result.retry()
    }
}
