package one.behavio.mobilebotui.ui

import androidx.compose.animation.core.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.behavio.mobilebotui.R
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource

internal enum class AgentSection { CONVERSATIONS, AUTOMATIONS, SETTINGS }
internal enum class AgentCardAction { SETTINGS, HIDE, DELETE }

@Composable
internal fun AgentHeroGrid(
    agents: List<AgentSummaryUi>,
    automations: List<AutomationUi>,
    hiddenAgentIds: Set<String>,
    onOpenAgent: (AgentSummaryUi) -> Unit,
    onOpenSection: (AgentSummaryUi, AgentSection) -> Unit,
    onAction: (AgentSummaryUi, AgentCardAction) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    // Keep the state holder stable: the long-lived pointer handler writes to it.
    var ordered by remember { mutableStateOf(agents) }
    val currentAgents by rememberUpdatedState(agents)
    val currentOrder by rememberUpdatedState(ordered)
    val saveOrder by rememberUpdatedState(onReorder)
    val slots = remember { mutableStateMapOf<String, Rect>() }
    var dragged by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(agents, dragged) {
        if (dragged == null) ordered = agents
    }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gap = with(density) { LocalThemeDefinition.current.card.gridGap.roundToPx() }
    val placeholderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    Layout(
        modifier = Modifier.fillMaxWidth()
            .drawBehind {
                slots[dragged]?.let { rect ->
                    drawRoundRect(placeholderColor, rect.topLeft, rect.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        finger = point
                        dragged = currentOrder.firstOrNull { slots[it.id]?.contains(point) == true }?.id
                        dragged?.let { id ->
                            grabOffset = point - slots.getValue(id).topLeft
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, delta ->
                        if (dragged != null) {
                            change.consume(); finger += delta
                            val target = currentOrder.firstOrNull { it.id != dragged &&
                                slots[it.id]?.let { rect -> rect.deflate(rect.width * 0.12f).contains(finger) } == true }
                            if (target != null) {
                                val list = currentOrder.toMutableList()
                                val from = list.indexOfFirst { it.id == dragged }
                                val to = list.indexOf(target)
                                if (from >= 0) { list.add(to, list.removeAt(from)); ordered = list }
                            }
                        }
                    },
                    onDragEnd = { if (dragged != null) saveOrder(currentOrder.map { it.id }); dragged = null },
                    onDragCancel = { ordered = currentAgents; dragged = null },
                )
            },
        content = {
            ordered.forEach { agent -> key(agent.id) {
                val slot = slots[agent.id]?.topLeft ?: Offset.Zero
                val target = if (dragged == agent.id) finger - grabOffset else slot
                val position by animateIntOffsetAsState(
                    IntOffset(target.x.roundToInt(), target.y.roundToInt()),
                    animationSpec = if (dragged == agent.id) snap() else spring(dampingRatio = 0.86f, stiffness = 500f),
                    label = "agent-card-position",
                )
                val lift by animateFloatAsState(if (dragged == agent.id) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f), label = "agent-card-lift")
                val moveEarlierLabel = stringResource(R.string.agent_move_earlier)
                val moveLaterLabel = stringResource(R.string.agent_move_later)
                AgentHeroCard(agent, hidden = agent.id in hiddenAgentIds,
                    activeAutomations = automations.count { it.agent.id == agent.id && it.enabled },
                    modifier = Modifier.offset { position }.zIndex(if (dragged == agent.id || lift > 0.01f) 1f else 0f)
                        .graphicsLayer {
                            scaleX = 1f + 0.04f * lift; scaleY = 1f + 0.04f * lift
                            shadowElevation = with(density) { 12.dp.toPx() } * lift
                            shape = RoundedCornerShape(18.dp)
                        }.semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(moveEarlierLabel) {
                                    val list = ordered.toMutableList(); val i = list.indexOf(agent)
                                    if (i > 0) { list.add(i - 1, list.removeAt(i)); ordered = list; onReorder(list.map { it.id }) }; i > 0
                                },
                                CustomAccessibilityAction(moveLaterLabel) {
                                    val list = ordered.toMutableList(); val i = list.indexOf(agent)
                                    if (i >= 0 && i < list.lastIndex) { list.add(i + 1, list.removeAt(i)); ordered = list; onReorder(list.map { it.id }); true } else false
                                },
                            )
                        },
                    onClick = { onOpenAgent(agent) },
                    onOpenSection = { onOpenSection(agent, it) },
                    onAction = { onAction(agent, it) },
                )
            } }
        },
    ) { measurables, constraints ->
        val width = ((constraints.maxWidth - gap) / 2).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
        var y = 0
        placeables.chunked(2).forEachIndexed { rowIndex, row ->
            row.forEachIndexed { column, placeable ->
                val id = ordered[rowIndex * 2 + column].id
                val x = column * (width + gap)
                slots[id] = Rect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + placeable.height).toFloat())
            }
            y += row.maxOf { it.height } + gap
        }
        layout(constraints.maxWidth, (y - gap).coerceAtLeast(0)) {
            // Each keyed item animates its own offset; one parent keeps cross-row moves continuous.
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

internal fun agentPortraitResource(agent: AgentSummaryUi, theme: GraphicTheme = GraphicTheme.HEROES): Int =
    themeAvatarResource(theme, agent.avatarIndex)

@Composable
private fun AgentHeroCard(
    agent: AgentSummaryUi,
    activeAutomations: Int,
    hidden: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onOpenSection: (AgentSection) -> Unit,
    onAction: (AgentCardAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val card = LocalThemeDefinition.current.card
    val colors = MaterialTheme.colorScheme
    val foil = card.treatment == CardTreatment.FOIL
    val accent = if (foil) colors.secondary else colors.primary
    val shape = RoundedCornerShape(card.radius)
    Surface(shape = shape, color = colors.surface,
        border = BorderStroke(card.border, accent.copy(alpha = 0.8f)),
        shadowElevation = card.elevation, modifier = modifier,
    ) {
        Column(Modifier.padding(start = card.inset, top = card.inset, end = card.inset)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape((card.radius - card.inset).coerceAtLeast(0.dp)))) {
                Box(Modifier.fillMaxSize().clickable(role = Role.Button,
                    onClickLabel = stringResource(R.string.agent_new_conversation_with, agent.name), onClick = onClick)
                    .testTag("home-agent-${agent.id}")) {
                    Image(painterResource(agentPortraitResource(agent, LocalGraphicTheme.current)), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                        0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0xE614271C))))
                    if (hidden) Text(stringResource(R.string.agent_hidden_badge), color = Color(0xFFFFF9E9),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                            .background(Color(0xDC18271E), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
                    Text(agent.name, color = Color(0xFFFFF9E9), fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 9.dp, vertical = 8.dp))
                }
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp).testTag("agent-menu-${agent.id}")) {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = RoundedCornerShape(50),
                            color = colors.surfaceVariant.copy(alpha = 0.90f),
                            contentColor = colors.onSurfaceVariant,
                            shadowElevation = 1.dp,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                repeat(3) {
                                    Box(Modifier.size(3.dp).background(colors.onSurfaceVariant, RoundedCornerShape(50)))
                                }
                            }
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.agent_settings)) }, onClick = { menuOpen = false; onAction(AgentCardAction.SETTINGS) })
                        DropdownMenuItem(text = { Text(stringResource(if (hidden) R.string.agent_show else R.string.agent_hide)) },
                            leadingIcon = { AgentVisibilityIcon(hidden = !hidden, description = null) }, onClick = { menuOpen = false; onAction(AgentCardAction.HIDE) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.agent_delete), color = colors.error) },
                            leadingIcon = { Image(painterResource(R.drawable.control_delete_agent), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(colors.error)) }, onClick = { menuOpen = false; onAction(AgentCardAction.DELETE) })
                    }
                }
            }
            // 24 dp contents in 36 dp rows: 12 dp between contents and at both outer edges.
            Column(Modifier.padding(vertical = 6.dp)) {
                AgentCardCounter(stringResource(R.string.automations), stringResource(R.string.active_automations), activeAutomations.toString(), "agent-automations-${agent.id}",
                    { AutomationNavIcon(24.dp) }) { onOpenSection(AgentSection.AUTOMATIONS) }
                AgentCardCounter(stringResource(R.string.conversations), stringResource(R.string.conversations), agent.conversationCount?.toString() ?: "—", "agent-conversations-${agent.id}",
                    { ConversationBubblesIcon(24.dp, adaptToSurface = true) }) { onOpenSection(AgentSection.CONVERSATIONS) }
            }
        }
    }
}

@Composable
private fun AgentCardCounter(label: String, accessibleLabel: String, count: String, tag: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "$accessibleLabel: $count" }
        .padding(horizontal = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f), maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(count, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.widthIn(min = 20.dp), maxLines = 1)
    }
}

@Composable
internal fun AgentVisibilityIcon(hidden: Boolean, description: String?) {
    Image(painterResource(if (hidden) R.drawable.control_eye_hidden else R.drawable.control_eye_open),
        contentDescription = description, modifier = Modifier.size(24.dp), contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant))
}
