package one.behavio.mobilebotui.automation

import android.content.Context
import android.util.Log
import one.behavio.mobilebotui.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

typealias ActionMetadata = Map<String, Any?>

data class ActionCorrelation(
    val traceId: String = UUID.randomUUID().toString(),
    val actionId: String = UUID.randomUUID().toString(),
    val parentTraceId: String? = null,
    val parentActionId: String? = null,
)

/** Reusable action logger used by every Android feature in development builds. */
interface ActionLogger {
    fun record(
        action: String,
        metadata: ActionMetadata = emptyMap(),
        correlation: ActionCorrelation = ActionCorrelation(),
    )
}

object NoOpActionLogger : ActionLogger {
    override fun record(action: String, metadata: ActionMetadata, correlation: ActionCorrelation) = Unit
}

class DevelopmentActionLogger private constructor(
    context: Context,
    private val component: String,
    private val clock: () -> Instant,
) : ActionLogger {
    private val applicationContext = context.applicationContext

    @Synchronized
    override fun record(action: String, metadata: ActionMetadata, correlation: ActionCorrelation) {
        if (!BuildConfig.DEBUG && action !in DevelopmentActions.SETUP_DIAGNOSTICS) return
        require(ACTION_PATTERN.matches(action)) { "Invalid development action name: $action" }
        DevelopmentActions.validate(action, metadata)
        val entry = JSONObject()
            .put("timestamp", clock().toString())
            .put("component", component)
            .put("eventId", UUID.randomUUID().toString())
            .put("traceId", correlation.traceId)
            .put("actionId", correlation.actionId)
            .put("parentTraceId", correlation.parentTraceId ?: JSONObject.NULL)
            .put("parentActionId", correlation.parentActionId ?: JSONObject.NULL)
            .put("sequence", sequence.incrementAndGet())
            .put("action", action)
            .put("metadata", JSONObject().apply {
                metadata.toSortedMap().forEach { (key, value) ->
                    put(key, if (isSensitiveKey(key)) "[redacted]" else safeValue(value))
                }
            })
            .toString()
        Log.v(TAG, entry)
        runCatching {
            val current = File(applicationContext.filesDir, FILE_NAME)
            if (current.length() >= MAX_BYTES) {
                current.copyTo(File(applicationContext.filesDir, PREVIOUS_FILE_NAME), overwrite = true)
                current.writeText("")
            }
            current.appendText("$entry\n")
        }
    }

    private fun safeValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is String, is Number, is Boolean -> value
        is Iterable<*> -> JSONArray(value.map { safeValue(it) })
        is Array<*> -> JSONArray(value.map { safeValue(it) })
        else -> value.toString()
    }

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.filter(Char::isLetterOrDigit).lowercase()
        if (SAFE_DERIVED_SUFFIXES.any(normalized::endsWith)) return false
        return SENSITIVE_KEYS.any(normalized::contains)
    }

    companion object {
        private const val TAG = "MobileBotAction"
        const val FILE_NAME = "development-actions.jsonl"
        private const val PREVIOUS_FILE_NAME = "development-actions.previous.jsonl"
        private const val MAX_BYTES = 1_048_576L
        private val ACTION_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
        private val SENSITIVE_KEYS = setOf(
            "authorization", "body", "content", "cookie", "devicecode", "password", "profile", "prompt",
            "query", "reply", "secret", "stderr", "stdout", "text", "token", "url", "message",
        )
        private val SAFE_DERIVED_SUFFIXES = setOf("bytes", "configured", "count", "length", "present")
        private val sequence = AtomicLong(0)

        @Volatile private var instance: DevelopmentActionLogger? = null

        fun get(context: Context): ActionLogger = instance ?: synchronized(this) {
            instance ?: DevelopmentActionLogger(
                context = context,
                component = "android",
                clock = Instant::now,
            ).also { instance = it }
        }

        internal fun createForTest(
            context: Context,
            component: String = "android",
            clock: () -> Instant = Instant::now,
        ): DevelopmentActionLogger = DevelopmentActionLogger(context, component, clock)
    }
}

