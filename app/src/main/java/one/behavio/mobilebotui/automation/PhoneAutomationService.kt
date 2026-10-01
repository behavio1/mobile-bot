package one.behavio.mobilebotui.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneAutomationService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loopbackServer: LoopbackServer? = null
    @Volatile private var lastAccessibilityPackage = ""
    @Volatile private var lastAccessibilityEventAtMillis = 0L
    @Volatile private var lastUnreadableActionAtMillis = 0L
    private val actionLog by lazy { DevelopmentActionLogger.get(this) }
    private val serviceCorrelation = ActionCorrelation()
    private var serviceConnectedAtNanos = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnectedAtNanos = System.nanoTime()
        val previousServer = loopbackServer
        loopbackServer = null
        _connected.value = false
        previousServer?.close()
        lateinit var server: LoopbackServer
        server = LoopbackServer(
            service = this,
            token = DeviceBridgeSecrets.getOrCreate(this),
            onBound = {
                mainHandler.post {
                    if (loopbackServer === server) _connected.value = true
                }
            },
            onStopped = {
                mainHandler.post {
                    if (loopbackServer === server) _connected.value = false
                }
            },
            onError = { stage, error ->
                Log.e(TAG, "Loopback server $stage failed (${error.javaClass.simpleName})")
            },
        )
        loopbackServer = server
        server.start()
        actionLog.record(
            DevelopmentActions.PHONE_SERVICE_CONNECTED,
            mapOf(
                "serviceInstanceId" to serviceCorrelation.traceId,
                "port" to PORT,
                "bindAddress" to "127.0.0.1",
            ),
            serviceCorrelation,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventPackage = event?.packageName?.toString().orEmpty()
        if (eventPackage.isNotBlank()) {
            lastAccessibilityPackage = eventPackage
            lastAccessibilityEventAtMillis = SystemClock.elapsedRealtime()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        val server = loopbackServer
        loopbackServer = null
        _connected.value = false
        server?.close()
        actionLog.record(
            DevelopmentActions.PHONE_SERVICE_DESTROYED,
            mapOf(
                "serviceInstanceId" to serviceCorrelation.traceId,
                "durationMillis" to if (serviceConnectedAtNanos == 0L) 0 else {
                    (System.nanoTime() - serviceConnectedAtNanos) / 1_000_000
                },
            ),
            serviceCorrelation,
        )
        super.onDestroy()
    }

    internal fun execute(
        request: JSONObject,
        traceId: String? = null,
        runId: String? = null,
        automationId: String? = null,
        agentId: String? = null,
    ): JSONObject {
        val action = request.optString("action")
        val startedAtNanos = System.nanoTime()
        val correlation = ActionCorrelation(
            traceId = traceId?.takeIf(::isSafeCorrelationId) ?: UUID.randomUUID().toString(),
        )
        actionLog.record(
            DevelopmentActions.PHONE_ACTION_STARTED,
            phoneRequestMetadata(request, action) + mapOf(
                "requestedAction" to action.ifBlank { "missing" },
                "runId" to runId,
                "automationId" to automationId,
                "agentId" to agentId,
            ),
            correlation,
        )
        return try {
            val response = if (action == "status") {
                deviceStatus()
            } else {
                val status = deviceStatus()
                if (status.optString("state") != "READY") {
                    status
                } else when (action) {
                    "return_to_mobile_bot" -> returnToMobileBot(request, runId, agentId)
                    "observe" -> observationResult()
                    "list_apps" -> listApps(request.optString("query"))
                    "open_app" -> openApp(request.optString("packageName"))
                    "open_url" -> openUrl(request.optString("url"))
                    "open_mail" -> openMail()
                    "open_sms" -> openSms()
                    "click" -> mutateAndObserve { clickByText(request) }
                    "set_text" -> if (request.optBoolean("readOnly")) {
                        result(false, "READ_ONLY_ACTION_REJECTED")
                    } else {
                        mutateAndObserve { setText(request) }
                    }
                    "scroll" -> mutateAndObserve { scroll(request.optString("direction", "forward")) }
                    "home" -> mutateAndObserve {
                        val performed = performGlobalAction(GLOBAL_ACTION_HOME)
                        actionResult(performed, if (performed) "ACTION_PERFORMED" else "ACTION_REJECTED")
                    }
                    "back" -> mutateAndObserve {
                        val performed = performGlobalAction(GLOBAL_ACTION_BACK)
                        actionResult(performed, if (performed) "ACTION_PERFORMED" else "ACTION_REJECTED")
                    }
                    else -> result(false, "INVALID_ACTION")
                }
            }
            val observation = response.optJSONObject("observation")
            actionLog.record(
                DevelopmentActions.PHONE_ACTION_FINISHED,
                phoneRequestMetadata(request, action) + mapOf(
                    "requestedAction" to action.ifBlank { "missing" },
                    "ok" to response.optBoolean("ok"),
                    "state" to response.optString("state", "UNKNOWN"),
                    "packageName" to response.optString("packageName").ifBlank {
                        observation?.optString("packageName").orEmpty()
                    }.ifBlank { null },
                    "visibleNodeCount" to observation?.optJSONArray("nodes")?.length(),
                    "observationTruncated" to observation?.optBoolean("truncated"),
                    "observationQuality" to observation?.optJSONObject("perception")?.optString("quality")?.ifBlank { null },
                    "observationSupportRecommended" to observation?.optJSONObject("perception")?.optBoolean("supportRecommended"),
                    "matchCount" to response.optInt("matchCount").takeIf { response.has("matchCount") },
                    "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
                    "runId" to runId,
                    "automationId" to automationId,
                    "agentId" to agentId,
                ),
                correlation,
            )
            response
        } catch (error: Throwable) {
            actionLog.record(
                DevelopmentActions.PHONE_ACTION_FINISHED,
                phoneRequestMetadata(request, action) + mapOf(
                    "requestedAction" to action.ifBlank { "missing" },
                    "ok" to false,
                    "state" to "EXECUTOR_ERROR",
                    "errorType" to error.javaClass.simpleName,
                    "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
                    "runId" to runId,
                    "automationId" to automationId,
                    "agentId" to agentId,
                ),
                correlation,
            )
            throw error
        }
    }

    private fun phoneRequestMetadata(request: JSONObject, action: String): ActionMetadata = when (action) {
        "open_url" -> mapOf(
            "targetHost" to runCatching { Uri.parse(request.optString("url")).host }.getOrNull(),
        )
        "open_app" -> mapOf("targetPackage" to request.optString("packageName").take(255))
        "click" -> mapOf("targetIndex" to request.optInt("index", 0))
        "set_text" -> mapOf(
            "targetIndex" to request.optInt("index", 0),
            "textLength" to request.optString("text").length,
        )
        "scroll" -> mapOf("direction" to request.optString("direction", "forward"))
        else -> emptyMap()
    }

    private fun deviceStatus(): JSONObject {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        return if (!power.isInteractive || keyguard.isKeyguardLocked) {
            result(false, "WAITING_FOR_UNLOCK")
        } else {
            result(true, "READY")
        }
    }

    @Suppress("DEPRECATION")
    private fun listApps(query: String = ""): JSONObject {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.MATCH_ALL,
        ).map { resolved ->
            JSONObject()
                .put("name", resolved.loadLabel(packageManager)?.toString().orEmpty())
                .put("packageName", resolved.activityInfo.packageName)
        }.distinctBy { it.getString("packageName") }
            .sortedWith(compareBy({ it.getString("name").lowercase(Locale.ROOT) }, { it.getString("packageName") }))
        val search = query.trim()
        val matches = if (search.isEmpty()) apps else apps.filter {
            it.getString("name").contains(search, ignoreCase = true) ||
                it.getString("packageName").contains(search, ignoreCase = true)
        }
        return result(true, "READY")
            .put("apps", JSONArray(matches))
            .put("appCount", matches.size)
            .put("totalAppCount", apps.size)
            .put("query", search)
    }

    @Suppress("DEPRECATION")
    private fun openApp(rawPackageName: String): JSONObject {
        val packageName = rawPackageName.trim()
        if (packageName.length !in 3..255 || !PACKAGE_NAME_PATTERN.matches(packageName)) {
            return result(false, "INVALID_PACKAGE_ID")
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            val installed = runCatching {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }.isSuccess
            return result(false, if (installed) "APP_HAS_NO_LAUNCHER" else "APP_NOT_FOUND")
                .put("packageName", packageName)
        }
        return launchAndAwait(launchIntent, packageName, "APP_OPEN_FAILED")
    }

    private fun openUrl(rawUrl: String): JSONObject {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull()
        if (uri?.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
            return result(false, "INVALID_WEB_URL")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = intent.resolveActivity(packageManager)?.packageName
            ?: return result(false, "NO_BROWSER_APP")
        return launchAndAwait(intent, target, "NO_BROWSER_APP")
    }

    private fun openMail(): JSONObject {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = intent.resolveActivity(packageManager)?.packageName
            ?: return result(false, "NO_MAIL_APP")
        return launchAndAwait(intent, target, "NO_MAIL_APP")
    }

    private fun openSms(): JSONObject {
        val target = Telephony.Sms.getDefaultSmsPackage(this)?.takeIf { it.isNotBlank() }
            ?: return result(false, "NO_SMS_APP")
        val intent = packageManager.getLaunchIntentForPackage(target)
            ?: return result(false, "APP_HAS_NO_LAUNCHER").put("packageName", target)
        return launchAndAwait(intent, target, "SMS_APP_OPEN_FAILED")
    }

    private fun returnToMobileBot(request: JSONObject, runId: String?, agentId: String?): JSONObject {
        val conversationId = request.optString("conversationId")
        if (runId == null || agentId == null || !isSafeCorrelationId(runId) ||
            !isSafeCorrelationId(agentId) || !isSafeCorrelationId(conversationId) ||
            request.optString("runId") != runId || request.optString("agentId") != agentId
        ) return result(false, "INVALID_RETURN_CONTEXT")
        val intent = Intent(this, one.behavio.mobilebotui.MainActivity::class.java)
            .setAction(one.behavio.mobilebotui.MainActivity.ACTION_PHONE_TASK_RETURN)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("runId", runId)
            .putExtra("agentId", agentId)
            .putExtra("conversationId", conversationId)
        return launchAndAwait(intent, packageName, "APP_RETURN_FAILED")
    }

    private fun launchAndAwait(intent: Intent, targetPackage: String, failureState: String): JSONObject {
        val started = onMain {
            runCatching {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }
        if (!started) return result(false, failureState).put("targetPackage", targetPackage)
        return awaitTargetObservation(targetPackage)
    }

    private fun awaitTargetObservation(targetPackage: String): JSONObject {
        var observedPackage = ""
        var lastTargetUnreadable: JSONObject? = null
        repeat(TARGET_OBSERVATION_ATTEMPTS) {
            Thread.sleep(OBSERVATION_RETRY_MILLIS)
            val status = deviceStatus()
            if (status.optString("state") != "READY") return status
            val observation = onMain(::buildObservation)
            observedPackage = observation.optString("packageName")
            if (observation.optString("state") == "SYSTEM_WINDOW_REQUIRES_USER") {
                return observation
            }
            if (observation.optString("state") == "READY" && observedPackage == targetPackage) {
                return result(true, "READY").put("observation", observation)
            }
            if (observation.optString("state") == "NO_READABLE_CONTENT" && observedPackage == targetPackage) {
                lastTargetUnreadable = observation
            }
        }
        if (observedPackage == targetPackage) {
            lastTargetUnreadable?.let { return it }
        }
        return result(false, "TARGET_APP_TIMEOUT")
            .put("targetPackage", targetPackage)
            .put("observedPackage", observedPackage)
    }

    private fun mutateAndObserve(block: () -> JSONObject): JSONObject {
        val actionResult = onMain(block)
        if (!actionResult.optBoolean("ok")) return actionResult
        var lastObservation = result(false, "NO_ACTIVE_WINDOW")
        repeat(POST_ACTION_OBSERVATION_ATTEMPTS) {
            Thread.sleep(OBSERVATION_RETRY_MILLIS)
            val observation = onMain(::buildObservation)
            lastObservation = observation
            if (observation.optString("state") == "READY") {
                return actionResult.put("observation", observation)
            }
            if (observation.optString("state") == "SYSTEM_WINDOW_REQUIRES_USER") {
                return observation
            }
        }
        if (lastObservation.optString("state") == "NO_ACTIVE_WINDOW") {
            lastUnreadableActionAtMillis = SystemClock.elapsedRealtime()
            return result(false, "SYSTEM_WINDOW_REQUIRES_USER")
        }
        return lastObservation
    }

    private fun observationResult(): JSONObject {
        val observation = onMain(::buildObservation)
        if (observation.optString("state") != "READY") return observation
        return result(true, "READY").put("observation", observation)
    }

    private fun buildObservation(): JSONObject {
        val root = freshRootInActiveWindow() ?: return if (systemWindowRequiresUser()) {
            result(false, "SYSTEM_WINDOW_REQUIRES_USER")
        } else {
            result(false, "NO_ACTIVE_WINDOW")
        }
        val packageName = root.packageName?.toString().orEmpty()
        val nodes = JSONArray()
        val depthTruncated = appendNodes(root, nodes, depth = 0, limit = 180)
        val truncated = depthTruncated || nodes.length() >= 180
        val metrics = collectObservationMetrics(root, truncated)
        val assessment = assessAccessibilityObservation(metrics)
        return JSONObject()
            .put("ok", true)
            .put("state", "READY")
            .put("packageName", packageName)
            .put("windowTitle", windows.firstOrNull { it.isActive }?.title?.toString().orEmpty())
            .put("nodes", nodes)
            .put("readableNodeCount", metrics.readableNodeCount)
            .put("truncated", truncated)
            .put("perception", perceptionResult(metrics, assessment))
    }

    private fun collectObservationMetrics(
        root: AccessibilityNodeInfo,
        truncated: Boolean,
    ): AccessibilityObservationMetrics {
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val rootArea = rootBounds.width().toLong().coerceAtLeast(1L) * rootBounds.height().toLong().coerceAtLeast(1L)
        val visibleNodes = collectNodes(root)
        val readableNodes = visibleNodes.filter(::hasReadableLabel)
        val actionableNodes = visibleNodes.filter(::isActionableNode)
        val labeledActions = readableNodes.mapNotNull { node ->
            clickableAncestor(node) ?: node.takeIf(::isActionableNode)
        }.distinctBy(::nodeKey)
        val largeUnlabeledSurfaces = visibleNodes.count { node ->
            if (hasReadableLabel(node) || node.childCount > 0) {
                return@count false
            }
            val className = node.className?.toString().orEmpty()
            val surfaceLike = className.endsWith("View") || className.contains("SurfaceView") || className.contains("TextureView")
            if (!surfaceLike) return@count false
            val bounds = Rect().also(node::getBoundsInScreen)
            val area = bounds.width().toLong().coerceAtLeast(0L) * bounds.height().toLong().coerceAtLeast(0L)
            area * 4 >= rootArea
        }
        return AccessibilityObservationMetrics(
            visibleNodeCount = visibleNodes.size,
            readableNodeCount = readableNodes.size,
            actionableNodeCount = actionableNodes.size,
            labeledActionableNodeCount = labeledActions.size,
            unlabeledLargeSurfaceCount = largeUnlabeledSurfaces,
            truncated = truncated,
        )
    }

    private fun perceptionResult(
        metrics: AccessibilityObservationMetrics,
        assessment: AccessibilityObservationAssessment,
    ): JSONObject = JSONObject()
        .put("source", "accessibility")
        .put("quality", assessment.quality)
        .put("supportRecommended", assessment.supportRecommended)
        .put("recommendedSupport", assessment.recommendedSupport)
        .put("reasons", JSONArray(assessment.reasons))
        .put("metrics", JSONObject()
            .put("visibleNodeCount", metrics.visibleNodeCount)
            .put("readableNodeCount", metrics.readableNodeCount)
            .put("actionableNodeCount", metrics.actionableNodeCount)
            .put("labeledActionableNodeCount", metrics.labeledActionableNodeCount)
            .put("unlabeledLargeSurfaceCount", metrics.unlabeledLargeSurfaceCount)
            .put("truncated", metrics.truncated))

    private fun hasReadableLabel(node: AccessibilityNodeInfo): Boolean =
        !node.isPassword && listOf(node.text, node.contentDescription, node.hintText)
            .any { !it.isNullOrBlank() }

    private fun isActionableNode(node: AccessibilityNodeInfo): Boolean =
        node.isClickable || node.isEditable || node.isScrollable

    private fun appendNodes(
        node: AccessibilityNodeInfo,
        output: JSONArray,
        depth: Int,
        limit: Int,
    ): Boolean {
        if (depth > MAX_TREE_DEPTH || output.length() >= limit) return true
        var truncated = false
        if (node.isVisibleToUser) {
            val text = if (node.isPassword) "[sensitive]" else node.text?.toString()?.trim().orEmpty()
            val description = if (node.isPassword) {
                "[sensitive]"
            } else {
                node.contentDescription?.toString()?.trim().orEmpty()
            }
            val hintText = if (node.isPassword) {
                "[sensitive]"
            } else {
                node.hintText?.toString()?.trim().orEmpty()
            }
            val viewId = node.viewIdResourceName.orEmpty()
            if (text.isNotEmpty() || description.isNotEmpty() || hintText.isNotEmpty() || viewId.isNotEmpty() || node.isClickable || node.isEditable || node.isScrollable) {
                val bounds = Rect().also(node::getBoundsInScreen)
                output.put(
                    JSONObject()
                        .put("text", text.take(500))
                        .put("description", description.take(300))
                        .put("hintText", hintText.take(300))
                        .put("viewId", viewId.take(200))
                        .put("className", node.className?.toString().orEmpty())
                        .put("clickable", node.isClickable)
                        .put("editable", node.isEditable)
                        .put("scrollable", node.isScrollable)
                        .put("bounds", "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"),
                )
            }
        }
        for (index in 0 until node.childCount) {
            if (output.length() >= limit) return true
            node.getChild(index)?.let {
                truncated = appendNodes(it, output, depth + 1, limit) || truncated
            }
        }
        return truncated
    }

    private fun clickByText(request: JSONObject): JSONObject {
        val text = request.optString("text").trim()
        val index = request.optInt("index", 0)
        if (text.isEmpty() || index < 0) return result(false, "INVALID_SELECTOR")
        val root = freshRootInActiveWindow() ?: return result(false, "NO_ACTIVE_WINDOW")
        val matches = findMatchingNodes(root, text)
            .filter { clickableAncestor(it) != null }
            .distinctBy { nodeKey(clickableAncestor(it) ?: it) }
        if (index >= matches.size) return result(false, "TARGET_NOT_FOUND").put("matchCount", matches.size)
        val target = clickableAncestor(matches[index]) ?: return result(false, "TARGET_NOT_CLICKABLE")
        if (request.optBoolean("readOnly") && mutableControl(target)) {
            return result(false, "READ_ONLY_ACTION_REJECTED").put("matchCount", matches.size)
        }
        val packageName = root.packageName?.toString().orEmpty()
        val performed = if (packageName == "com.android.chrome") {
            val bounds = Rect().also(target::getBoundsInScreen)
            dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(
                        Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) },
                        0,
                        80,
                    ))
                    .build(),
                null,
                null,
            )
        } else {
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return actionResult(performed, "ACTION_PERFORMED")
            .put("matchCount", matches.size)
    }

    private fun setText(request: JSONObject): JSONObject {
        val selector = request.optString("selector").trim()
        val value = request.optString("text")
        val index = request.optInt("index", 0)
        if (value.length > 10_000 || index < 0) return result(false, "INVALID_TEXT")
        val root = freshRootInActiveWindow() ?: return result(false, "NO_ACTIVE_WINDOW")
        val matches = collectNodes(root).filter { node ->
            node.isEditable && (selector.isEmpty() || matchesText(node, selector))
        }
        if (index >= matches.size) return result(false, "TARGET_NOT_FOUND").put("matchCount", matches.size)
        if (matches[index].isPassword) return result(false, "SENSITIVE_TARGET").put("matchCount", matches.size)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val performed = matches[index].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return actionResult(performed, "TEXT_SET").put("matchCount", matches.size)
    }

    private fun scroll(direction: String): JSONObject {
        val root = freshRootInActiveWindow() ?: return result(false, "NO_ACTIVE_WINDOW")
        val target = collectNodes(root).firstOrNull { it.isScrollable }
            ?: return result(false, "SCROLL_TARGET_NOT_FOUND")
        val action = if (direction == "backward") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return actionResult(target.performAction(action), "ACTION_PERFORMED")
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_TREE_DEPTH || result.size >= 300) return
            if (node.isVisibleToUser) result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        return result
    }

    private fun freshRootInActiveWindow(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) clearCache()
        val activeRoot = rootInActiveWindow
            ?: windows.firstOrNull { it.isActive }?.root
            ?: windows.firstOrNull { it.isFocused }?.root
            ?: return null
        return if (activeRoot.refresh()) {
            activeRoot
        } else {
            rootInActiveWindow
                ?: windows.firstOrNull { it.isActive }?.root
                ?: windows.firstOrNull { it.isFocused }?.root
        }
    }

    private fun systemWindowRequiresUser(): Boolean {
        if (windows.any {
                it.isActive && it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM
            }
        ) return true
        val recentEvent = SystemClock.elapsedRealtime() - lastAccessibilityEventAtMillis <= SYSTEM_WINDOW_EVENT_TTL_MILLIS
        val recentUnreadableAction = SystemClock.elapsedRealtime() - lastUnreadableActionAtMillis <= SYSTEM_WINDOW_EVENT_TTL_MILLIS
        return recentUnreadableAction || (recentEvent && lastAccessibilityPackage in PROTECTED_SYSTEM_PACKAGES)
    }

    private fun findMatchingNodes(root: AccessibilityNodeInfo, selector: String): List<AccessibilityNodeInfo> {
        val all = collectNodes(root)
        val exact = all.filter { matchesText(it, selector, exact = true) }
        return if (exact.isNotEmpty()) exact else all.filter { matchesText(it, selector, exact = false) }
    }

    private fun matchesText(node: AccessibilityNodeInfo, selector: String, exact: Boolean = false): Boolean {
        val needle = selector.lowercase(Locale.ROOT)
        return listOf(node.text, node.contentDescription, node.hintText, node.viewIdResourceName)
            .mapNotNull { it?.toString()?.lowercase(Locale.ROOT) }
            .any { if (exact) it == needle else it.contains(needle) }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(6) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    private fun mutableControl(node: AccessibilityNodeInfo): Boolean {
        fun isMutable(candidate: AccessibilityNodeInfo): Boolean {
            val className = candidate.className?.toString().orEmpty()
            return candidate.isEditable || candidate.isCheckable || listOf(
                "Button", "Switch", "CheckBox", "RadioButton", "SeekBar",
            ).any { className.endsWith(it) }
        }
        if (isMutable(node)) return true
        return collectNodes(node).any(::isMutable)
    }

    private fun nodeKey(node: AccessibilityNodeInfo): String {
        val bounds = Rect().also(node::getBoundsInScreen)
        return listOf(
            node.text?.toString().orEmpty(),
            node.contentDescription?.toString().orEmpty(),
            node.viewIdResourceName.orEmpty(),
            node.className?.toString().orEmpty(),
            bounds.flattenToString(),
        ).joinToString("|")
    }

    private fun actionResult(ok: Boolean, successState: String): JSONObject =
        result(ok, if (ok) successState else "ACTION_REJECTED")

    private fun result(ok: Boolean, state: String): JSONObject =
        JSONObject().put("ok", ok).put("state", state)

    private fun isSafeCorrelationId(value: String): Boolean =
        value.length in 1..128 && value.all { it.isLetterOrDigit() || it in "-_.:" }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                result.set(block())
            } catch (throwable: Throwable) {
                error.set(throwable)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(10, TimeUnit.SECONDS)) throw IllegalStateException("main_thread_timeout")
        error.get()?.let { throw it }
        return result.get()
    }

    companion object {
        private const val TAG = "PhoneAutomationService"
        const val PORT = 8768
        private const val MAX_TREE_DEPTH = 64
        private const val OBSERVATION_RETRY_MILLIS = 250L
        private const val SYSTEM_WINDOW_EVENT_TTL_MILLIS = 120_000L
        private val PROTECTED_SYSTEM_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.settings",
        )
        private const val TARGET_OBSERVATION_ATTEMPTS = 60
        private const val POST_ACTION_OBSERVATION_ATTEMPTS = 20
        private val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, PhoneAutomationService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.equals(component, ignoreCase = true) }
        }
    }
}

