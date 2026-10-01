package one.behavio.mobilebotui.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import one.behavio.mobilebotui.R
import java.util.WeakHashMap
import androidx.compose.ui.res.stringResource

object TrailStopIds {
    const val BASE = "base"
    const val TEAM = "team"
    const val SKILLS = "skills"
    const val PHONE = "phone"
    const val MISSION = "mission"

    internal val all = setOf(BASE, TEAM, SKILLS, PHONE, MISSION)
}

private data class StatusBarAppearanceLease(
    val originalLightIcons: Boolean,
    var ownerCount: Int,
)

private val statusBarAppearanceLeases = WeakHashMap<Window, StatusBarAppearanceLease>()

private data class TrailStop(
    val id: String,
    val name: String,
    @param:DrawableRes val icon: Int,
    val preview: String,
    val previewActionLabel: String,
    val message: String,
    val actionLabel: String,
    val done: Boolean,
    val optional: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingTrailScreen(
    baseReady: Boolean,
    teamReady: Boolean,
    skillsReady: Boolean,
    phoneEnabled: Boolean,
    phoneConnected: Boolean,
    missionReady: Boolean,
    skippedStops: Set<String>,
    onBaseAction: () -> Unit,
    onTeamAction: () -> Unit,
    onSkillsAction: () -> Unit,
    onPhoneSettings: () -> Unit,
    onPhoneCheck: () -> Unit,
    onMissionAction: () -> Unit,
    onSkipStop: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    initialStop: String? = null,
    baseContent: (@Composable () -> Unit)? = null,
) {
    DarkStatusBarIcons()
    val trailScroll = rememberScrollState()
    val phoneReady = phoneEnabled && phoneConnected
    val stops = listOf(
        TrailStop(
            TrailStopIds.BASE,
            stringResource(R.string.trail_base),
            themeHomeResource(LocalGraphicTheme.current),
            if (baseReady) stringResource(R.string.trail_base_ready) else stringResource(R.string.trail_base_todo),
            if (baseReady) stringResource(R.string.see_details) else stringResource(R.string.setup_prepare_app),
            if (baseReady) stringResource(R.string.trail_base_ready_body) else stringResource(R.string.trail_base_todo_body),
            stringResource(R.string.check_again),
            baseReady,
            false,
        ),
        TrailStop(
            TrailStopIds.TEAM,
            stringResource(R.string.trail_team),
            R.drawable.trail_team,
            if (teamReady) stringResource(R.string.trail_team_ready) else stringResource(R.string.trail_team_todo),
            stringResource(R.string.meet_agents),
            stringResource(R.string.trail_team_body),
            stringResource(R.string.see_crew),
            teamReady,
            false,
        ),
        TrailStop(
            TrailStopIds.SKILLS,
            stringResource(R.string.powers),
            themePowersResource(LocalGraphicTheme.current),
            if (skillsReady) stringResource(R.string.trail_powers_ready) else stringResource(R.string.trail_powers_todo),
            stringResource(R.string.choose),
            stringResource(R.string.trail_powers_body),
            stringResource(R.string.choose_powers),
            skillsReady,
            true,
        ),
        TrailStop(
            TrailStopIds.PHONE,
            stringResource(R.string.trail_phone),
            R.drawable.trail_phone,
            when {
                phoneReady -> stringResource(R.string.trail_phone_ready)
                phoneEnabled -> stringResource(R.string.trail_phone_enabled)
                else -> stringResource(R.string.trail_phone_todo)
            },
            if (phoneReady || phoneEnabled) stringResource(R.string.check_access) else stringResource(R.string.show_how),
            if (phoneReady) stringResource(R.string.trail_phone_ready_body) else stringResource(R.string.trail_phone_todo_body),
            stringResource(R.string.show_steps),
            phoneReady,
            true,
        ),
        TrailStop(
            TrailStopIds.MISSION,
            stringResource(R.string.trail_mission),
            R.drawable.trail_mission,
            if (missionReady) stringResource(R.string.trail_mission_ready) else stringResource(R.string.trail_mission_todo),
            stringResource(R.string.try_agent),
            stringResource(R.string.trail_mission_body),
            stringResource(R.string.start_mission),
            missionReady,
            true,
        ),
    )
    val defaultStop = if (!baseReady) {
        TrailStopIds.BASE
    } else {
        initialStop?.takeIf(TrailStopIds.all::contains)
    }
    var selectedStopId by rememberSaveable(defaultStop) { mutableStateOf(defaultStop) }
    var sheetOpen by rememberSaveable { mutableStateOf(!baseReady) }
    var showPhoneSteps by rememberSaveable { mutableStateOf(false) }
    var phoneSettingsOpened by rememberSaveable { mutableStateOf(false) }
    val selectedStop = stops.firstOrNull { it.id == selectedStopId }
    val completedCount = stops.count { it.done }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TrailBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(trailScroll)
            .animateContentSize()
            .testTag("trail-screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.first_mission), color = TrailText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("$completedCount/${stops.size}", color = TrailMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val nodeOffsets = listOf(
                maxWidth * 0.50f - 50.dp,
                maxWidth * 0.35f - 50.dp,
                maxWidth * 0.28f - 50.dp,
                maxWidth * 0.43f - 50.dp,
                maxWidth * 0.60f - 50.dp,
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                stops.forEachIndexed { index, stop ->
                    TrailPathStop(
                        stop = stop,
                        selected = stop.id == selectedStopId,
                        skipped = stop.id in skippedStops,
                        enabled = baseReady || stop.id == TrailStopIds.BASE,
                        offset = nodeOffsets[index],
                        onClick = {
                            selectedStopId = if (selectedStopId == stop.id) null else stop.id
                        },
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = if (selectedStop == null) 170.dp else 90.dp, end = 8.dp)
                    .width(maxWidth * 0.44f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                selectedStop?.let { stop ->
                    ComicBubble(modifier = Modifier.fillMaxWidth().testTag("trail-preview")) {
                        Text(
                            stop.preview,
                            color = BubbleInk, fontSize = 13.sp, lineHeight = 17.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        BubbleAction(stop.previewActionLabel, "trail-open-details", { sheetOpen = true })
                    }
                }
                Image(
                    painter = painterResource(LocalThemeDefinition.current.artwork.guide),
                    contentDescription = stringResource(R.string.first_mission_guide),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                )
            }
        }

        if (baseReady) {
            OutlinedButton(
                onClick = onContinue,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .height(46.dp)
                    .testTag("trail-continue"),
            ) {
                Text(stringResource(R.string.go_to_app), fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (sheetOpen && selectedStop != null) {
        val stop = selectedStop
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Bubble,
            contentColor = BubbleInk,
        ) {
            Column(
                Modifier.fillMaxWidth().testTag("trail-details")
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stop.name, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { sheetOpen = false }, modifier = Modifier.testTag("trail-close-details")) {
                        Text(stringResource(R.string.close), color = BubbleInk)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (stop.id == TrailStopIds.BASE && baseContent != null && !baseReady) {
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                        primary = BubbleInk, onPrimary = Bubble, surface = Bubble,
                        onSurface = BubbleInk, onSurfaceVariant = BubbleMuted,
                    )) { baseContent() }
                } else {
                    Text(stop.message, color = BubbleInk, fontSize = 16.sp, lineHeight = 23.sp)
                    Spacer(Modifier.height(16.dp))
                    if (stop.id == TrailStopIds.PHONE) {
                        PhoneActions(
                            ready = phoneReady, phoneEnabled = phoneEnabled,
                            showSteps = showPhoneSteps, settingsOpened = phoneSettingsOpened,
                            onShowSteps = { showPhoneSteps = true },
                            onOpenSettings = { phoneSettingsOpened = true; onPhoneSettings() },
                            onCheck = onPhoneCheck,
                        )
                    } else {
                        BubbleAction(stop.actionLabel, "trail-action-${stop.id}") {
                            sheetOpen = false
                            when (stop.id) {
                                TrailStopIds.BASE -> onBaseAction()
                                TrailStopIds.TEAM -> onTeamAction()
                                TrailStopIds.SKILLS -> onSkillsAction()
                                TrailStopIds.MISSION -> onMissionAction()
                            }
                        }
                    }
                    if (stop.optional && !stop.done) {
                        TextButton(
                            onClick = { onSkipStop(stop.id); sheetOpen = false },
                            modifier = Modifier.testTag("trail-skip-${stop.id}"),
                        ) { Text(stringResource(R.string.skip), color = BubbleMuted) }
                    }
                }
            }
        }
    }

}

@Composable
private fun ComicBubble(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // The guide follows the bubble in the same Column. Reserve the tail's space
    // below the body so wrapped or enlarged text cannot change its direction.
    val bubbleColor = Bubble
    val outlineColor = BubbleInk
    Box(modifier.padding(bottom = 20.dp).drawBehind {
        val w = size.width
        val h = size.height
        val inset = 3.dp.toPx()
        val corner = 23.dp.toPx()
        val tailX = w * 0.56f
        val tailHalfWidth = 10.dp.toPx()
        val tailLength = 16.dp.toPx()
        val outline = Path().apply {
            moveTo(corner, inset)
            cubicTo(w * 0.40f, 0f, w * 0.72f, inset * 2, w - corner, inset)
            quadraticTo(w - inset, inset, w - inset, corner)
            cubicTo(w, h * 0.38f, w - inset, h * 0.68f, w - inset * 2, h - corner)
            quadraticTo(w - inset * 2, h - inset, w - corner, h - inset)
            lineTo(tailX + tailHalfWidth, h - inset)
            quadraticTo(tailX + tailHalfWidth, h + tailLength * 0.5f, tailX, h + tailLength)
            quadraticTo(tailX, h + tailLength * 0.25f, tailX - tailHalfWidth, h - inset)
            cubicTo(w * 0.35f, h, w * 0.25f, h - inset * 2, corner, h - inset)
            quadraticTo(inset, h - inset, inset, h - corner)
            lineTo(inset, corner)
            quadraticTo(inset, inset, corner, inset)
            close()
        }
        drawPath(outline, bubbleColor)
        drawPath(outline, outlineColor, style = Stroke(width = 2.5.dp.toPx()))
    }) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) { content() }
    }
}

@Composable
private fun PhoneActions(
    ready: Boolean,
    phoneEnabled: Boolean,
    showSteps: Boolean,
    settingsOpened: Boolean,
    onShowSteps: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheck: () -> Unit,
) {
    when {
        ready -> {
            BubbleAction(stringResource(R.string.check_connection), "trail-phone-check", onCheck)
            SmallSettingsAction(stringResource(R.string.settings), onOpenSettings)
        }
        phoneEnabled || settingsOpened -> {
            Text(stringResource(R.string.phone_check_after_return), color = BubbleMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            BubbleAction(stringResource(R.string.check_connection), "trail-phone-check", onCheck)
            SmallSettingsAction(stringResource(R.string.open_settings_again), onOpenSettings)
        }
        showSteps -> {
            CompactStep(stringResource(R.string.phone_steps_intro))
            CompactStep(stringResource(R.string.phone_step_1))
            CompactStep(stringResource(R.string.phone_step_2))
            CompactStep(stringResource(R.string.phone_step_3))
            CompactStep(stringResource(R.string.phone_step_4))
            CompactStep(stringResource(R.string.phone_steps_privacy))
            Spacer(Modifier.height(6.dp))
            BubbleAction(stringResource(R.string.open_settings), "trail-action-phone", onOpenSettings)
        }
        else -> BubbleAction(stringResource(R.string.show_steps), "trail-phone-show-steps", onShowSteps)
    }
}

@Composable
private fun CompactStep(text: String) {
    Text(text, color = BubbleMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(vertical = 1.dp))
}

@Composable
private fun SmallSettingsAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = BubbleMuted),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.height(40.dp).testTag("trail-action-phone"),
    ) { Text(label, fontSize = 11.sp) }
}