/** Stable action names keep logs queryable across features and releases. */
object DevelopmentActions {
    const val SETUP_INSPECTED = "setup.inspected"
    const val SETUP_PREPARATION = "setup.preparation"
    val SETUP_DIAGNOSTICS = setOf(SETUP_INSPECTED, SETUP_PREPARATION,
        "termux.command_started", "termux.command_finished", "host.start_requested")
    const val APP_CREATED = "app.lifecycle_created"
    const val APP_RESUMED = "app.lifecycle_resumed"
    const val SETUP_ACTION_REQUESTED = "setup.action_requested"
    const val TERMUX_COMMAND_STARTED = "termux.command_started"
    const val TERMUX_COMMAND_FINISHED = "termux.command_finished"
    const val HOST_START_REQUESTED = "host.start_requested"
    const val WORKSPACE_UI_ACTION = "workspace.ui_action"
    const val WORKSPACE_ACTION_STARTED = "workspace.action_started"
    const val WORKSPACE_ACTION_FINISHED = "workspace.action_finished"
    const val PHONE_SERVICE_CONNECTED = "phone.service_connected"
    const val PHONE_SERVICE_DESTROYED = "phone.service_destroyed"
    const val PHONE_ACTION_STARTED = "phone.action_started"
    const val PHONE_ACTION_FINISHED = "phone.action_finished"
    const val SCHEDULER_PERIODIC_SCHEDULED = "scheduler.periodic_scheduled"
    const val SCHEDULER_RECONCILE_ENQUEUED = "scheduler.reconcile_enqueued"
    const val SCHEDULER_RECONCILE_STARTED = "scheduler.reconcile_started"
    const val SCHEDULER_RECONCILE_COMPLETED = "scheduler.reconcile_completed"
    const val SCHEDULER_RECONCILE_FAILED = "scheduler.reconcile_failed"
    const val NOTIFICATION_SYNC_STARTED = "notification.sync_started"
    const val NOTIFICATION_SYNC_COMPLETED = "notification.sync_completed"
    const val NOTIFICATION_POSTED = "notification.posted"
    const val NOTIFICATION_CANCELLED = "notification.cancelled"
    const val NOTIFICATION_DISMISSED = "notification.dismissed"
    const val NOTIFICATION_PERMISSION_BLOCKED = "notification.permission_blocked"
    const val SYSTEM_EVENT_RECEIVED = "system.event_received"
    const val OWNER_PROFILE_SAVED = "owner.profile_saved"
    const val AGENT_TASK_CREATE_REQUESTED = "agent.task_create_requested"
    const val AGENT_CREATED = "agent.created"

    val ALL = setOf(
        SETUP_INSPECTED,
        SETUP_PREPARATION,
        APP_CREATED,
        APP_RESUMED,
        SETUP_ACTION_REQUESTED,
        TERMUX_COMMAND_STARTED,
        TERMUX_COMMAND_FINISHED,
        HOST_START_REQUESTED,
        WORKSPACE_UI_ACTION,
        WORKSPACE_ACTION_STARTED,
        WORKSPACE_ACTION_FINISHED,
        PHONE_SERVICE_CONNECTED,
        PHONE_SERVICE_DESTROYED,
        PHONE_ACTION_STARTED,
        PHONE_ACTION_FINISHED,
        SCHEDULER_PERIODIC_SCHEDULED,
        SCHEDULER_RECONCILE_ENQUEUED,
        SCHEDULER_RECONCILE_STARTED,
        SCHEDULER_RECONCILE_COMPLETED,
        SCHEDULER_RECONCILE_FAILED,
        NOTIFICATION_SYNC_STARTED,
        NOTIFICATION_SYNC_COMPLETED,
        NOTIFICATION_POSTED,
        NOTIFICATION_CANCELLED,
        NOTIFICATION_DISMISSED,
        NOTIFICATION_PERMISSION_BLOCKED,
        SYSTEM_EVENT_RECEIVED,
        OWNER_PROFILE_SAVED,
        AGENT_TASK_CREATE_REQUESTED,
        AGENT_CREATED,
    )

