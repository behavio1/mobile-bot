package one.behavio.mobilebotui.ui

import one.behavio.mobilebotui.R

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import one.behavio.mobilebotui.setup.*
import org.junit.*
import org.junit.Assert.*

class AgentCardsTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val testName = org.junit.rules.TestName()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var oldAvatar = 1
    private var oldOrder = emptyList<String>()
    private var oldHidden = emptySet<String>()
    private val atlas = starterAgentsUi().first().copy(conversationCount = 36)
    private val nova = starterAgentsUi()[1].copy(conversationCount = 2)
    @Before fun prepare() {
        oldAvatar = VisualPreferences.avatarIndex(context, atlas.id)
        oldOrder = VisualPreferences.agentOrder(context); oldHidden = VisualPreferences.hiddenAgents(context)
        VisualPreferences.saveAgentOrder(context, emptyList()); VisualPreferences.saveHiddenAgents(context, emptySet())
    }
    @After fun restore() {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = java.io.File(context.filesDir, "agent-card-screenshots").apply { mkdirs() }
        instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot()?.let { bitmap ->
            java.io.File(directory, "${testName.methodName}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        VisualPreferences.saveAvatarIndex(context, atlas.id, oldAvatar)
        VisualPreferences.saveAgentOrder(context, oldOrder); VisualPreferences.saveHiddenAgents(context, oldHidden)
    }
    private fun automation(agent: AgentSummaryUi, id: String, enabled: Boolean = true) = AutomationUi(
        id=id, conversationId="conversation-$id", agent=agent, name="Automatyzacja $id", intervalMinutes=15,
        statusLabel="", nextRunAtLabel="Za 15 minut", lastSummary=null, scheduledAtLabel=null, startedAtLabel=null,
        delayLabel=null, waitingDurationLabel=null, deferredReasonLabel=null, skippedOccurrences=0, enabled=enabled)

    private fun show(onSelect: (String, Boolean)->Unit = {_,_->}, onDelete:(String)->Unit = {}, dark:Boolean=false) {
        val state = WorkspaceUiState(agents=listOf(atlas,nova), snapshotLoaded=true,
            automations=listOf(automation(atlas,"a"),automation(atlas,"paused",false),automation(nova,"other")),
            conversationPagesByAgent=mapOf(atlas.id to AgentConversationsUi(loaded=true)))
        compose.setContent { MobileBotTheme { MobileBotApp(
            facts=SetupFacts(termuxInstalled=true, runCommandPermissionGranted=true, bridgeState=BridgeState.ENABLED,
                hostStatus=HostStatus(reachable=true,protocolVersion=TermuxContract.PROTOCOL_VERSION,
                    environmentRevision=TermuxContract.ENVIRONMENT_REVISION,hostVersion=TermuxContract.HOST_VERSION,
                    codexInstalled=true,codexAuthenticated=true)),
            workspace=state, phoneAutomationOnboardingSkipped=true, phoneAutomationEnabled=true,
            onRefresh={},onInstallTermux={},onRequestPermission={},onOpenTermux={},onConfigureTermux={},onBootstrap={},onLogin={},
            onSelectAgent=onSelect,onDeleteAgent=onDelete,onCreateAgentTask={},
        ) } }
    }
    @Test fun portraitStartsNewConversation() {
        var selected: Pair<String,Boolean>?=null
        show(onSelect={id,new->selected=id to new})
        compose.onNodeWithTag("home-agent-${atlas.id}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(atlas.id to true,selected) }
    }
    @Test fun automationCounterOpensOnlyThatAgentsSection() {
        show()
        compose.onNodeWithTag("agent-automations-${atlas.id}").performScrollTo().assertTextContains("1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent-automations-section").assertIsDisplayed()
        compose.onNodeWithText("Automatyzacja other").assertDoesNotExist()
        compose.onNodeWithText("Automatyzacja a").assertIsDisplayed()
    }
    @Test fun conversationCounterOpensConversationSection() {
        show()
        compose.onNodeWithTag("agent-conversations-${atlas.id}").performScrollTo().assertTextContains("36").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent-conversations-section").assertIsDisplayed()
    }
    @Test fun hideToggleAndRestorePersist() {
        show(dark=true)
        compose.onNodeWithTag("agent-menu-${atlas.id}").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.agent_hide)).performClick()
        compose.onNodeWithTag("home-agent-${atlas.id}").assertDoesNotExist()
        compose.runOnIdle { assertTrue(atlas.id in VisualPreferences.hiddenAgents(context)) }
        compose.onNodeWithTag("toggle-hidden-agents").performScrollTo().performClick()
        compose.onNodeWithTag("agent-menu-${atlas.id}").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.agent_show)).performClick()
        compose.runOnIdle { assertFalse(atlas.id in VisualPreferences.hiddenAgents(context)) }
        compose.onNodeWithTag("home-agent-${atlas.id}").assertIsDisplayed()
    }
    @Test fun deleteRequiresConfirmationAndCanBeCancelled() {
        var deleted:String?=null
        show(onDelete={deleted=it})
        compose.onNodeWithTag("agent-menu-${atlas.id}").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.agent_delete)).performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.runOnIdle { assertNull(deleted) }
        compose.onNodeWithTag("agent-menu-${atlas.id}").performClick()
        compose.onNodeWithText(text(R.string.agent_delete)).performClick()
        compose.onNodeWithText(text(R.string.agent_delete)).performClick()
        compose.runOnIdle { assertEquals(atlas.id,deleted) }
    }
    @Test fun longPressDragReordersWithoutStartingConversation() {
        var saved=emptyList<String>();var opened=false
        compose.setContent { MobileBotTheme { Surface(Modifier.width(360.dp)) {
            AgentHeroGrid(agents=listOf(atlas,nova),automations=emptyList(),hiddenAgentIds=emptySet(),
                onOpenAgent={opened=true},onOpenSection={_,_->},onAction={_,_->},onReorder={saved=it})
        } } }
        val start=compose.onNodeWithTag("home-agent-${atlas.id}").fetchSemanticsNode().boundsInRoot.center
        val end=compose.onNodeWithTag("home-agent-${nova.id}").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("home-agent-${atlas.id}").performTouchInput {
            down(center);advanceEventTime(700);moveBy(end-start,delayMillis=400);advanceEventTime(100);up()
        }
        compose.runOnIdle { assertFalse(opened); assertEquals(listOf(nova.id,atlas.id),saved) }
    }
    @Test fun repeatedDragsAcrossRowsPersistAfterParentUpdates() {
        val initial = starterAgentsUi().take(4)
        var agents by mutableStateOf(initial)
        var opened = false
        compose.setContent { MobileBotTheme { Surface(Modifier.width(320.dp)) {
            AgentHeroGrid(agents=agents, automations=emptyList(), hiddenAgentIds=emptySet(),
                onOpenAgent={opened=true}, onOpenSection={_,_->}, onAction={_,_->},
                onReorder={ ids -> agents = ids.map { id -> agents.first { it.id == id } } })
        } } }
        fun move(from: Int, to: Int) {
            compose.waitForIdle()
            val before = agents
            val source = compose.onNodeWithTag("home-agent-${before[from].id}")
            val start = source.fetchSemanticsNode().boundsInRoot.center
            val end = compose.onNodeWithTag("home-agent-${before[to].id}").fetchSemanticsNode().boundsInRoot.center
            source.performTouchInput {
                down(center); advanceEventTime(700)
                moveBy(end-start, delayMillis=400); advanceEventTime(100); up()
            }
            compose.waitForIdle()
            val expected = before.toMutableList().apply { add(to, removeAt(from)) }
            compose.runOnIdle { assertEquals(expected.map { it.id }, agents.map { it.id }); assertFalse(opened) }
        }
        move(0, 1)
        move(3, 0)
        move(2, 1)
        move(0, 3)
    }

    @Test fun appearanceSelectsPortraitAndSavesWithName() {
        var agent by mutableStateOf(atlas)
        var saved by mutableStateOf(false)
        var submitted: Pair<String, Int>? = null
        compose.setContent { MobileBotTheme { Surface {
            RenameAgentScreen(agent, saving=false, saved=saved, error=null, onBack={},
                onSave={ name, avatar ->
                    assertTrue(VisualPreferences.saveAvatarIndex(context, agent.id, avatar))
                    submitted = name to avatar
                    agent = agent.copy(name=name, avatarIndex=avatar)
                    saved = true
                })
        } } }
        compose.onNodeWithTag("agent-appearance-avatar-${atlas.avatarIndex}").assertIsSelected()
        compose.onNodeWithTag("agent-appearance-avatar-15").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertNull(submitted) }
        compose.onNodeWithTag("agent-rename-field").performScrollTo().performTextReplacement("Aurora")
        compose.onNodeWithTag("save-agent-name").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Aurora" to 15, submitted); assertEquals(15, VisualPreferences.avatarIndex(context, agent.id)) }
        compose.onNodeWithTag("agent-name-saved").performScrollTo().assertIsDisplayed()
    }

}

private fun text(id: Int, vararg args: Any): String =
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