@Composable
private fun BubbleAction(label: String, testTag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp).testTag(testTag),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrailPathStop(
    stop: TrailStop,
    selected: Boolean,
    skipped: Boolean,
    enabled: Boolean,
    offset: Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 700f),
        label = "trail-node-press-${stop.id}",
    )
    val fill by androidx.compose.animation.animateColorAsState(if (!enabled) Color(0xFF929794) else if (stop.done) TrailGreen else if (stop.optional) TrailYellow else TrailBlue, tween(220), label = "trail-status-color")
    val border = if (!enabled) Color(0xFF717773) else if (stop.done) TrailGreenBorder else if (stop.optional) TrailYellowBorder else TrailBlueBorder
    val shadow = if (!enabled) Color(0xFF505652) else if (stop.done) TrailGreenShadow else if (stop.optional) TrailYellowShadow else TrailBlueShadow

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val stopState = stringResource(
            when {
                !enabled -> R.string.trail_needs_base
                stop.done -> R.string.done
                skipped -> R.string.later
                stop.optional -> R.string.optional
                else -> R.string.todo
            },
        )
        Column(
            modifier = Modifier
                .offset(x = offset)
                .width(100.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .semantics { stateDescription = stopState }
                .testTag("trail-stop-${stop.id}")
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.width(100.dp).height(68.dp)) {
                val statusLabel = when {
                    !enabled -> stringResource(R.string.unavailable)
                    stop.done -> stringResource(R.string.done)
                    skipped -> stringResource(R.string.later)
                    stop.optional -> stringResource(R.string.optional)
                    else -> stringResource(R.string.todo)
                }
                Box(
                    modifier = Modifier.offset(x = 88.dp, y = 19.dp)
                        .size(30.dp)
                        .border(2.dp, if (enabled) fill else Color(0xFF717773), RoundedCornerShape(8.dp))
                        .background(TrailBackground, RoundedCornerShape(8.dp))
                        .testTag("trail-status-${stop.id}")
                        .semantics {
                            contentDescription = statusLabel
                            toggleableState = if (stop.done && enabled) ToggleableState.On else ToggleableState.Off
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (enabled && stop.done) Image(
                        painter = painterResource(R.drawable.trail_check),
                        contentDescription = null,
                        modifier = Modifier.size(25.dp),
                    )
                }
            Box(modifier = Modifier.offset(x = 16.dp).size(68.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    modifier = Modifier.size(64.dp).offset(y = 4.dp).clip(CircleShape).background(shadow),
                )
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = fill,
                    border = BorderStroke(if (selected) 5.dp else 4.dp, if (selected) TrailText else border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(stop.icon),
                            colorFilter = if (!enabled) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(47.dp),
                        )
                    }
                }
            }
            }
            Text(stop.name, color = if (enabled) TrailText else Color(0xFF9CA39E), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (skipped || (stop.optional && !stop.done)) {
                Text(
                    if (skipped) stringResource(R.string.later) else stringResource(R.string.optional),
                    color = if (enabled) Color(0xFFF2D780) else Color(0xFF9CA39E),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun DarkStatusBarIcons() {
    val view = LocalView.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    DisposableEffect(view, dark) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val lease = statusBarAppearanceLeases[window]
            if (lease == null) {
                statusBarAppearanceLeases[window] = StatusBarAppearanceLease(
                    originalLightIcons = controller.isAppearanceLightStatusBars,
                    ownerCount = 1,
                )
            } else {
                lease.ownerCount += 1
            }
            controller.isAppearanceLightStatusBars = !dark
            onDispose {
                val activeLease = statusBarAppearanceLeases[window] ?: return@onDispose
                activeLease.ownerCount -= 1
                if (activeLease.ownerCount == 0) {
                    statusBarAppearanceLeases.remove(window)
                    controller.isAppearanceLightStatusBars = activeLease.originalLightIcons
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
