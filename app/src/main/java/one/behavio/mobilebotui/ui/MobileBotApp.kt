package one.behavio.mobilebotui.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.behavio.mobilebotui.setup.SetupFacts
import one.behavio.mobilebotui.setup.SetupStage
import one.behavio.mobilebotui.setup.TermuxContract
import one.behavio.mobilebotui.setup.RuntimeKind
import one.behavio.mobilebotui.setup.resolveSetupStage
import one.behavio.mobilebotui.R
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalResources

@Composable
fun MobileBotApp(
    facts: SetupFacts,
    workspace: WorkspaceUiState = WorkspaceUiState(),
    phoneAutomationEnabled: Boolean = false,
    phoneAutomationConnected: Boolean = false,
    phoneAutomationOnboardingSkipped: Boolean = false,
    skippedOnboardingStops: Set<String> = emptySet(),
    graphicTheme: GraphicTheme = GraphicTheme.HEROES,
    onGraphicThemeChange: (GraphicTheme) -> Unit = {},
    onSkipOnboardingStop: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onInstallTermux: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onConfigureTermux: () -> Unit,
    onBootstrap: () -> Unit,
    onLogin: () -> Unit,
    onSelectRuntime: (RuntimeKind) -> Unit = {},
    onPrepareLocalAi: () -> Unit = {},
    onOpenPhoneAutomationSettings: () -> Unit = {},
    onCheckPhoneAutomation: () -> Unit = {},
    onSkipPhoneAutomationOnboarding: () -> Unit = {},
    onSaveOwnerProfile: (String) -> Unit = {},
    onCreateAgentTask: (CreateAgentTaskDraft) -> Unit,
    onSelectAgent: (agentId: String, newConversation: Boolean) -> Unit = { _, _ -> },
    onOpenConversation: (agentId: String, conversationId: String, title: String) -> Unit = { _, _, _ -> },
    onLoadAgentConversations: (agentId: String, loadMore: Boolean) -> Unit = { _, _ -> },
    onSendTask: (agentId: String, prompt: String) -> Unit = { _, _ -> },
    onRenameAgent: (agentId: String, name: String) -> Unit = { _, _ -> },
    onSaveAgentAppearance: (String, String, Int) -> Unit = { id, name, _ -> onRenameAgent(id, name) },
    onSaveAgentRole: (agentId: String, roleDescription: String) -> Unit = { _, _ -> },
    onSaveAgentSkills: (agentId: String, skillIds: List<String>) -> Unit = { _, _ -> },
    onSetAutomationEnabled: (automationId: String, enabled: Boolean) -> Unit = { _, _ -> },
    onDeleteAgent: (String) -> Unit = {},
) {
    val stage = resolveSetupStage(facts)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(targetState = stage, label = "setup-stage") { current ->
            if (current == SetupStage.READY) {
                ReadyAppScreen(
                    workspace = workspace,
                    graphicTheme = graphicTheme,
                    onGraphicThemeChange = onGraphicThemeChange,
                    skippedOnboardingStops = skippedOnboardingStops,
                    onSkipOnboardingStop = onSkipOnboardingStop,
                    onRefresh = onRefresh,
                    phoneAutomationEnabled = phoneAutomationEnabled,
                    phoneAutomationConnected = phoneAutomationConnected,
                    phoneAutomationOnboardingSkipped = phoneAutomationOnboardingSkipped,
                    onOpenPhoneAutomationSettings = onOpenPhoneAutomationSettings,
                    onCheckPhoneAutomation = onCheckPhoneAutomation,
                    onSkipPhoneAutomationOnboarding = onSkipPhoneAutomationOnboarding,
                    onSaveOwnerProfile = onSaveOwnerProfile,
                    onCreateAgentTask = onCreateAgentTask,
                    onSelectAgent = onSelectAgent,
                    onOpenConversation = onOpenConversation,
                    onLoadAgentConversations = onLoadAgentConversations,
                    onSendTask = onSendTask,
                    onSaveAgentAppearance = onSaveAgentAppearance,
                    onSaveAgentRole = onSaveAgentRole,
                    onSaveAgentSkills = onSaveAgentSkills,
                    onSetAutomationEnabled = onSetAutomationEnabled,
                    onDeleteAgent = onDeleteAgent,
                )
            } else {
                OnboardingTrailScreen(
                    baseReady = false, teamReady = false, skillsReady = false,
                    phoneEnabled = phoneAutomationEnabled, phoneConnected = phoneAutomationConnected,
                    missionReady = false, skippedStops = skippedOnboardingStops,
                    onBaseAction = onRefresh, onTeamAction = {}, onSkillsAction = {},
                    onPhoneSettings = onOpenPhoneAutomationSettings, onPhoneCheck = onCheckPhoneAutomation,
                    onMissionAction = {}, onSkipStop = onSkipOnboardingStop, onContinue = {},
                    baseContent = {
                SetupScreen(
                    embedded = true,
                    stage = current,
                    facts = facts,
                    onRefresh = onRefresh,
                    onInstallTermux = onInstallTermux,
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onConfigureTermux = onConfigureTermux,
                    onBootstrap = onBootstrap,
                    onLogin = onLogin,
                    onSelectRuntime = onSelectRuntime,
                    onPrepareLocalAi = onPrepareLocalAi,
                )
                    },
                )
            }
        }
    }
}

@Composable
internal fun SetupScreen(
    embedded: Boolean = false,
    stage: SetupStage,
    facts: SetupFacts,
    onRefresh: () -> Unit,
    onInstallTermux: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTermux: () -> Unit,
    onConfigureTermux: () -> Unit,
    onBootstrap: () -> Unit,
    onLogin: () -> Unit,
    onSelectRuntime: (RuntimeKind) -> Unit = {},
    onPrepareLocalAi: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .then(if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
                .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()))
            .padding(horizontal = if (embedded) 0.dp else 24.dp, vertical = if (embedded) 0.dp else 20.dp)
            .testTag("setup-screen"),
    ) {
        if (!embedded) {
            BrandMark()
            Spacer(Modifier.height(44.dp))
        }
        Text(
            text = if (facts.checking) stringResource(R.string.setup_checking_app_title) else setupTitle(stage),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.testTag("setup-title"),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (facts.checking) stringResource(R.string.setup_checking_app_body) else setupDescription(stage),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 25.sp,
        )
        Spacer(Modifier.height(28.dp))
        if (facts.checking || stage == SetupStage.BOOTSTRAPPING || stage == SetupStage.CHECKING || stage == SetupStage.LOCAL_AI_PREPARING) {
            BootstrapProgress(detail = facts.detail ?: if (stage == SetupStage.CHECKING) stringResource(R.string.setup_checking_components) else null)
        } else if (stage == SetupStage.TERMUX_BRIDGE_DISABLED) {
            TermuxAccessGuide()
        } else if (stage != SetupStage.BOOTSTRAP_REQUIRED && stage != SetupStage.LOCAL_AI_REQUIRED && stage != SetupStage.LOCAL_AI_FAILED) {
            SetupChecklist(stage)
        }

        if (!facts.checking && stage == SetupStage.BOOTSTRAP_REQUIRED) {
            Spacer(Modifier.height(20.dp))
            RuntimeChoice(facts.selectedRuntime, onSelectRuntime)
        }

        facts.detail
            ?.takeIf {
                it.isNotBlank() && !facts.checking &&
                    stage != SetupStage.TERMUX_BRIDGE_DISABLED &&
                    stage != SetupStage.BOOTSTRAPPING && stage != SetupStage.CHECKING
            }
            ?.let { detail ->
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = detail.take(700),
                    modifier = Modifier.padding(16.dp).testTag("setup-detail"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        if (!facts.checking) when (stage) {
            SetupStage.CHECKING -> Unit

            SetupStage.BOOTSTRAPPING -> Unit

            SetupStage.TERMUX_MISSING -> PrimaryAction(stringResource(R.string.download_termux), onInstallTermux)
            SetupStage.RUN_COMMAND_PERMISSION_MISSING ->
                PrimaryAction(stringResource(R.string.grant_termux_access), onRequestPermission)
            SetupStage.TERMUX_BRIDGE_DISABLED -> {
                PrimaryAction(stringResource(R.string.copy_step_open_termux), onConfigureTermux)
                Spacer(Modifier.height(12.dp))
                SecondaryAction(stringResource(R.string.done_check_access), onRefresh)
            }
            SetupStage.TERMUX_BRIDGE_FAILED -> {
                PrimaryAction(stringResource(R.string.open_termux), onOpenTermux)
                Spacer(Modifier.height(12.dp))
                SecondaryAction(stringResource(R.string.check_again), onRefresh)
            }
            SetupStage.BOOTSTRAP_REQUIRED ->
                PrimaryAction(if (facts.detail.isNullOrBlank()) stringResource(R.string.start) else stringResource(R.string.try_again), onBootstrap)
            SetupStage.CODEX_LOGIN_REQUIRED ->
                PrimaryAction(stringResource(R.string.codex_sign_in), onLogin)
            SetupStage.LOCAL_AI_REQUIRED ->
                PrimaryAction(stringResource(R.string.download_model_size), onPrepareLocalAi)
            SetupStage.LOCAL_AI_PREPARING -> Unit
            SetupStage.LOCAL_AI_FAILED ->
                PrimaryAction(stringResource(R.string.retry_model_download), onPrepareLocalAi)
            SetupStage.READY -> Unit
        }


    }
}

@Composable
private fun RuntimeChoice(selected: RuntimeKind, onSelect: (RuntimeKind) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("runtime-choice"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ai_mode_question), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.ai_mode_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            RuntimeOption(
                title = "Codex",
                description = stringResource(R.string.ai_mode_codex),
                selected = selected == RuntimeKind.CODEX,
                onClick = { onSelect(RuntimeKind.CODEX) },
                tag = "runtime-codex",
            )
            Spacer(Modifier.height(8.dp))
            RuntimeOption(
                title = stringResource(R.string.ai_mode_local),
                description = stringResource(R.string.ai_mode_local_body),
                selected = selected == RuntimeKind.PI_LOCAL,
                onClick = { onSelect(RuntimeKind.PI_LOCAL) },
                tag = "runtime-local",
            )
        }
    }
}

