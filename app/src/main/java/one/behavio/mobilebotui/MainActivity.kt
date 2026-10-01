package one.behavio.mobilebotui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import one.behavio.mobilebotui.setup.SetupViewModel
import one.behavio.mobilebotui.setup.TermuxContract
import one.behavio.mobilebotui.setup.TermuxCommandClient
import one.behavio.mobilebotui.setup.BridgeState
import one.behavio.mobilebotui.ui.MobileBotApp
import one.behavio.mobilebotui.ui.MobileBotTheme
import one.behavio.mobilebotui.ui.GraphicTheme
import one.behavio.mobilebotui.ui.VisualPreferences
import one.behavio.mobilebotui.workspace.WorkspaceViewModel
import one.behavio.mobilebotui.automation.PhoneAutomationService
import one.behavio.mobilebotui.automation.AutomationBackgroundCoordinator
import one.behavio.mobilebotui.automation.DevelopmentActionLogger
import one.behavio.mobilebotui.automation.DevelopmentActions
import one.behavio.mobilebotui.automation.ActionCorrelation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import one.behavio.mobilebotui.R
import androidx.compose.ui.platform.LocalResources

class MainActivity : ComponentActivity() {
    private val setupViewModel: SetupViewModel by viewModels()
    private val workspaceViewModel: WorkspaceViewModel by viewModels()
    private val phoneAutomationEnabled = MutableStateFlow(false)
    private val phoneBridgeVerified = MutableStateFlow(false)
    private val phoneCheckGeneration = MutableStateFlow(0)
    private val skippedOnboardingStops = MutableStateFlow<Set<String>>(emptySet())
    private val phoneAutomationOnboardingSkipped = MutableStateFlow(false)
    private val graphicTheme = MutableStateFlow(GraphicTheme.HEROES)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePhoneTaskReturn(intent)
        val actionLog = DevelopmentActionLogger.get(applicationContext)
        actionLog.record(
            DevelopmentActions.APP_CREATED,
            mapOf("restoredState" to (savedInstanceState != null)),
        )
        AutomationBackgroundCoordinator.schedule(applicationContext)
        skippedOnboardingStops.value = PhoneAutomationOnboardingPreferences.skippedStops(this)
        phoneAutomationOnboardingSkipped.value = PhoneAutomationOnboardingPreferences.isSkipped(this)
        one.behavio.mobilebotui.ui.CustomThemes.load(this)
        graphicTheme.value = VisualPreferences.theme(this)
        setContent {
            val context = LocalContext.current
            val resources = LocalResources.current
            val facts by setupViewModel.facts.collectAsStateWithLifecycle()
            val workspace by workspaceViewModel.state.collectAsStateWithLifecycle()
            val automationEnabled by phoneAutomationEnabled.collectAsStateWithLifecycle()
            val automationConnected by PhoneAutomationService.connected.collectAsStateWithLifecycle()
            val bridgeVerified by phoneBridgeVerified.collectAsStateWithLifecycle()
            val checkGeneration by phoneCheckGeneration.collectAsStateWithLifecycle()
            val skippedStops by skippedOnboardingStops.collectAsStateWithLifecycle()
            val automationOnboardingSkipped by phoneAutomationOnboardingSkipped.collectAsStateWithLifecycle()
            val activeGraphicTheme by graphicTheme.collectAsStateWithLifecycle()
            LaunchedEffect(workspace.customThemes, workspace.snapshotLoaded) {
                if (workspace.snapshotLoaded) {
                    one.behavio.mobilebotui.ui.CustomThemes.install(context, workspace.customThemes)
                    graphicTheme.value = VisualPreferences.theme(context)
                }
            }
            val termuxCommandClient = remember { TermuxCommandClient(context.applicationContext) }
            LaunchedEffect(facts.hostStatus?.hostVersion, facts.hostStatus?.codexAuthenticated, facts.hostStatus?.localAiReady) {
                if (facts.hostStatus?.codexAuthenticated == true || facts.hostStatus?.localAiReady == true) workspaceViewModel.refresh()
            }
            LaunchedEffect(
                automationConnected,
                facts.runCommandPermissionGranted,
                facts.bridgeState,
                facts.hostStatus?.hostVersion,
                facts.hostStatus?.environmentRevision,
                checkGeneration,
            ) {
                phoneBridgeVerified.value = false
                if (
                    automationConnected &&
                    facts.runCommandPermissionGranted &&
                    facts.bridgeState == BridgeState.ENABLED
                ) {
                    verifyPhoneBridge(termuxCommandClient, notify = false)
                }
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                val settingsRequired = !granted &&
                    !shouldShowRequestPermissionRationale(TermuxContract.RUN_COMMAND_PERMISSION)
                actionLog.record(DevelopmentActions.SETUP_ACTION_REQUESTED, mapOf(
                    "actionTarget" to "termux_permission_result", "granted" to granted,
                    "settingsRequired" to settingsRequired,
                ))
                if (settingsRequired) {
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:$packageName")))
                }
                setupViewModel.refresh()
            }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                actionLog.record(
                    DevelopmentActions.SETUP_ACTION_REQUESTED,
                    mapOf("actionTarget" to "notification_permission_result", "granted" to granted),
                )
                AutomationBackgroundCoordinator.reconcileNow(
                    context.applicationContext,
                    "notification_permission_result",
                )
            }
            LaunchedEffect(workspace.automations.isNotEmpty()) {
                if (
                    workspace.automations.isNotEmpty() &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    actionLog.record(
                        DevelopmentActions.SETUP_ACTION_REQUESTED,
                        mapOf("actionTarget" to "notification_permission"),
                    )
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            MobileBotTheme(graphicTheme = activeGraphicTheme) {
                MobileBotApp(
                    facts = facts,
                    workspace = workspace,
                    phoneAutomationEnabled = automationEnabled,
                    phoneAutomationConnected = automationConnected && bridgeVerified,
                    phoneAutomationOnboardingSkipped = automationOnboardingSkipped,
                    skippedOnboardingStops = skippedStops,
                    graphicTheme = activeGraphicTheme,
                    onGraphicThemeChange = { selectedTheme ->
                        VisualPreferences.saveTheme(context, selectedTheme)
                        graphicTheme.value = selectedTheme
                    },
                    onSkipOnboardingStop = { stop ->
                        PhoneAutomationOnboardingPreferences.skipStop(context, stop)
                        skippedOnboardingStops.value = PhoneAutomationOnboardingPreferences.skippedStops(context)
                        actionLog.record(DevelopmentActions.SETUP_ACTION_REQUESTED, mapOf("actionTarget" to "onboarding_stop_skipped", "stopId" to stop))
                    },
                    onRefresh = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "refresh"),
                        )
                        setupViewModel.refresh()
                    },
                    onInstallTermux = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "install_termux"),
                        )
                        val alreadyInstalled = try {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(TermuxContract.PACKAGE_NAME, 0)
                            true
                        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
                        if (alreadyInstalled) {
                            actionLog.record(DevelopmentActions.SETUP_ACTION_REQUESTED,
                                mapOf("actionTarget" to "skip_termux_download_already_installed"))
                            setupViewModel.refresh()
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TermuxContract.TERMUX_INSTALL_URL)))
                        }
                    },
                    onRequestPermission = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "termux_run_command_permission"),
                        )
                        permissionLauncher.launch(TermuxContract.RUN_COMMAND_PERMISSION)
                    },
                    onOpenTermux = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "open_termux"),
                        )
                        context.packageManager.getLaunchIntentForPackage(TermuxContract.PACKAGE_NAME)
                            ?.let(context::startActivity)
                    },
                    onConfigureTermux = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "configure_termux"),
                        )
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                resources.getString(R.string.termux_command_clip_label),
                                TermuxContract.ENABLE_EXTERNAL_APPS_COMMAND,
                            ),
                        )
                        Toast.makeText(
                            context,
                            resources.getString(R.string.termux_command_copied),
                            Toast.LENGTH_LONG,
                        ).show()
                        context.packageManager.getLaunchIntentForPackage(TermuxContract.PACKAGE_NAME)
                            ?.let(context::startActivity)
                    },
                    onBootstrap = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "bootstrap"),
                        )
                        setupViewModel.bootstrap()
                    },
                    onLogin = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "codex_login"),
                        )
                        setupViewModel.login()
                    },
                    onSelectRuntime = { runtime ->
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "runtime_selection", "runtime" to runtime.wireValue),
                        )
                        setupViewModel.selectRuntime(runtime)
                    },
                    onPrepareLocalAi = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "local_ai_prepare"),
                        )
                        setupViewModel.prepareLocalAi()
                    },
                    onOpenPhoneAutomationSettings = {
                        actionLog.record(
                            DevelopmentActions.SETUP_ACTION_REQUESTED,
                            mapOf("actionTarget" to "accessibility_settings"),
                        )
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onCheckPhoneAutomation = {
                        phoneAutomationEnabled.value = PhoneAutomationService.isEnabled(context)
                        lifecycleScope.launch { verifyPhoneBridge(termuxCommandClient, notify = true) }
                    },
                    onSkipPhoneAutomationOnboarding = {
                        actionLog.record(DevelopmentActions.SETUP_ACTION_REQUESTED, mapOf("actionTarget" to "onboarding_continue", "connected" to automationConnected))
                        PhoneAutomationOnboardingPreferences.markSkipped(context)
                        phoneAutomationOnboardingSkipped.value = true
                    },
                    onSaveOwnerProfile = { about ->
                        val correlation = ActionCorrelation()
                        actionLog.record(
                            DevelopmentActions.OWNER_PROFILE_SAVED,
                            mapOf("profileLength" to about.trim().length),
                            correlation,
                        )
                        workspaceViewModel.saveOwnerProfile(about, correlation.traceId)
                    },
                    onCreateAgentTask = { draft ->
                        val correlation = ActionCorrelation()
                        actionLog.record(
                            DevelopmentActions.AGENT_TASK_CREATE_REQUESTED,
                            mapOf(
                                "nameLength" to draft.agentName.trim().length,
                                "promptLength" to draft.taskPrompt.trim().length,
                            ),
                            correlation,
                        )
                        workspaceViewModel.createAgentAndSend(draft, correlation.traceId)
                    },
                    onSelectAgent = { agentId, newConversation ->
                        actionLog.record(
                            DevelopmentActions.WORKSPACE_UI_ACTION,
                            mapOf(
                                "operation" to "select_agent",
                                "agentId" to agentId,
                                "newConversation" to newConversation,
                            ),
                        )
                        workspaceViewModel.selectAgent(agentId, newConversation)
                    },
                    onOpenConversation = { agentId, conversationId, title ->
                        val correlation = ActionCorrelation()
                        actionLog.record(
                            DevelopmentActions.WORKSPACE_UI_ACTION,
                            mapOf(
                                "operation" to "open_conversation",
                                "agentId" to agentId,
                                "conversationId" to conversationId,
                            ),
                            correlation,
                        )
                        workspaceViewModel.openConversation(
                            agentId,
                            conversationId,
                            title,
                            correlation.traceId,
                        )
                    },
                    onSendTask = { agentId, prompt ->
                        val correlation = ActionCorrelation()
                        actionLog.record(
                            DevelopmentActions.WORKSPACE_UI_ACTION,
                            mapOf(
                                "operation" to "send_task",
                                "agentId" to agentId,
                                "promptLength" to prompt.length,
                            ),
                            correlation,
                        )
                        workspaceViewModel.send(agentId, prompt, correlation.traceId)
                    },
                    onSaveAgentAppearance = { agentId, name, avatarIndex ->
                        val correlation = ActionCorrelation()
                        actionLog.record(
                            DevelopmentActions.WORKSPACE_UI_ACTION,
                            mapOf(
                                "operation" to "rename_agent",
                                "agentId" to agentId,
                                "nameLength" to name.trim().length,
                            ),
                            correlation,
                        )
                        workspaceViewModel.renameAgent(agentId, name, correlation.traceId, avatarIndex)
                    },
                    onLoadAgentConversations = { agentId, loadMore ->
                        workspaceViewModel.loadAgentConversations(agentId, loadMore)
                    },
                    onSaveAgentRole = { agentId, roleDescription ->
                        val correlation = ActionCorrelation()
                        actionLog.record(
                            DevelopmentActions.WORKSPACE_UI_ACTION,
                            mapOf("operation" to "save_agent_role", "agentId" to agentId, "roleLength" to roleDescription.trim().length),
                            correlation,
                        )
                        workspaceViewModel.saveAgentRole(agentId, roleDescription, correlation.traceId)
                    },
                    onSaveAgentSkills = { agentId, skillIds ->
                        val correlation = ActionCorrelation()
                        actionLog.record(
                            DevelopmentActions.WORKSPACE_UI_ACTION,
                            mapOf(
                                "operation" to "save_agent_skills",
                                "agentId" to agentId,
                                "assignmentCount" to skillIds.size,
                            ),
                            correlation,
                        )
                        workspaceViewModel.saveAgentSkills(agentId, skillIds, correlation.traceId)
                    },
                    onDeleteAgent = workspaceViewModel::deleteAgent,
                    onSetAutomationEnabled = { automationId, enabled ->
                        workspace.automations.firstOrNull { it.id == automationId }?.let { automation ->
                            val correlation = ActionCorrelation()
                            actionLog.record(
                                DevelopmentActions.WORKSPACE_UI_ACTION,
                                mapOf(
                                    "operation" to "set_automation_enabled",
                                    "agentId" to automation.agent.id,
                                    "automationId" to automationId,
                                    "enabled" to enabled,
                                ),
                                correlation,
                            )
                            workspaceViewModel.setAutomationEnabled(automationId, enabled, correlation.traceId)
                        }
                    },
                )
            }
        }
    }

    private suspend fun verifyPhoneBridge(client: TermuxCommandClient, notify: Boolean) {
        phoneBridgeVerified.value = false
        val state = if (!PhoneAutomationService.isEnabled(this)) {
            "SERVICE_DISABLED"
        } else if (!PhoneAutomationService.connected.value) {
            "BRIDGE_UNAVAILABLE"
        } else {
            runCatching {
                check(client.syncDeviceBridgeToken().succeeded)
                val result = client.probePhone()
                check(result.succeeded)
                val response = JSONObject(result.stdout.trim())
                if (response.optBoolean("ok") && response.optString("state") == "READY") {
                    "READY"
                } else response.optString("state", "PHONE_PROBE_FAILED")
            }.getOrDefault("PHONE_PROBE_FAILED")
        }
        phoneBridgeVerified.value = state == "READY" && PhoneAutomationService.connected.value
        if (notify) Toast.makeText(this, when (state) {
            "READY" -> getString(R.string.phone_check_ready)
            "SERVICE_DISABLED" -> getString(R.string.phone_check_service_disabled)
            "WAITING_FOR_UNLOCK" -> getString(R.string.phone_check_unlock)
            "NO_READABLE_CONTENT", "NO_ACTIVE_WINDOW" -> getString(R.string.phone_check_no_content)
            else -> getString(R.string.phone_check_failed)
        }, Toast.LENGTH_LONG).show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePhoneTaskReturn(intent)
    }

    private fun handlePhoneTaskReturn(intent: Intent?) {
        if (intent?.action != ACTION_PHONE_TASK_RETURN) return
        val runId = intent.getStringExtra("runId") ?: return
        val agentId = intent.getStringExtra("agentId") ?: return
        val conversationId = intent.getStringExtra("conversationId") ?: return
        if (listOf(runId, agentId, conversationId).any { value ->
                value.length !in 1..128 || value.any { !it.isLetterOrDigit() && it !in "-_.:" }
            }) return
        DevelopmentActionLogger.get(applicationContext).record(
            DevelopmentActions.WORKSPACE_UI_ACTION,
            mapOf("operation" to "return_from_phone_task", "runId" to runId,
                "agentId" to agentId, "conversationId" to conversationId),
        )
        workspaceViewModel.returnFromPhoneTask(runId, agentId, conversationId)
        intent.removeExtra("runId")
        intent.removeExtra("agentId")
        intent.removeExtra("conversationId")
    }

    companion object {
        const val ACTION_PHONE_TASK_RETURN = "one.behavio.mobilebotui.PHONE_TASK_RETURN"
    }

    override fun onResume() {
        super.onResume()
        phoneCheckGeneration.value += 1
        DevelopmentActionLogger.get(applicationContext).record(
            DevelopmentActions.APP_RESUMED,
            mapOf("activity" to "MainActivity"),
        )
        phoneAutomationEnabled.value = PhoneAutomationService.isEnabled(this)
        setupViewModel.refresh()
        workspaceViewModel.refresh()
        AutomationBackgroundCoordinator.reconcileNow(applicationContext, "app_resume")
    }
}

internal object PhoneAutomationOnboardingPreferences {
    private const val PREFERENCES = "phone_automation_onboarding"
    private const val SKIPPED = "skipped"

    fun skippedStops(context: Context): Set<String> =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet("skipped_stops", emptySet())?.toSet().orEmpty()

    fun skipStop(context: Context, stop: String) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putStringSet("skipped_stops", skippedStops(context) + stop).apply()
    }

    fun isSkipped(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(SKIPPED, false)

    fun markSkipped(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SKIPPED, true)
            .apply()
    }
}