    /**
     * Minimum diagnostic contract for each event. Validation runs only when the
     * development logger is enabled, so omissions fail next to the call site.
     */
    val REQUIRED_METADATA: Map<String, Set<String>> = mapOf(
        SETUP_INSPECTED to setOf("stage", "termuxInstalled", "bridgeState"),
        SETUP_PREPARATION to setOf("phase"),
        APP_CREATED to setOf("restoredState"),
        APP_RESUMED to setOf("activity"),
        SETUP_ACTION_REQUESTED to setOf("actionTarget"),
        TERMUX_COMMAND_STARTED to setOf("requestId", "operation", "background", "timeoutMillis"),
        TERMUX_COMMAND_FINISHED to setOf("requestId", "operation", "succeeded", "durationMillis"),
        HOST_START_REQUESTED to setOf("requestId", "hostVersion", "verboseLogs"),
        WORKSPACE_UI_ACTION to setOf("operation", "agentId"),
        WORKSPACE_ACTION_STARTED to setOf("requestId", "method", "path"),
        WORKSPACE_ACTION_FINISHED to setOf(
            "requestId", "method", "path", "status", "succeeded", "durationMillis", "errorCode",
        ),
        PHONE_SERVICE_CONNECTED to setOf("serviceInstanceId", "port", "bindAddress"),
        PHONE_SERVICE_DESTROYED to setOf("serviceInstanceId", "durationMillis"),
        PHONE_ACTION_STARTED to setOf("requestedAction", "runId", "automationId", "agentId"),
        PHONE_ACTION_FINISHED to setOf(
            "requestedAction", "ok", "state", "durationMillis", "runId", "automationId", "agentId",
        ),
        SCHEDULER_PERIODIC_SCHEDULED to setOf("workId", "intervalMinutes", "policy"),
        SCHEDULER_RECONCILE_ENQUEUED to setOf("trigger", "workId", "policy"),
        SCHEDULER_RECONCILE_STARTED to setOf("trigger", "workId", "attempt"),
        SCHEDULER_RECONCILE_COMPLETED to setOf(
            "trigger", "workId", "attempt", "automationCount", "waitingCount", "durationMillis",
        ),
        SCHEDULER_RECONCILE_FAILED to setOf(
            "trigger", "workId", "attempt", "durationMillis", "errorType", "errorCode",
        ),
        NOTIFICATION_SYNC_STARTED to setOf("agentCount", "waitingAutomationCount"),
        NOTIFICATION_SYNC_COMPLETED to setOf(
            "agentCount", "waitingAutomationCount", "postedCount", "cancelledCount", "suppressedCount", "durationMillis",
        ),
        NOTIFICATION_POSTED to setOf(
            "agentId", "automationCount", "automationIds", "episodeId", "notificationId", "mode",
        ),
        NOTIFICATION_CANCELLED to setOf("agentId", "episodeId", "notificationId", "reason"),
        NOTIFICATION_DISMISSED to setOf("agentId", "episodeId", "notificationId"),
        NOTIFICATION_PERMISSION_BLOCKED to setOf(
            "agentId", "automationCount", "automationIds", "episodeId", "notificationId",
        ),
        SYSTEM_EVENT_RECEIVED to setOf("broadcastAction"),
        OWNER_PROFILE_SAVED to setOf("profileLength"),
        AGENT_TASK_CREATE_REQUESTED to setOf("nameLength", "promptLength"),
        AGENT_CREATED to setOf("agentId", "nameLength"),
    )

    fun validate(action: String, metadata: ActionMetadata) {
        val required = requireNotNull(REQUIRED_METADATA[action]) {
            "Development action is missing from REQUIRED_METADATA: $action"
        }
        val missing = required - metadata.keys
        require(missing.isEmpty()) {
            "Development action $action is missing metadata: ${missing.sorted().joinToString()}"
        }
        val hasDomainContext = listOf("runId", "automationId").any { key ->
            metadata[key]?.toString()?.isNotBlank() == true
        }
        if (hasDomainContext) {
            require(metadata["agentId"]?.toString()?.isNotBlank() == true) {
                "Development action $action has run/automation context without agentId"
            }
        }
    }
}