@Composable
private fun RuntimeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("M", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text("Mobile Bot", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun setupTitle(stage: SetupStage): String = when (stage) {
    SetupStage.CHECKING -> stringResource(R.string.setup_title_checking)
    SetupStage.TERMUX_MISSING -> stringResource(R.string.setup_title_termux_missing)
    SetupStage.RUN_COMMAND_PERMISSION_MISSING -> stringResource(R.string.setup_title_permission)
    SetupStage.TERMUX_BRIDGE_DISABLED -> stringResource(R.string.setup_title_bridge)
    SetupStage.TERMUX_BRIDGE_FAILED -> stringResource(R.string.setup_title_bridge_failed)
    SetupStage.BOOTSTRAP_REQUIRED -> stringResource(R.string.setup_prepare_app)
    SetupStage.BOOTSTRAPPING -> stringResource(R.string.setup_title_preparing)
    SetupStage.CODEX_LOGIN_REQUIRED -> stringResource(R.string.setup_title_codex_login)
    SetupStage.LOCAL_AI_REQUIRED -> stringResource(R.string.setup_title_local_ai)
    SetupStage.LOCAL_AI_PREPARING -> stringResource(R.string.setup_title_local_ai_preparing)
    SetupStage.LOCAL_AI_FAILED -> stringResource(R.string.setup_title_local_ai_failed)
    SetupStage.READY -> stringResource(R.string.ready)
}

@Composable
private fun setupDescription(stage: SetupStage): String = when (stage) {
    SetupStage.CHECKING -> stringResource(R.string.setup_body_checking)
    SetupStage.TERMUX_MISSING ->
        stringResource(R.string.setup_body_termux_missing)
    SetupStage.RUN_COMMAND_PERMISSION_MISSING ->
        stringResource(R.string.setup_body_permission)
    SetupStage.TERMUX_BRIDGE_DISABLED ->
        stringResource(R.string.setup_body_bridge)
    SetupStage.TERMUX_BRIDGE_FAILED ->
        stringResource(R.string.setup_body_bridge_failed)
    SetupStage.BOOTSTRAP_REQUIRED ->
        stringResource(R.string.setup_body_bootstrap)
    SetupStage.BOOTSTRAPPING ->
        stringResource(R.string.setup_body_preparing)
    SetupStage.CODEX_LOGIN_REQUIRED ->
        stringResource(R.string.setup_body_codex_login)
    SetupStage.LOCAL_AI_REQUIRED ->
        stringResource(R.string.setup_body_local_ai)
    SetupStage.LOCAL_AI_PREPARING ->
        stringResource(R.string.setup_body_local_ai_preparing)
    SetupStage.LOCAL_AI_FAILED ->
        stringResource(R.string.setup_body_local_ai_failed)
    SetupStage.READY -> ""
}

@Composable
internal fun BootstrapProgress(detail: String?) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("setup-current-status"),
    ) {
        Text(
            text = detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.setup_starting),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("setup-current-status-text"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TermuxAccessGuide() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("termux-access-guide"),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            GuideStep(
                number = 1,
                title = stringResource(R.string.termux_step_copy),
                description = stringResource(R.string.termux_step_copy_body),
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(start = 38.dp, top = 10.dp, bottom = 16.dp),
            ) {
                Text(
                    text = TermuxContract.ENABLE_EXTERNAL_APPS_COMMAND,
                    color = Color(0xFFF2F5F3),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(13.dp).testTag("termux-access-command"),
                )
            }
            GuideStep(
                number = 2,
                title = stringResource(R.string.termux_step_paste),
                description = stringResource(R.string.termux_step_paste_body),
            )
            Spacer(Modifier.height(14.dp))
            GuideStep(
                number = 3,
                title = stringResource(R.string.termux_step_return),
                description = stringResource(R.string.termux_step_return_body),
            )
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SetupChecklist(stage: SetupStage) {
    val activeIndex = when (stage) {
        SetupStage.TERMUX_MISSING -> 0
        SetupStage.RUN_COMMAND_PERMISSION_MISSING,
        SetupStage.TERMUX_BRIDGE_DISABLED,
        SetupStage.TERMUX_BRIDGE_FAILED,
        -> 1
        SetupStage.BOOTSTRAP_REQUIRED,
        SetupStage.BOOTSTRAPPING,
        -> 2
        SetupStage.CODEX_LOGIN_REQUIRED -> 3
        SetupStage.LOCAL_AI_REQUIRED,
        SetupStage.LOCAL_AI_PREPARING,
        SetupStage.LOCAL_AI_FAILED -> 3
        SetupStage.CHECKING -> -1
        SetupStage.READY -> 4
    }
    val labels = if (stage == SetupStage.LOCAL_AI_REQUIRED || stage == SetupStage.LOCAL_AI_PREPARING || stage == SetupStage.LOCAL_AI_FAILED)
        listOf("Termux", stringResource(R.string.setup_step_secure), stringResource(R.string.setup_step_host_ai), stringResource(R.string.setup_step_model))
    else listOf("Termux", stringResource(R.string.setup_step_secure), stringResource(R.string.setup_step_host_codex), stringResource(R.string.setup_step_login))
    Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            labels.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    index < activeIndex -> MaterialTheme.colorScheme.primary
                                    index == activeIndex -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (index < activeIndex) "✓" else "${index + 1}",
                            color = when {
                                index < activeIndex -> MaterialTheme.colorScheme.onPrimary
                                index == activeIndex -> MaterialTheme.colorScheme.onSecondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontWeight = if (index == activeIndex) FontWeight.SemiBold else FontWeight.Normal)
                }
                if (index < labels.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("primary-action"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(label)
    }
}

private enum class WorkspaceScreen {
    HOME, AUTOMATIONS, PHONE_AUTOMATION, PROFILE, CREATE_AGENT_TASK, CHAT, CONVERSATIONS, FILES, NAME, ROLE, SKILLS, SKILL_WORKSHOP
}

@Composable
private fun ReadyAppScreen(
    workspace: WorkspaceUiState,
    graphicTheme: GraphicTheme,
    onGraphicThemeChange: (GraphicTheme) -> Unit,
    skippedOnboardingStops: Set<String>,
    onSkipOnboardingStop: (String) -> Unit,
    onRefresh: () -> Unit,
    phoneAutomationEnabled: Boolean,
    phoneAutomationConnected: Boolean,
    phoneAutomationOnboardingSkipped: Boolean,
    onOpenPhoneAutomationSettings: () -> Unit,
    onCheckPhoneAutomation: () -> Unit,
    onSkipPhoneAutomationOnboarding: () -> Unit,
    onSaveOwnerProfile: (String) -> Unit,
    onCreateAgentTask: (CreateAgentTaskDraft) -> Unit,
    onSelectAgent: (agentId: String, newConversation: Boolean) -> Unit,
    onOpenConversation: (agentId: String, conversationId: String, title: String) -> Unit,
    onLoadAgentConversations: (agentId: String, loadMore: Boolean) -> Unit,
    onSendTask: (agentId: String, prompt: String) -> Unit,
    onSaveAgentAppearance: (String, String, Int) -> Unit,
    onSaveAgentRole: (agentId: String, roleDescription: String) -> Unit,
    onSaveAgentSkills: (agentId: String, skillIds: List<String>) -> Unit,
    onSetAutomationEnabled: (automationId: String, enabled: Boolean) -> Unit,
    onDeleteAgent: (String) -> Unit,
) {
    val missionPrompt = stringResource(R.string.mission_prompt)
    var agentSection by rememberSaveable { mutableStateOf(AgentSection.CONVERSATIONS) }
    var screen by rememberSaveable {
        mutableStateOf(
            if (phoneAutomationOnboardingSkipped) {
                WorkspaceScreen.HOME
            } else {
                WorkspaceScreen.PHONE_AUTOMATION
            },
        )
    }
    var selectedAgentId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedAgent = workspace.agents.firstOrNull { it.id == selectedAgentId }
    var handledCreatedAgentId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(workspace.createdAgentId, workspace.agents) {
        val createdAgentId = workspace.createdAgentId
        if (createdAgentId != null && createdAgentId != handledCreatedAgentId) {
            workspace.agents.firstOrNull { it.id == createdAgentId }?.let { agent ->
                selectedAgentId = agent.id
                handledCreatedAgentId = createdAgentId
                screen = WorkspaceScreen.CHAT
            }
        }
    }
    var fromTrail by rememberSaveable { mutableStateOf(false) }
    var skillsReturnScreen by rememberSaveable { mutableStateOf(WorkspaceScreen.CONVERSATIONS) }
    var handledPhoneReturnRunId by rememberSaveable { mutableStateOf<String?>(null) }
    val phoneTaskReturn = workspace.phoneTaskReturn
    LaunchedEffect(phoneTaskReturn, workspace.agents) {
        if (phoneTaskReturn != null && phoneTaskReturn.runId != handledPhoneReturnRunId) {
            workspace.agents.firstOrNull { it.id == phoneTaskReturn.agentId }?.let { agent ->
                selectedAgentId = agent.id
                fromTrail = false
                screen = WorkspaceScreen.CHAT
                handledPhoneReturnRunId = phoneTaskReturn.runId
            }
        }
    }
    var missionDraft by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = screen != WorkspaceScreen.HOME) {
        screen = when (screen) {
            WorkspaceScreen.PHONE_AUTOMATION -> {
                onSkipPhoneAutomationOnboarding()
                WorkspaceScreen.HOME
            }
            WorkspaceScreen.CONVERSATIONS -> WorkspaceScreen.CHAT
            WorkspaceScreen.AUTOMATIONS -> WorkspaceScreen.HOME
            WorkspaceScreen.NAME -> WorkspaceScreen.CONVERSATIONS
            WorkspaceScreen.ROLE -> WorkspaceScreen.CONVERSATIONS
            WorkspaceScreen.FILES -> WorkspaceScreen.CONVERSATIONS
            WorkspaceScreen.SKILL_WORKSHOP -> WorkspaceScreen.SKILLS
            WorkspaceScreen.SKILLS -> skillsReturnScreen
            else -> if (fromTrail) WorkspaceScreen.PHONE_AUTOMATION else WorkspaceScreen.HOME
        }
    }
    AnimatedContent(targetState = screen, label = "workspace-navigation") { current ->
        when (current) {
            WorkspaceScreen.HOME -> HomeScreen(
                agents = workspace.agents,
                tasks = workspace.recentCompletedTasks,
                automations = workspace.automations,
                deletingAgentId = workspace.deletingAgentId,
                deleteAgentError = workspace.deleteAgentError,
                onDeleteAgent = onDeleteAgent,
                onOpenAgentSection = { agent, section ->
                    selectedAgentId = agent.id
                    agentSection = section
                    onSelectAgent(agent.id, false)
                    screen = WorkspaceScreen.CONVERSATIONS
                },
                phoneAutomationEnabled = phoneAutomationEnabled,
                onEnablePhoneAutomation = onOpenPhoneAutomationSettings,
                onOpenPhoneAutomation = { fromTrail = false; screen = WorkspaceScreen.PHONE_AUTOMATION },
                onOpenOwnerProfile = { screen = WorkspaceScreen.PROFILE },
                onOpenAutomations = { screen = WorkspaceScreen.AUTOMATIONS },
                onCreateAgent = { screen = WorkspaceScreen.CREATE_AGENT_TASK },
                onOpenAgent = { agent ->
                    selectedAgentId = agent.id
                    onSelectAgent(agent.id, true)
                    screen = WorkspaceScreen.CHAT
                },
                onOpenTask = { task ->
                    selectedAgentId = task.agent.id
                    onOpenConversation(task.agent.id, task.conversationId, task.title)
                    screen = WorkspaceScreen.CHAT
                },
            )
            WorkspaceScreen.AUTOMATIONS -> AutomationListScreen(
                automations = workspace.automations,
                automationSavingIds = workspace.automationSavingIds,
                automationErrors = workspace.automationErrors,
                onBackHome = { screen = WorkspaceScreen.HOME },
                onOpenConversation = { automation ->
                    selectedAgentId = automation.agent.id
                    onOpenConversation(automation.agent.id, automation.conversationId, automation.name)
                    screen = WorkspaceScreen.CHAT
                },
                onSetAutomationEnabled = onSetAutomationEnabled,
            )
            WorkspaceScreen.PHONE_AUTOMATION -> OnboardingTrailScreen(
                baseReady = true,
                teamReady = workspace.snapshotLoaded && workspace.agents.isNotEmpty(),
                skillsReady = workspace.snapshotLoaded && workspace.agents.any { it.assignedSkillIds.isNotEmpty() },
                phoneEnabled = phoneAutomationEnabled,
                phoneConnected = phoneAutomationConnected,
                missionReady = workspace.recentCompletedTasks.isNotEmpty(),
                skippedStops = skippedOnboardingStops,
                onBaseAction = onRefresh,
                onTeamAction = {
                    fromTrail = true
                    val agent = workspace.agents.firstOrNull()
                    selectedAgentId = agent?.id
                    agent?.let { onSelectAgent(it.id, false) }
                    screen = if (agent != null) WorkspaceScreen.CHAT else WorkspaceScreen.CREATE_AGENT_TASK
                },
                onSkillsAction = {
                    fromTrail = true
                    skillsReturnScreen = WorkspaceScreen.PHONE_AUTOMATION
                    val agent = workspace.agents.firstOrNull { it.id == "starter-nova" } ?: workspace.agents.firstOrNull()
                    selectedAgentId = agent?.id
                    screen = if (agent != null) WorkspaceScreen.SKILLS else WorkspaceScreen.CREATE_AGENT_TASK
                },
                onPhoneSettings = onOpenPhoneAutomationSettings,
                onPhoneCheck = onCheckPhoneAutomation,
                onMissionAction = {
                    fromTrail = true
                    val agent = workspace.agents.firstOrNull { it.id == "starter-echo" } ?: workspace.agents.firstOrNull()
                    selectedAgentId = agent?.id
                    agent?.let { onSelectAgent(it.id, true) }
                    missionDraft = missionPrompt
                    screen = if (agent != null) WorkspaceScreen.CHAT else WorkspaceScreen.CREATE_AGENT_TASK
                },
                onSkipStop = onSkipOnboardingStop,
                onContinue = {
                    onSkipPhoneAutomationOnboarding()
                    fromTrail = false
                    screen = WorkspaceScreen.HOME
                },
            )
            WorkspaceScreen.PROFILE -> OwnerProfileScreen(
                ownerProfile = workspace.ownerProfile,
                graphicTheme = graphicTheme,
                onGraphicThemeChange = onGraphicThemeChange,
                saving = workspace.ownerProfileSaving,
                saved = workspace.ownerProfileSaved,
                error = workspace.ownerProfileError,
                onBack = { screen = WorkspaceScreen.HOME },
                onSave = onSaveOwnerProfile,
            )
            WorkspaceScreen.CREATE_AGENT_TASK -> CreateAgentTaskScreen(
                creating = workspace.agentTaskCreating,
                error = workspace.agentTaskError,
                graphicTheme = graphicTheme,
                onBack = { screen = WorkspaceScreen.HOME },
                onSubmit = onCreateAgentTask,
            )
            WorkspaceScreen.CHAT -> ChatScreen(
                initialMessage = if (fromTrail) missionDraft else "",
                agent = selectedAgent,
                agents = workspace.agents,
                chat = selectedAgent?.let { workspace.chatsByAgent[it.id] } ?: AgentChatUi(),
                phoneAutomationEnabled = phoneAutomationEnabled,
                onEnablePhoneAutomation = onOpenPhoneAutomationSettings,
                onSelectAgent = { agent ->
                    selectedAgentId = agent.id
                    onSelectAgent(agent.id, false)
                },
                onAddAgent = { screen = WorkspaceScreen.CREATE_AGENT_TASK },
                onShowConversations = { agentSection = AgentSection.CONVERSATIONS; screen = WorkspaceScreen.CONVERSATIONS },
                onOpenSkills = { onRefresh(); skillsReturnScreen = WorkspaceScreen.CHAT; screen = WorkspaceScreen.SKILLS },
                onBackHome = { screen = if (fromTrail) WorkspaceScreen.PHONE_AUTOMATION else WorkspaceScreen.HOME },
                onSendTask = { id, prompt -> missionDraft = ""; onSendTask(id, prompt) },
            )
            WorkspaceScreen.CONVERSATIONS -> ConversationListScreen(
                initialSection = agentSection,
                automations = workspace.automations.filter { it.agent.id == selectedAgent?.id },
                automationSavingIds = workspace.automationSavingIds,
                automationErrors = workspace.automationErrors,
                onSetAutomationEnabled = onSetAutomationEnabled,
                onOpenAutomation = { automation ->
                    onOpenConversation(automation.agent.id, automation.conversationId, automation.name)
                    screen = WorkspaceScreen.CHAT
                },
                agents = workspace.agents,
                selectedAgent = selectedAgent,
                conversationPage = selectedAgent?.let { workspace.conversationPagesByAgent[it.id] }
                    ?: AgentConversationsUi(),
                onBackToChat = { screen = WorkspaceScreen.CHAT },
                onBackHome = { screen = WorkspaceScreen.HOME },
                onSelectAgent = { agent ->
                    selectedAgentId = agent.id
                    onSelectAgent(agent.id, false)
                },
                onAddAgent = { screen = WorkspaceScreen.CREATE_AGENT_TASK },
                onNewConversation = { agent ->
                    selectedAgentId = agent.id
                    onSelectAgent(agent.id, true)
                    screen = WorkspaceScreen.CHAT
                },
                onOpenConversation = { conversation ->
                    selectedAgentId = conversation.agent.id
                    onOpenConversation(conversation.agent.id, conversation.id, conversation.title)
                    screen = WorkspaceScreen.CHAT
                },
                onLoadConversations = onLoadAgentConversations,
                onOpenFiles = { screen = WorkspaceScreen.FILES },
                onOpenName = { screen = WorkspaceScreen.NAME },
                onOpenRole = { screen = WorkspaceScreen.ROLE },
                onOpenSkills = { onRefresh(); skillsReturnScreen = WorkspaceScreen.CONVERSATIONS; screen = WorkspaceScreen.SKILLS },
            )
            WorkspaceScreen.FILES -> selectedAgent?.let { agent ->
                AgentFilesScreen(agent, onBack = { screen = WorkspaceScreen.CONVERSATIONS })
            }
            WorkspaceScreen.NAME -> RenameAgentScreen(
                agent = selectedAgent,
                saving = workspace.nameSavingAgentId == selectedAgent?.id,
                saved = workspace.nameSavedAgentId == selectedAgent?.id,
                error = workspace.nameError,
                onBack = { screen = WorkspaceScreen.CONVERSATIONS },
                onSave = { name, avatar -> selectedAgent?.let { onSaveAgentAppearance(it.id, name, avatar) } },
            )
            WorkspaceScreen.ROLE -> AgentRoleScreen(
                agent = selectedAgent,
                saving = workspace.roleSavingAgentId == selectedAgent?.id,
                saved = workspace.roleSavedAgentId == selectedAgent?.id,
                error = workspace.roleError,
                onBack = { screen = WorkspaceScreen.CONVERSATIONS },
                onSave = { role -> selectedAgent?.let { onSaveAgentRole(it.id, role) } },
            )
            WorkspaceScreen.SKILL_WORKSHOP -> selectedAgent?.let { agent ->
                SkillWorkshopScreen(agent.id, onBack = { onRefresh(); screen = WorkspaceScreen.SKILLS })
            }
            WorkspaceScreen.SKILLS -> SkillsScreen(
                agent = selectedAgent,
                definitions = workspace.skillDefinitions,
                saving = workspace.skillsSavingAgentId == selectedAgent?.id,
                saved = workspace.skillsSavedAgentId == selectedAgent?.id,
                error = workspace.skillsError,
                onBack = { screen = skillsReturnScreen },
                onCreateSkill = { screen = WorkspaceScreen.SKILL_WORKSHOP },
                onSave = { skillIds -> selectedAgent?.let { onSaveAgentSkills(it.id, skillIds) } },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    agents: List<AgentSummaryUi>,
    tasks: List<RecentTaskUi>,
    automations: List<AutomationUi>,
    deletingAgentId: String?,
    deleteAgentError: String?,
    onDeleteAgent: (String) -> Unit,
    onOpenAgentSection: (AgentSummaryUi, AgentSection) -> Unit,
    phoneAutomationEnabled: Boolean,
    onEnablePhoneAutomation: () -> Unit,
    onOpenPhoneAutomation: () -> Unit,
    onOpenOwnerProfile: () -> Unit,
    onOpenAutomations: () -> Unit,
    onCreateAgent: () -> Unit,
    onOpenAgent: (AgentSummaryUi) -> Unit,
    onOpenTask: (RecentTaskUi) -> Unit,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    var order by remember { mutableStateOf(VisualPreferences.agentOrder(context)) }
    var hidden by remember { mutableStateOf(VisualPreferences.hiddenAgents(context)) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleteTarget = agents.firstOrNull { it.id == deleteTargetId }
    if (deleteTarget != null) AlertDialog(
        onDismissRequest = { if (deletingAgentId == null) deleteTargetId = null },
        title = { Text(stringResource(R.string.agent_delete_confirm, deleteTarget.name)) },
        text = { Column { Text(stringResource(R.string.agent_delete_body))
            deleteAgentError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = { onDeleteAgent(deleteTarget.id) }, enabled = deletingAgentId == null) {
            Text(if (deletingAgentId == deleteTarget.id) stringResource(R.string.deleting) else stringResource(R.string.agent_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteTargetId = null }, enabled = deletingAgentId == null) { Text(stringResource(R.string.cancel)) } },
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("home-screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onOpenOwnerProfile,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("owner-profile-open"),
            ) {
                Text(stringResource(R.string.profile))
            }
            Box {
                IconButton(
                    onClick = { settingsOpen = true },
                    modifier = Modifier
                        .testTag("home-settings-open")
                        .let { val label = stringResource(R.string.settings); it.semantics { contentDescription = label } },
                ) {
                    SettingsGearIcon()
                }
                DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.advanced)) },
                        onClick = {
                            settingsOpen = false
                            onOpenPhoneAutomation()
                        },
                        modifier = Modifier.testTag("home-settings-advanced"),
                    )
                }
            }
        }
        if (!phoneAutomationEnabled) {
            Spacer(Modifier.height(18.dp))
            PhoneAccessRequiredBanner(onEnable = onEnablePhoneAutomation)
            Spacer(Modifier.height(26.dp))
        } else {
            Spacer(Modifier.height(34.dp))
        }
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        HomeAutomationsLink(onClick = onOpenAutomations)

        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.your_agents), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { showHidden = !showHidden }, modifier = Modifier.testTag("toggle-hidden-agents")) {
                AgentVisibilityIcon(hidden = !showHidden,
                    description = if (showHidden) stringResource(R.string.hidden_agents_hide) else stringResource(R.string.hidden_agents_show))
            }
            TextButton(onClick = onCreateAgent) { Text(stringResource(R.string.new_agent_plus)) }
        }
        Spacer(Modifier.height(12.dp))
        localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        AgentHeroGrid(
            agents = agents.filter { showHidden || it.id !in hidden }
                .sortedBy { order.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } },
            hiddenAgentIds = hidden,
            automations = automations,
            onOpenAgent = onOpenAgent,
            onOpenSection = onOpenAgentSection,
            onAction = { agent, action -> when (action) {
                AgentCardAction.SETTINGS -> onOpenAgentSection(agent, AgentSection.SETTINGS)
                AgentCardAction.DELETE -> deleteTargetId = agent.id
                AgentCardAction.HIDE -> {
                    val next = if (agent.id in hidden) hidden - agent.id else hidden + agent.id
                    if (VisualPreferences.saveHiddenAgents(context, next)) { hidden = next; localError = null }
                    else localError = resources.getString(R.string.agents_visibility_save_failed)
                }
            } },
            onReorder = { ids ->
                val next = ids + order.filter { it !in ids }
                if (VisualPreferences.saveAgentOrder(context, next)) { order = next; localError = null }
                else localError = resources.getString(R.string.agents_order_save_failed)
            },
        )

        if (tasks.isNotEmpty()) {
            var groupCompletedByAgent by rememberSaveable { mutableStateOf(false) }
            var collapsedAgentIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
            Spacer(Modifier.height(34.dp))
            SectionHeader(title = stringResource(R.string.recently_completed))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompletedOrderChip(
                    label = stringResource(R.string.newest_first),
                    selected = !groupCompletedByAgent,
                    onClick = { groupCompletedByAgent = false },
                    modifier = Modifier.weight(1f).testTag("completed-order-time"),
                )
                CompletedOrderChip(
                    label = stringResource(R.string.by_agent),
                    selected = groupCompletedByAgent,
                    onClick = { groupCompletedByAgent = true },
                    modifier = Modifier.weight(1f).testTag("completed-order-agent"),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (groupCompletedByAgent) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    tasks.groupBy { it.agent.id }.values.forEach { agentTasks ->
                        val agentId = agentTasks.first().agent.id
                        CompletedAgentGroup(
                            agent = agentTasks.first().agent,
                            tasks = agentTasks,
                            expanded = agentId !in collapsedAgentIds,
                            onToggleExpanded = {
                                collapsedAgentIds = ArrayList(
                                    if (agentId in collapsedAgentIds) {
                                        collapsedAgentIds - agentId
                                    } else {
                                        collapsedAgentIds + agentId
                                    },
                                )
                            },
                            onOpenTask = onOpenTask,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    tasks.forEach { task ->
                        RecentTaskRow(task = task, showAgent = true, onClick = { onOpenTask(task) })
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PhoneAccessRequiredBanner(onEnable: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.32f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("phone-access-disabled"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "!",
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.phone_control_off),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.phone_control_off_body),
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = onEnable,
                modifier = Modifier.testTag("phone-access-enable"),
            ) {
                Text(stringResource(R.string.turn_on), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeAutomationsLink(onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .testTag("home-automations-open"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            AutomationNavIcon()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.automations),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 26.sp)
        }
    }
}

@Composable
internal fun AutomationNavIcon(iconSize: Dp = 46.dp) {
    val iconColor = MaterialTheme.colorScheme.primary
    val medalColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f).compositeOver(MaterialTheme.colorScheme.surface)
    Surface(
        shape = CircleShape,
        color = medalColor,
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.22f)),
        modifier = Modifier.size(iconSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(iconSize * (25f / 46f))) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = iconColor,
                    startAngle = -58f,
                    sweepAngle = 295f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.1f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.8f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawLine(
                    color = iconColor,
                    start = center,
                    end = center.copy(y = size.height * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconColor,
                    start = center,
                    end = center.copy(x = size.width * 0.69f, y = size.height * 0.57f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun AutomationListScreen(
    automations: List<AutomationUi>,
    automationSavingIds: Set<String>,
    automationErrors: Map<String, String>,
    onBackHome: () -> Unit,
    onOpenConversation: (AutomationUi) -> Unit,
    onSetAutomationEnabled: (automationId: String, enabled: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("automations-screen"),
    ) {
        Text(
            stringResource(R.string.back_home),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClick = onBackHome)
                .padding(vertical = 12.dp)
                .testTag("automations-back-home"),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.automations),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        if (automations.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.no_automations),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                automations.forEach { automation ->
                    AutomationRow(
                        automation = automation,
                        saving = automation.id in automationSavingIds,
                        error = automationErrors[automation.id],
                        onEnabledChange = { enabled -> onSetAutomationEnabled(automation.id, enabled) },
                        onClick = { onOpenConversation(automation) },
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun OwnerProfileScreen(
    ownerProfile: String,
    graphicTheme: GraphicTheme,
    onGraphicThemeChange: (GraphicTheme) -> Unit,
    saving: Boolean,
    saved: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var about by remember(ownerProfile) { mutableStateOf(ownerProfile) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("owner-profile-screen"),
    ) {
        Text(
            stringResource(R.string.back_home),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.owner_profile), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.owner_profile_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.app_style), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.app_style_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        GraphicTheme.entries.chunked(2).forEach { rowThemes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowThemes.forEach { theme ->
                    GraphicThemeCard(
                        theme = theme,
                        selected = theme == graphicTheme,
                        onClick = { onGraphicThemeChange(theme) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.about_you), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = about,
            onValueChange = { if (it.length <= 2_000) about = it },
            label = { Text(stringResource(R.string.about_you_label)) },
            placeholder = {
                Text(stringResource(R.string.about_you_hint))
            },
            minLines = 8,
            maxLines = 14,
            modifier = Modifier.fillMaxWidth().testTag("owner-profile-text"),
        )
        Spacer(Modifier.height(6.dp))
        Text("${about.length}/2000", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onSave(about) },
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("owner-profile-save"),
        ) {
            Text(if (saving) stringResource(R.string.saving) else stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
        }
        if (saved) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.owner_profile_saved),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        error?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun GraphicThemeCard(
    theme: GraphicTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MobileBotTheme(graphicTheme = theme) {
    Surface(
        modifier = modifier
            .clickable(role = Role.RadioButton, onClick = onClick)
            .testTag("graphic-theme-${theme.storedId}"),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                (1..3).forEach { avatarIndex ->
                    Image(
                        painter = painterResource(themeAvatarResource(theme, avatarIndex)),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(38.dp).clip(CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(theme.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                theme.summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
            )
            if (selected) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.selected_check), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
}

@Composable
private fun AutomationRow(
    automation: AutomationUi,
    saving: Boolean,
    error: String?,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    var detailsVisible by rememberSaveable(automation.id) { mutableStateOf(false) }
    val hasResult = !automation.lastSummary.isNullOrBlank()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
            .testTag("automation-${automation.id}"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = automation.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AgentAvatar(automation.agent, 28)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            automation.agent.name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (automation.deferredReasonLabel != null ||
                        automation.waitingDurationLabel != null ||
                        automation.statusLabel == stringResource(R.string.automation_status_running)
                    ) {
                        Spacer(Modifier.height(5.dp))
                        AutomationStatusLine(automation)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RepeatScheduleIcon(MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            stringResource(R.string.automation_schedule, automation.intervalMinutes, automation.nextRunAtLabel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (saving) {
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.saving_change),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("automation-toggle-error-${automation.id}"),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = automation.enabled,
                        onCheckedChange = onEnabledChange,
                        enabled = !saving,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.secondary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                            disabledCheckedThumbColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.62f),
                            disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                            disabledCheckedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                            disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                            disabledUncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            disabledUncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                        ),
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("automation-switch-${automation.id}")
                            .let {
                                val label = stringResource(
                                    if (automation.enabled) R.string.automation_turn_off else R.string.automation_turn_on,
                                    automation.name,
                                )
                                it.semantics { contentDescription = label }
                            },
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { detailsVisible = true },
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("automation-details-open-${automation.id}"),
                    ) {
                        Text(
                            if (hasResult) stringResource(R.string.see_result) else stringResource(R.string.details),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (detailsVisible) {
        AutomationDetailsSheet(
            automation = automation,
            onDismiss = { detailsVisible = false },
            onOpenConversation = onClick,
        )
    }
}

@Composable
private fun AutomationStatusLine(automation: AutomationUi) {
    val hasProblem = automation.deferredReasonLabel != null || automation.waitingDurationLabel != null
    val contentColor = when {
        !automation.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        hasProblem -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (hasProblem) Modifier.testTag("automation-waiting-${automation.id}") else Modifier,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(contentColor))
        Spacer(Modifier.width(7.dp))
        Text(
            automation.statusLabel,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AutomationDetailsSheet(
    automation: AutomationUi,
    onDismiss: () -> Unit,
    onOpenConversation: () -> Unit,
) {
    var historyExpanded by rememberSaveable(automation.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("automation-details-${automation.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                automation.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            AutomationStatusLine(automation)
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.last_result),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    automation.lastSummary?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.automation_no_result),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        onOpenConversation()
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("automation-open-conversation-${automation.id}"),
            ) {
                Text(stringResource(R.string.open_conversation), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(
                onClick = { historyExpanded = !historyExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("automation-history-${automation.id}"),
            ) {
                Text(
                    stringResource(R.string.run_history),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                SheetChevronIcon(
                    expanded = historyExpanded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = historyExpanded) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        AutomationHistoryRow(stringResource(R.string.scheduled_time), automation.scheduledAtLabel ?: stringResource(R.string.no_data))
                        AutomationHistoryRow(stringResource(R.string.actual_start), automation.startedAtLabel ?: stringResource(R.string.no_data))
                        AutomationHistoryRow(stringResource(R.string.delay), automation.delayLabel ?: stringResource(R.string.none))
                        AutomationHistoryRow(
                            stringResource(R.string.skipped_runs),
                            automation.skippedOccurrences.toString(),
                            Modifier.testTag("automation-skipped-${automation.id}"),
                        )
                        automation.waitingDurationLabel?.let {
                            AutomationHistoryRow(stringResource(R.string.current_wait), it)
                        }
                        automation.deferredReasonLabel?.let {
                            AutomationHistoryRow(stringResource(R.string.wait_reason), it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationHistoryRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RepeatScheduleIcon(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        drawCircle(color = color, radius = size.minDimension * 0.42f, style = Stroke(stroke))
        drawLine(
            color,
            start = center,
            end = center.copy(y = size.height * 0.27f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            start = center,
            end = center.copy(x = size.width * 0.68f, y = size.height * 0.58f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SheetChevronIcon(expanded: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        val upperY = if (expanded) size.height * 0.62f else size.height * 0.38f
        val lowerY = if (expanded) size.height * 0.38f else size.height * 0.62f
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width * 0.24f, upperY), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, lowerY), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, lowerY), end = androidx.compose.ui.geometry.Offset(size.width * 0.76f, upperY), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun SettingsGearIcon() {
    val iconColor = MaterialTheme.colorScheme.primary
    val medalColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f).compositeOver(MaterialTheme.colorScheme.surface)
    Surface(
        shape = CircleShape,
        color = medalColor,
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.22f)),
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(21.dp)) {
                val gear = Path()
                repeat(32) { point ->
                    val angle = Math.toRadians((point * 360.0 / 32.0) - 90.0)
                    val radius = if (point % 4 == 1 || point % 4 == 2) {
                        size.minDimension * 0.47f
                    } else {
                        size.minDimension * 0.34f
                    }
                    val x = center.x + kotlin.math.cos(angle).toFloat() * radius
                    val y = center.y + kotlin.math.sin(angle).toFloat() * radius
                    if (point == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
                }
                gear.close()
                drawPath(gear, color = iconColor)
                drawCircle(color = medalColor, radius = size.minDimension * 0.13f)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        action?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction),
            )
        }
    }
}

@Composable
private fun RecentConversationRow(conversation: RecentConversationUi, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("recent-conversation-${conversation.id}"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    conversation.updatedAtLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                conversation.lastMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompletedOrderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        ),
        modifier = modifier
            .height(48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompletedAgentGroup(
    agent: AgentSummaryUi,
    tasks: List<RecentTaskUi>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenTask: (RecentTaskUi) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("completed-agent-${agent.id}")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) stringResource(R.string.agent_tasks_collapse) else stringResource(R.string.agent_tasks_expand),
                    onClick = onToggleExpanded,
                )
                .let {
                    val label = stringResource(
                        if (expanded) R.string.agent_tasks_collapse_named else R.string.agent_tasks_expand_named,
                        agent.name,
                    )
                    it.semantics { contentDescription = label }
                }
                .padding(horizontal = 4.dp, vertical = 5.dp),
        ) {
            AgentAvatar(agent, 38)
            Spacer(Modifier.width(10.dp))
            Text(
                agent.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            AgentGroupToggleIcon(expanded)
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(9.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tasks.forEach { task ->
                        RecentTaskRow(task = task, showAgent = false, onClick = { onOpenTask(task) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentGroupToggleIcon(expanded: Boolean) {
    val iconColor = MaterialTheme.colorScheme.primary
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "agent-group-chevron",
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.22f)),
        modifier = Modifier.size(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(17.dp).rotate(rotation)) {
                val stroke = 1.8.dp.toPx()
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.38f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.64f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.64f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.38f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun RecentTaskRow(task: RecentTaskUi, showAgent: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("recent-task-${task.id}"),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            if (showAgent) {
                AgentAvatar(task.agent, 44)
                Spacer(Modifier.width(13.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showAgent) {
                        Text(task.agent.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        task.completedAtLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(task.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(
                    task.resultSummary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
        }
    }
}

@Composable
private fun CreateAgentTaskScreen(
    creating: Boolean,
    error: String?,
    graphicTheme: GraphicTheme,
    onBack: () -> Unit,
    onSubmit: (CreateAgentTaskDraft) -> Unit,
) {
    var agentName by remember { mutableStateOf("") }
    var roleDescription by remember { mutableStateOf("") }
    var taskPrompt by remember { mutableStateOf("") }
    var avatarIndex by rememberSaveable { mutableStateOf(1) }
    val canSubmit = agentName.isNotBlank() && roleDescription.length <= 8_000

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("create-agent-task-screen"),
    ) {
        Text(
            stringResource(R.string.back),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onBack),
        )
        Spacer(Modifier.height(34.dp))
        Text(stringResource(R.string.new_agent), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.new_agent_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 23.sp,
        )
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.agent), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = agentName,
            onValueChange = { agentName = it },
            enabled = !creating,
            placeholder = { Text(stringResource(R.string.agent_name_example)) },
            supportingText = { Text(stringResource(R.string.agent_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("agent-name-field"),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.choose_look), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.choose_look_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        (1..15).chunked(5).forEach { rowAvatars ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowAvatars.forEach { candidate ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.RadioButton) { avatarIndex = candidate }
                            .testTag("avatar-choice-$candidate"),
                        shape = MaterialTheme.shapes.medium,
                        color = if (candidate == avatarIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            if (candidate == avatarIndex) 2.dp else 1.dp,
                            if (candidate == avatarIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Image(
                            painter = painterResource(themeAvatarResource(graphicTheme, candidate)),
                            contentDescription = stringResource(R.string.avatar_n, candidate),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(60.dp).padding(4.dp).clip(MaterialTheme.shapes.small),
                        )
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(11.dp))
        Text(stringResource(R.string.agent_role_optional), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = roleDescription,
            onValueChange = { if (it.length <= 8_000) roleDescription = it },
            enabled = !creating,
            placeholder = { Text(stringResource(R.string.agent_role_hint)) },
            supportingText = { Text("${roleDescription.length}/8000") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth().testTag("agent-role-field-create"),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.first_task_optional), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = taskPrompt,
            onValueChange = { taskPrompt = it },
            enabled = !creating,
            placeholder = { Text(stringResource(R.string.first_task_placeholder)) },
            supportingText = { Text(stringResource(R.string.first_task_hint)) },
            minLines = 5,
            maxLines = 9,
            modifier = Modifier.fillMaxWidth().testTag("task-prompt-field"),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = {
                onSubmit(
                    CreateAgentTaskDraft(
                        agentName = agentName.trim(),
                        taskPrompt = taskPrompt.trim(),
                        roleDescription = roleDescription.trim(),
                        avatarIndex = avatarIndex,
                    ),
                )
            },
            enabled = canSubmit && !creating,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("submit-agent-task"),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            if (creating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.creating_agent), fontWeight = FontWeight.SemiBold)
            } else {
                Text(
                    if (taskPrompt.isBlank()) stringResource(R.string.create_agent) else stringResource(R.string.create_and_send),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("create-agent-task-error"),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.new_agent_footer),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun ChatHeaderIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String,
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit,
) {
    val iconColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val medalColor = if (enabled) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Surface(
            shape = CircleShape,
            color = medalColor,
            border = BorderStroke(1.dp, iconColor.copy(alpha = if (enabled) 0.22f else 0.12f)),
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { icon(iconColor) }
        }
    }
}

@Composable
internal fun ConversationBubblesIcon(size: Dp = 38.dp, adaptToSurface: Boolean = false) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val filter = if (adaptToSurface && LocalGraphicTheme.current == GraphicTheme.HEROES) {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, ink.red * 255f,
            0f, 0f, 0f, 0f, ink.green * 255f,
            0f, 0f, 0f, 0f, ink.blue * 255f,
            -0.8504f, -2.8608f, -0.2888f, 0f, 765f,
        )))
    } else null
    Image(
        painterResource(themeConversationsResource(LocalGraphicTheme.current)),
        contentDescription = null,
        modifier = Modifier.size(size),
        colorFilter = filter,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ChatScreen(
    initialMessage: String = "",
    agent: AgentSummaryUi?,
    agents: List<AgentSummaryUi>,
    chat: AgentChatUi,
    phoneAutomationEnabled: Boolean,
    onEnablePhoneAutomation: () -> Unit,
    onSelectAgent: (AgentSummaryUi) -> Unit,
    onAddAgent: () -> Unit,
    onShowConversations: () -> Unit,
    onOpenSkills: () -> Unit,
    onBackHome: () -> Unit,
    onSendTask: (agentId: String, prompt: String) -> Unit,
) {
    var textSizeStep by rememberSaveable { mutableStateOf(0) }
    val agentName = agent?.name ?: stringResource(R.string.agent)
    var message by rememberSaveable(agent?.id) { mutableStateOf(initialMessage) }
    val listState = rememberLazyListState()
    LaunchedEffect(chat.messages.size, chat.isRunning) {
        val lastItem = chat.messages.size + if (chat.isRunning || chat.errorMessage != null) 1 else 0
        if (lastItem > 0) listState.animateScrollToItem(lastItem - 1)
    }
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        AgentTopBar(
            agents = agents,
            selectedAgentId = agent?.id,
            onSelectAgent = onSelectAgent,
            onAddAgent = onAddAgent,
            onBackHome = onBackHome,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatHeaderIconButton(
                onClick = onShowConversations,
                contentDescription = stringResource(R.string.conversations),
                testTag = "conversation-list-toggle",
            ) { _ -> ConversationBubblesIcon() }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chat.title.ifBlank { stringResource(R.string.new_conversation) },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("conversation-title"),
                )
                Text(
                    agent?.subtitle.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    chat.errorMessage != null -> Text(
                        chat.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    chat.isRunning -> Text(
                        stringResource(R.string.working),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            ChatHeaderIconButton(
                onClick = onOpenSkills,
                enabled = agent != null,
                contentDescription = stringResource(R.string.powers),
                testTag = "chat-agent-skills",
            ) {
                Image(
                    painter = painterResource(themePowersResource(LocalGraphicTheme.current)),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (!phoneAutomationEnabled) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                PhoneAccessRequiredBanner(onEnable = onEnablePhoneAutomation)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().chatTextZoom(textSizeStep) { textSizeStep = it }.testTag("chat-stream"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (chat.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (chat.messages.isEmpty()) {
                item {
                    val starters = agent?.conversationStarters.orEmpty().take(4)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = if (starters.isEmpty()) 52.dp else 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (agent != null) AgentAvatar(agent, 68)
                        Spacer(Modifier.height(18.dp))
                        Text(stringResource(R.string.agent_new_conversation_with, agentName), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        if (starters.isEmpty()) {
                            Text(
                                stringResource(R.string.chat_empty_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 21.sp,
                            )
                        } else {
                            Spacer(Modifier.height(20.dp))
                            ConversationStarterSuggestions(
                                starters = starters,
                                onSelect = { starter -> message = starter },
                            )
                        }
                    }
                }
            }
            items(chat.messages, key = { it.id }) { chatMessage ->
                ChatMessage(message = chatMessage, agent = agent, textScale = chatTextScale(textSizeStep))
            }
            if (chat.isRunning) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("agent-working")) {
                        if (agent != null) AgentAvatar(agent, 24)
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(stringResource(R.string.agent_is_replying, agentName), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            chat.errorMessage?.let { error ->
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth().testTag("chat-error"),
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text(stringResource(R.string.message_to_agent, agentName)) },
                enabled = !chat.isRunning,
                modifier = Modifier.weight(1f).testTag("chat-composer"),
                shape = MaterialTheme.shapes.large,
                minLines = 1,
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        enabled = message.isNotBlank() && !chat.isRunning,
                        role = Role.Button,
                    ) {
                        agent?.let {
                            onSendTask(it.id, message.trim())
                            message = ""
                        }
                    }
                    .let { val label = stringResource(R.string.send_message); it.semantics { contentDescription = label } }
                    .testTag("send-task"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(themeSendResource(LocalGraphicTheme.current)),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (message.isBlank() || chat.isRunning) 0.38f else 1f),
                )
            }
        }
    }
}

@Composable
private fun ConversationStarterSuggestions(
    starters: List<String>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        starters.take(4).forEachIndexed { index, starter ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onSelect(starter) }
                    .testTag("conversation-starter-$index"),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        starter,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatMessage(message: ChatMessageUi, agent: AgentSummaryUi?, textScale: Float = 1f) {
    if (message.role == ChatMessageRole.USER) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(17.dp, 17.dp, 4.dp, 17.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(0.86f).testTag("user-message"),
            ) {
                Text(message.text, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(14.dp),
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale, lineHeight = 21.sp * textScale)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth().testTag("agent-message-block")) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("agent-message-header")) {
                if (agent != null) {
                    AgentAvatar(agent, 24)
                    Spacer(Modifier.width(8.dp))
                }
                Text(agent?.name ?: stringResource(R.string.agent), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                if (message.timeLabel.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(message.timeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(7.dp))
            val uriHandler = LocalUriHandler.current
            Box(Modifier.fillMaxWidth().testTag("agent-message")) {
                MarkdownText(message.text, textScale = textScale) { link ->
                    val scheme = android.net.Uri.parse(link).scheme?.lowercase()
                    if (scheme in setOf("https", "http", "mailto")) {
                        runCatching { uriHandler.openUri(link) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationListScreen(
    initialSection: AgentSection,
    automations: List<AutomationUi>,
    automationSavingIds: Set<String>,
    automationErrors: Map<String, String>,
    onSetAutomationEnabled: (String, Boolean) -> Unit,
    onOpenAutomation: (AutomationUi) -> Unit,
    agents: List<AgentSummaryUi>,
    selectedAgent: AgentSummaryUi?,
    conversationPage: AgentConversationsUi,
    onBackToChat: () -> Unit,
    onBackHome: () -> Unit,
    onSelectAgent: (AgentSummaryUi) -> Unit,
    onAddAgent: () -> Unit,
    onNewConversation: (AgentSummaryUi) -> Unit,
    onOpenConversation: (RecentConversationUi) -> Unit,
    onLoadConversations: (agentId: String, loadMore: Boolean) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenName: () -> Unit,
    onOpenRole: () -> Unit,
    onOpenSkills: () -> Unit,
) {
    val selected = selectedAgent ?: agents.firstOrNull()
    val conversationAnchor = remember { BringIntoViewRequester() }
    val automationAnchor = remember { BringIntoViewRequester() }
    val settingsAnchor = remember { BringIntoViewRequester() }
    var sectionOpened by remember(selected?.id, initialSection) { mutableStateOf(false) }
    LaunchedEffect(selected?.id, initialSection, conversationPage.loaded) {
        if (!sectionOpened && (conversationPage.loaded || initialSection == AgentSection.AUTOMATIONS)) {
            withFrameNanos { }
            when (initialSection) {
                AgentSection.CONVERSATIONS -> conversationAnchor
                AgentSection.AUTOMATIONS -> automationAnchor
                AgentSection.SETTINGS -> settingsAnchor
            }.bringIntoView()
            sectionOpened = true
        }
    }
    LaunchedEffect(selected?.id) {
        selected?.let { onLoadConversations(it.id, false) }
    }
    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding().testTag("conversation-list-screen")) {
        AgentTopBar(
            agents = agents,
            selectedAgentId = selected?.id,
            onSelectAgent = onSelectAgent,
            onAddAgent = onAddAgent,
            onBackHome = onBackHome,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.back_to_conversation),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onBackToChat).testTag("close-conversation-list"),
                )
            }
            Spacer(Modifier.height(18.dp))
            if (selected != null) PrimaryAction(stringResource(R.string.new_conversation_plus)) { onNewConversation(selected) }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.automations).uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.bringIntoViewRequester(automationAnchor).testTag("agent-automations-section"))
            Spacer(Modifier.height(8.dp))
            if (automations.isEmpty()) Text(stringResource(R.string.agent_no_automations),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            automations.forEach { automation ->
                AutomationRow(automation = automation, saving = automation.id in automationSavingIds,
                    error = automationErrors[automation.id],
                    onEnabledChange = { onSetAutomationEnabled(automation.id, it) },
                    onClick = { onOpenAutomation(automation) })
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.conversations).uppercase(),
                modifier = Modifier.bringIntoViewRequester(conversationAnchor).testTag("agent-conversations-section"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))
            when {
                conversationPage.items.isEmpty() && conversationPage.errorMessage != null -> {
                    Text(
                        conversationPage.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 14.dp).testTag("conversation-list-error"),
                    )
                    if (selected != null) {
                        OutlinedButton(
                            onClick = { onLoadConversations(selected.id, false) },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("retry-conversations"),
                        ) {
                            Text(stringResource(R.string.try_again))
                        }
                    }
                }
                conversationPage.items.isEmpty() && (!conversationPage.loaded || conversationPage.isLoading) -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                conversationPage.items.isEmpty() -> {
                    Text(
                        stringResource(R.string.agent_no_conversations),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                else -> conversationPage.items.forEach { conversation ->
                    RecentConversationRow(conversation = conversation, onClick = { onOpenConversation(conversation) })
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (conversationPage.items.isNotEmpty() && conversationPage.errorMessage != null) {
                Text(
                    conversationPage.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp).testTag("conversation-list-error"),
                )
            }
            if (conversationPage.hasMore && selected != null) {
                OutlinedButton(
                    onClick = { onLoadConversations(selected.id, true) },
                    enabled = !conversationPage.isLoading,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("load-more-conversations"),
                ) {
                    Text(if (conversationPage.isLoading) stringResource(R.string.loading) else stringResource(R.string.load_more))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.agent_workspace),
                modifier = Modifier.bringIntoViewRequester(settingsAnchor).testTag("agent-settings-section"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))
            WorkspaceDestinationRow("", stringResource(R.string.agent_files), stringResource(R.string.documents_notes), iconDrawable = themeFilesResource(LocalGraphicTheme.current), onClick = onOpenFiles, testTag = "open-agent-files")
            Spacer(Modifier.height(8.dp))
            WorkspaceDestinationRow(
                icon = "✎",
                title = stringResource(R.string.change_look),
                subtitle = stringResource(R.string.photo_and_name),
                onClick = onOpenName,
                testTag = "open-agent-name",
            )
            Spacer(Modifier.height(8.dp))
            WorkspaceDestinationRow(
                icon = "✦",
                title = stringResource(R.string.agent_role),
                subtitle = if (selected?.roleDescription.isNullOrBlank()) {
                    stringResource(R.string.agent_role_short_hint)
                } else {
                    stringResource(R.string.agent_role_set)
                },
                onClick = onOpenRole,
                testTag = "open-agent-role",
            )
            Spacer(Modifier.height(8.dp))
            WorkspaceDestinationRow(
                icon = "",
                iconDrawable = themePowersResource(LocalGraphicTheme.current),
                title = stringResource(R.string.powers),
                subtitle = (selected?.assignedSkillIds?.size ?: 0).let { pluralStringResource(R.plurals.assigned_powers_count, it, it) },
                onClick = onOpenSkills,
                testTag = "open-agent-skills",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun RenameAgentScreen(
    agent: AgentSummaryUi?,
    saving: Boolean,
    saved: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var name by remember(agent?.id) { mutableStateOf(agent?.name.orEmpty()) }
    var avatarIndex by remember(agent?.id) { mutableStateOf(agent?.avatarIndex ?: 1) }
    val focusManager = LocalFocusManager.current
    val normalizedName = name.trim()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("agent-rename-screen"),
    ) {
        Text(
            stringResource(R.string.back_arrow),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onBack).testTag("close-agent-name"),
        )
        Spacer(Modifier.height(34.dp))
        Text(stringResource(R.string.change_look), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            enabled = !saving,
            label = { Text(stringResource(R.string.agent_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("agent-rename-field"),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.agent_photo), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        (1..15).chunked(5).forEach { candidates ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { candidate ->
                    Surface(
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                            .clickable(enabled = !saving, role = Role.RadioButton) { avatarIndex = candidate }
                            .semantics { selected = avatarIndex == candidate }
                            .testTag("agent-appearance-avatar-$candidate"),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(if (avatarIndex == candidate) 2.dp else 1.dp,
                            if (avatarIndex == candidate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Image(painterResource(themeAvatarResource(LocalGraphicTheme.current, candidate)),
                            contentDescription = stringResource(R.string.photo_n, candidate),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.padding(4.dp).clip(MaterialTheme.shapes.small))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { focusManager.clearFocus(); onSave(normalizedName, avatarIndex) },
            enabled = normalizedName.isNotEmpty() && normalizedName.length <= 120 && !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save-agent-name"),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.saving_short))
            } else {
                Text(stringResource(R.string.save_changes), fontWeight = FontWeight.SemiBold)
            }
        }
        if (saved && normalizedName == agent?.name && avatarIndex == agent?.avatarIndex) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.changes_saved),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("agent-name-saved"),
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("agent-name-error"))
        }
    }
}

@Composable
private fun AgentRoleScreen(
    agent: AgentSummaryUi?,
    saving: Boolean,
    saved: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var roleDescription by remember(agent?.id, agent?.roleDescription) {
        mutableStateOf(agent?.roleDescription.orEmpty())
    }
    val normalizedRole = roleDescription.trim()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("agent-role-screen"),
    ) {
        Text(
            stringResource(R.string.back_arrow),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onBack).testTag("close-agent-role"),
        )
        Spacer(Modifier.height(34.dp))
        Text(stringResource(R.string.agent_role), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.agent_role_prompt_named, agent?.name ?: stringResource(R.string.agent_generic)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = roleDescription,
            onValueChange = { if (it.length <= 8_000) roleDescription = it },
            enabled = !saving,
            label = { Text(stringResource(R.string.role_description)) },
            placeholder = { Text(stringResource(R.string.role_description_hint)) },
            supportingText = { Text("${roleDescription.length}/8000") },
            minLines = 7,
            maxLines = 14,
            modifier = Modifier.fillMaxWidth().testTag("agent-role-field"),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onSave(normalizedRole) },
            enabled = normalizedRole.isNotEmpty() && roleDescription.length <= 8_000 && !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save-agent-role"),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.preparing_suggestions))
            } else {
                Text(stringResource(R.string.save_role), fontWeight = FontWeight.SemiBold)
            }
        }
        if (saved && normalizedRole == agent?.roleDescription) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.role_saved),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("agent-role-saved"),
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("agent-role-error"))
        }
    }
}

@Composable
private fun SkillsScreen(
    agent: AgentSummaryUi?,
    definitions: List<SkillDefinitionUi>,
    saving: Boolean,
    saved: Boolean,
    error: String?,
    onBack: () -> Unit,
    onCreateSkill: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var selectedIds by remember(agent?.id, agent?.assignedSkillIds) {
        mutableStateOf(agent?.assignedSkillIds?.toSet().orEmpty())
    }
    val originalIds = agent?.assignedSkillIds?.toSet().orEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("agent-skills-screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.back_arrow),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onBack).testTag("close-agent-skills"),
            )
            Spacer(Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.assigned_count, selectedIds.size, selectedIds.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(agent?.name?.let { stringResource(R.string.powers_of_agent, it) } ?: stringResource(R.string.powers), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(
                stringResource(R.string.choose_agent_powers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onCreateSkill, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("create-skill"), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.new_power_plus))
            }
            Spacer(Modifier.height(16.dp))
            definitions.sortedByDescending { it.id.startsWith("custom-") }.forEach { definition ->
                val checked = if (definition.assignable) definition.id in selectedIds else true
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = definition.assignable && !saving) {
                            selectedIds = if (checked) selectedIds - definition.id else selectedIds + definition.id
                        }
                        .testTag("skill-option-${definition.id}"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = definition.assignable && !saving)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(definition.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (definition.assignable) definition.summary else stringResource(R.string.power_available_all),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            if (saved && selectedIds == originalIds) {
                Text(
                    stringResource(R.string.saved),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("agent-skills-saved"),
                )
                Spacer(Modifier.height(10.dp))
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("agent-skills-error"))
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = { onSave(definitions.filter { it.assignable && it.id in selectedIds }.map { it.id }) },
                enabled = agent != null && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save-agent-skills"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.save_assignments), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceDestinationRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    iconDrawable: Int? = null,
) {
    val surfaceModifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = surfaceModifier) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iconDrawable != null) {
                Image(painterResource(iconDrawable), contentDescription = null, modifier = Modifier.size(42.dp), contentScale = ContentScale.Fit)
            } else {
                Text(icon, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AgentTopBar(
    agents: List<AgentSummaryUi>,
    selectedAgentId: String?,
    onSelectAgent: (AgentSummaryUi) -> Unit,
    onAddAgent: () -> Unit,
    onBackHome: (() -> Unit)? = null,
) {
    val selectedTabRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedAgentId) {
        if (selectedAgentId != null && agents.any { it.id == selectedAgentId }) {
            withFrameNanos { }
            selectedTabRequester.bringIntoView()
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .testTag("agent-top-bar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackHome != null) {
                HomeNavigationButton(
                    onClick = onBackHome,
                )
                Spacer(Modifier.width(4.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                agents.forEach { agent ->
                    val selected = agent.id == selectedAgentId
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
                        modifier = Modifier
                            .width(70.dp)
                            .height(67.dp)
                            .then(
                                if (selected) {
                                    Modifier.bringIntoViewRequester(selectedTabRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelectAgent(agent) },
                            )
                            .testTag("agent-tab-${agent.id}"),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AgentAvatar(agent, 39)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                agent.name,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onAddAgent)
                        .testTag("add-agent"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 23.sp)
                }
            }
        }
    }
}

@Composable
private fun HomeNavigationButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(67.dp)
            .clickable(onClick = onClick)
            .testTag("chat-home")
            .let { val label = stringResource(R.string.home_screen); it.semantics { contentDescription = label } },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(themeHomeResource(LocalGraphicTheme.current)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(62.dp),
        )
    }
}

@Composable
internal fun AgentAvatar(agent: AgentSummaryUi, size: Int) {
    val drawable = agentPortraitResource(agent, LocalGraphicTheme.current)
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(drawable),
            contentDescription = stringResource(R.string.agent_avatar_named, agent.name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
