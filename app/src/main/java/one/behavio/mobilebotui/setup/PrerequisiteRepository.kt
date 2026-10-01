package one.behavio.mobilebotui.setup

import one.behavio.mobilebotui.automation.DevelopmentActionLogger
import one.behavio.mobilebotui.automation.DevelopmentActions
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import one.behavio.mobilebotui.R

class PrerequisiteRepository(
    private val context: Context,
    private val termux: TermuxCommandClient = TermuxCommandClient(context),
    private val host: HostHealthClient = HostHealthClient(),
) {
    private val log = DevelopmentActionLogger.get(context)
    private val preferences = context.getSharedPreferences("setup", Context.MODE_PRIVATE)

    fun selectedRuntime(): RuntimeKind = preferences.getString("runtime", RuntimeKind.CODEX.wireValue)
        ?.let { value -> RuntimeKind.entries.firstOrNull { it.wireValue == value } }
        ?: RuntimeKind.CODEX

    suspend fun selectRuntime(runtime: RuntimeKind) {
        preferences.edit().putString("runtime", runtime.wireValue).apply()
        runCatching { host.selectRuntime(runtime) }
    }

    suspend fun inspect(onProgress: (String) -> Unit = {}): SetupFacts {
        val facts = inspectFacts(onProgress)
        log.record(DevelopmentActions.SETUP_INSPECTED, mapOf(
            "stage" to resolveSetupStage(facts).name,
            "termuxInstalled" to facts.termuxInstalled,
            "permissionGranted" to facts.runCommandPermissionGranted,
            "bridgeState" to facts.bridgeState.name,
            "hostReachable" to facts.hostStatus?.reachable,
            "hostVersion" to facts.hostStatus?.hostVersion,
            "expectedHostVersion" to TermuxContract.HOST_VERSION,
            "environmentRevision" to facts.hostStatus?.environmentRevision,
            "expectedEnvironmentRevision" to TermuxContract.ENVIRONMENT_REVISION,
            "codexInstalled" to facts.hostStatus?.codexInstalled,
            "codexVersion" to facts.hostStatus?.codexVersion,
            "codexAuthenticated" to facts.hostStatus?.codexAuthenticated,
            "selectedRuntime" to facts.selectedRuntime.wireValue,
            "localAiStatus" to facts.hostStatus?.localAiStatus,
            "localAiModelInstalled" to facts.hostStatus?.localAiModelInstalled,
            "localAiReady" to facts.hostStatus?.localAiReady,
        ))
        return facts
    }

    private suspend fun inspectFacts(onProgress: (String) -> Unit): SetupFacts {
        val preferredRuntime = selectedRuntime()
        onProgress(context.getString(R.string.setup_checking_termux_installed))
        if (!isTermuxInstalled()) return SetupFacts(termuxInstalled = false)

        onProgress(context.getString(R.string.setup_checking_termux_access))
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            TermuxContract.RUN_COMMAND_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            return SetupFacts(
                termuxInstalled = true,
                runCommandPermissionGranted = false,
            )
        }

        var bridgeVerified = false
        return try {
            onProgress(context.getString(R.string.setup_checking_termux_connection))
            val probe = termux.probe()
            if (probe.succeeded && probe.stdout.contains("mobile-bot-ui-bridge-ready")) {
                bridgeVerified = true
                // The host authorizes the app with this token; reinstalling the app creates a new one.
                termux.syncDeviceBridgeToken()
                onProgress(context.getString(R.string.setup_checking_preparation_running))
                val activePreparation = termux.activePreparationDetail()
                if (activePreparation != null) return SetupFacts(
                    termuxInstalled = true, runCommandPermissionGranted = true,
                    bridgeState = BridgeState.ENABLED, busy = true, detail = activePreparation,
                    selectedRuntime = preferredRuntime,
                )
                onProgress(context.getString(R.string.setup_checking_codex_files))
                var hostStatus = host.read()
                if (!hostStatus.reachable) {
                    onProgress(context.getString(R.string.setup_starting_components))
                    termux.startHost()
                    for (attempt in 0 until 60) {
                        delay(250)
                        hostStatus = host.read()
                        if (hostStatus.reachable) break
                    }
                }
                SetupFacts(
                    termuxInstalled = true,
                    runCommandPermissionGranted = true,
                    bridgeState = BridgeState.ENABLED,
                    hostStatus = hostStatus,
                    selectedRuntime = if (hostStatus.reachable) hostStatus.selectedRuntime else preferredRuntime,
                )
            } else {
                val message = listOfNotNull(probe.errorMessage, probe.stderr.takeIf { it.isNotBlank() })
                    .joinToString("\n")
                val disabled = message.contains("allow-external-apps", ignoreCase = true)
                SetupFacts(
                    termuxInstalled = true,
                    runCommandPermissionGranted = true,
                    bridgeState = if (disabled) BridgeState.DISABLED else BridgeState.FAILED,
                    detail = if (disabled) context.getString(R.string.setup_termux_access_disabled)
                        else context.getString(R.string.setup_termux_components_failed),
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            SetupFacts(
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                bridgeState = if (bridgeVerified) BridgeState.ENABLED else BridgeState.FAILED,
                detail = if (bridgeVerified) context.getString(R.string.setup_readiness_check_failed)
                    else context.getString(R.string.setup_termux_connect_failed),
            )
        }
    }

    suspend fun bootstrap(runtime: RuntimeKind, onProgress: (String) -> Unit = {}): TermuxCommandResult {
        onProgress(context.getString(R.string.setup_checking_ready))
        val initial = inspect(onProgress)
        if (initial.busy) {
            onProgress(initial.detail ?: context.getString(R.string.setup_in_progress))
            return TermuxCommandResult("already-running", "", "", 0, null, null)
        }
        if (initial.hostStatus?.isCurrentRuntime() == true && initial.selectedRuntime == runtime) {
            log.record(DevelopmentActions.SETUP_PREPARATION, mapOf("phase" to "skipped_current_runtime"))
            onProgress(context.getString(R.string.setup_already_ready))
            return TermuxCommandResult("already-ready", "", "", 0, null, null)
        }
        val result = termux.bootstrap(runtime, onProgress)
        if (result.succeeded) {
            repeat(2) {
                onProgress(context.getString(R.string.setup_starting_app))
                termux.startHost()
                onProgress(context.getString(R.string.setup_checking_environment))
                repeat(60) {
                    delay(250)
                    val current = host.read()
                    if (current.isCurrentRuntime() && current.selectedRuntime == runtime) {
                        onProgress(context.getString(R.string.setup_environment_ready))
                        return result
                    }
                }
            }
        }
        return result
    }

    suspend fun startCodexLogin(): TermuxCommandResult = termux.startCodexLogin()

    suspend fun prepareLocalAi(): Boolean = host.prepareLocalAi()

    @Suppress("DEPRECATION")
    private fun isTermuxInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TermuxContract.PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