private class LoopbackServer(
    private val service: PhoneAutomationService,
    private val token: String,
    private val onBound: () -> Unit,
    private val onStopped: () -> Unit,
    private val onError: (stage: String, error: Throwable) -> Unit,
) {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var closed = false

    fun start() {
        executor.execute {
            val server = ServerSocket()
            socket = server
            try {
                if (closed) return@execute
                server.reuseAddress = true
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PhoneAutomationService.PORT))
                if (closed) return@execute
                onBound()
                while (!server.isClosed) {
                    server.accept().use { client ->
                        try {
                            handle(client)
                        } catch (error: Exception) {
                            if (!closed) onError("client", error)
                        }
                    }
                }
            } catch (error: Exception) {
                if (!closed) onError(if (server.isBound) "accept" else "bind", error)
            } finally {
                runCatching { server.close() }
                socket = null
                onStopped()
            }
        }
    }

    fun close() {
        closed = true
        runCatching { socket?.close() }
        executor.shutdownNow()
    }

    private fun handle(client: Socket) {
        client.soTimeout = 20_000
        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream())
        val request = readRequest(input)
        val authorization = request.headers["authorization"].orEmpty()
        val response = if (authorization != "Bearer $token") {
            JSONObject().put("ok", false).put("state", "UNAUTHORIZED")
        } else if (request.method != "POST" || request.path != "/v1/actions") {
            JSONObject().put("ok", false).put("state", "NOT_FOUND")
        } else {
            runCatching {
                service.execute(
                    request = JSONObject(request.body),
                    traceId = request.headers["x-mobile-bot-trace-id"],
                    runId = request.headers["x-mobile-bot-run-id"],
                    automationId = request.headers["x-mobile-bot-automation-id"],
                    agentId = request.headers["x-mobile-bot-agent-id"],
                )
            }
                .getOrElse { JSONObject().put("ok", false).put("state", "EXECUTOR_ERROR") }
        }
        val bytes = response.toString().toByteArray(StandardCharsets.UTF_8)
        val status = if (response.optString("state") == "UNAUTHORIZED") "401 Unauthorized" else "200 OK"
        output.write(
            "HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                .toByteArray(StandardCharsets.US_ASCII),
        )
        output.write(bytes)
        output.flush()
    }

    private fun readRequest(input: BufferedInputStream): HttpRequest {
        val headerBytes = ArrayList<Byte>()
        var tail = ""
        while (headerBytes.size < 16_384 && !tail.endsWith("\r\n\r\n")) {
            val next = input.read()
            if (next < 0) break
            headerBytes += next.toByte()
            tail = (tail + next.toChar()).takeLast(4)
        }
        val headerText = headerBytes.toByteArray().toString(StandardCharsets.US_ASCII)
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull()?.split(' ') ?: emptyList()
        val headers = lines.drop(1).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).trim().lowercase(Locale.ROOT) to line.substring(separator + 1).trim()
        }.toMap()
        val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 65_536) ?: 0
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(body, offset, length - offset)
            if (count < 0) break
            offset += count
        }
        return HttpRequest(
            method = requestLine.getOrNull(0).orEmpty(),
            path = requestLine.getOrNull(1).orEmpty(),
            headers = headers,
            body = body.copyOf(offset).toString(StandardCharsets.UTF_8),
        )
    }
}

private data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)
