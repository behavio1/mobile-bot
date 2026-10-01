package one.behavio.mobilebotui.ui

import one.behavio.mobilebotui.R

import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import one.behavio.mobilebotui.setup.BridgeState
import one.behavio.mobilebotui.setup.HostStatus
import one.behavio.mobilebotui.setup.SetupFacts
import one.behavio.mobilebotui.setup.TermuxContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetupScreenTest {
    @get:Rule
    val matrixTestName = org.junit.rules.TestName()

    @org.junit.After
    fun captureMatrixScreenWhenRequested() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("matrixScreenshots") != "true") return
        val directory = java.io.File(instrumentation.targetContext.filesDir, "matrix-screenshots").apply { mkdirs() }
        val screenshot = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot()
        screenshot?.let { bitmap ->
            java.io.File(directory, "${matrixTestName.methodName}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun missingTermuxShowsInstallAction() {
        var installOpened = false
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = SetupFacts(termuxInstalled = false),
                    onRefresh = {},
                    onInstallTermux = { installOpened = true },
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.setup_title_termux_missing)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.download_termux)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(installOpened) }
    }

    @Test
    fun checkingExistingInstallationHidesStaleDownloadAction() {
        setSetupContent(SetupFacts(termuxInstalled = false, checking = true,
            detail = "Sprawdzam zainstalowanego Termuksa…"))
        compose.onNodeWithText(text(R.string.download_termux)).assertIsNotDisplayed()
        compose.onNodeWithTag("setup-current-status-text")
            .performScrollTo().assertTextContains("Sprawdzam zainstalowanego Termuksa…")
    }

    @Test
    fun checkingStateDoesNotExposeASetupAction() {
        setSetupContent(SetupFacts())

        compose.onNodeWithText(text(R.string.setup_title_checking)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_checking_components)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.download_termux)).assertIsNotDisplayed()
        compose.onNodeWithText(text(R.string.start)).assertIsNotDisplayed()
    }

    @Test
    fun missingRunCommandPermissionRequestsAndroidPermission() {
        var requested = false
        setSetupContent(
            facts = SetupFacts(
                termuxInstalled = true,
                runCommandPermissionGranted = false,
            ),
            onRequestPermission = { requested = true },
        )

        compose.onNodeWithText(text(R.string.setup_title_permission)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.grant_termux_access)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun failedBridgeOffersTermuxAndRetryActions() {
        var opened = false
        var refreshed = false
        setSetupContent(
            facts = SetupFacts(
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                bridgeState = BridgeState.FAILED,
                detail = "probe_failed",
            ),
            onOpenTermux = { opened = true },
            onRefresh = { refreshed = true },
        )

        compose.onNodeWithText(text(R.string.setup_title_bridge_failed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("setup-detail").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.open_termux)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
        compose.onNodeWithText(text(R.string.check_again)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(refreshed) }
    }

    @Test
    fun incompleteOrOutdatedEnvironmentStartsBootstrap() {
        var bootstrapped = false
        setSetupContent(
            facts = readyFacts().copy(
                hostStatus = readyFacts().hostStatus?.copy(hostVersion = "0.0.0"),
            ),
            onBootstrap = { bootstrapped = true },
        )

        compose.onNodeWithTag("setup-title").assertTextContains(text(R.string.setup_prepare_app)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_body_bootstrap))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_step_host_codex)).assertIsNotDisplayed()
        compose.onNodeWithTag("primary-action")
            .assertTextContains(text(R.string.start))
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertTrue(bootstrapped) }
    }

    @Test
    fun activeBootstrapShowsProgressWithoutAnotherAction() {
        val currentDetail = "Pobieranie aplikacji: krok 2 z 4"
        setSetupContent(
            SetupFacts(
                termuxInstalled = true,
                runCommandPermissionGranted = true,
                bridgeState = BridgeState.ENABLED,
                busy = true,
                detail = currentDetail,
            ),
        )

        compose.onNodeWithText(text(R.string.setup_title_preparing)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_body_preparing))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("setup-current-status").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(currentDetail).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.setup_step_host_codex)).assertIsNotDisplayed()
        compose.onNodeWithText(text(R.string.setup_step_login)).assertIsNotDisplayed()
        compose.onNodeWithText(text(R.string.start)).assertIsNotDisplayed()
    }

    @Test
    fun bootstrapStatusWrapsWithoutClippingAtLargeFont() {
        val detail = "Pobieram niezbędne aplikacje i sprawdzam ich wersje. Czekam na zakończenie przygotowania."
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 1.5f),
            ) {
                MobileBotTheme {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.width(220.dp),
                    ) { BootstrapProgress(detail) }
                }
            }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("setup-current-status-text", useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        compose.runOnIdle {
            val layout = layouts.single()
            assertTrue("Status must wrap on a narrow screen", layout.lineCount > 1)
            assertTrue("Full progress text must remain visible", !layout.hasVisualOverflow)
            assertTrue("No line may be ellipsized", (0 until layout.lineCount).none { layout.isLineEllipsized(it) })
            assertEquals(detail.length, layout.getLineEnd(layout.lineCount - 1))
        }
    }

    @Test
    fun installedButLoggedOutCodexStartsVisibleLogin() {
        var loginStarted = false
        setSetupContent(
            facts = readyFacts().copy(
                hostStatus = readyFacts().hostStatus?.copy(codexAuthenticated = false),
            ),
            onLogin = { loginStarted = true },
        )

        compose.onNodeWithText(text(R.string.setup_title_codex_login)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.codex_sign_in)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(loginStarted) }
    }

    @Test
    fun readyHomeShowsStarterAgentsWithoutDuplicateConversationSection() {
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.home_title)).assertIsDisplayed()
        compose.onNodeWithText("Atlas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nova").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Echo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ostatnie rozmowy").assertDoesNotExist()
        compose.onNodeWithTag("conversations-empty-state").assertDoesNotExist()
        compose.onNodeWithText("Rozpocznij rozmowę").assertDoesNotExist()
        compose.onNodeWithText("Wybierz agenta albo wróć do jednej z ostatnich rozmów.").assertDoesNotExist()
    }

    @Test
    fun disabledPhoneControlShowsActionableNoticeOnHome() {
        var settingsOpened = false
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    phoneAutomationEnabled = false,
                    facts = readyFacts(),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onOpenPhoneAutomationSettings = { settingsOpened = true },
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithTag("phone-access-disabled").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.phone_control_off)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.phone_control_off_body)).assertIsDisplayed()
        compose.onNodeWithTag("phone-access-enable").performClick()
        compose.runOnIdle { assertTrue(settingsOpened) }

        settingsOpened = false
        compose.onNodeWithText("Atlas").performClick()
        compose.onNodeWithTag("phone-access-disabled").assertIsDisplayed()
        compose.onNodeWithTag("phone-access-enable").performClick()
        compose.runOnIdle { assertTrue(settingsOpened) }
    }

    @Test
    fun waitingAutomationShowsReasonCurrentDelayAndMissedTerms() {
        val agent = starterAgentsUi().first()
        val automation = AutomationUi(
            id = "waiting-test",
            conversationId = "conversation-test",
            agent = agent,
            name = "Najtańszy kabel USB-C",
            intervalMinutes = 15,
            statusLabel = text(R.string.automation_status_waiting_unlock),
            nextRunAtLabel = "Dzisiaj 10:15",
            lastSummary = null,
            scheduledAtLabel = "Dzisiaj 11:00",
            startedAtLabel = null,
            delayLabel = null,
            waitingDurationLabel = "47 min",
            deferredReasonLabel = text(R.string.automation_reason_unlock),
            skippedOccurrences = 3,
        )
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = WorkspaceUiState(agents = listOf(agent), automations = listOf(automation)),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithTag("automation-waiting-test").assertDoesNotExist()
        compose.onNodeWithTag("home-automations-open").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.automation_status_waiting_unlock)).performScrollTo().assertIsDisplayed()
        // The list exposes the current blocker; scheduling diagnostics stay in details.
        compose.onNodeWithTag("automation-history-waiting-test").assertDoesNotExist()
        compose.onNodeWithTag("automation-details-open-waiting-test").performScrollTo().performClick()
        compose.onNodeWithTag("automation-details-waiting-test").assertIsDisplayed()
        compose.onNodeWithTag("automation-history-waiting-test").performScrollTo().performClick()
        compose.onNodeWithText("47 min", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("automation-skipped-waiting-test").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun advancedSettingsOpenThePhoneSetup() {
        var opened = false
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    phoneAutomationEnabled = false,
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onOpenPhoneAutomationSettings = { opened = true },
                )
            }
        }

        compose.onNodeWithTag("home-settings-open").performScrollTo().performClick()
        compose.onNodeWithTag("home-settings-advanced").performClick()
        compose.onNodeWithTag("trail-stop-phone").performScrollTo().performClick()
        compose.onNodeWithTag("trail-open-details").performScrollTo().performClick()
        compose.onNodeWithTag("trail-phone-show-steps").performScrollTo().performClick()
        compose.onNodeWithTag("trail-action-phone").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun selectingAgentAllowsRealTaskSubmissionCallback() {
        var submittedAgent: String? = null
        var submittedPrompt: String? = null
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onSendTask = { agentId, prompt ->
                        submittedAgent = agentId
                        submittedPrompt = prompt
                    },
                )
            }
        }

        compose.onNodeWithText("Atlas").performClick()
        compose.onNodeWithTag("chat-composer").performTextInput("Sprawdź stan dokumentacji")
        compose.onNodeWithTag("send-task").performClick()
        compose.runOnIdle {
            assertEquals("starter-atlas", submittedAgent)
            assertEquals("Sprawdź stan dokumentacji", submittedPrompt)
        }
    }

    @Test
    fun chatHeaderOpensAgentSkillsDirectly() {
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithText("Atlas").performClick()
        compose.onNodeWithTag("chat-agent-skills").performClick()

        compose.onNodeWithTag("agent-skills-screen").assertIsDisplayed()
        compose.onNodeWithText("Moce Atlas").assertIsDisplayed()
    }

    @Test
    fun chatHeaderUsesConversationAndSkillsIconsWithoutIdleBadge() {
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithText("Atlas").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Gotowy").assertDoesNotExist()
        compose.onNodeWithTag("chat-home")
            .assertContentDescriptionContains(text(R.string.home_screen))
            .assertIsDisplayed()
        compose.onNodeWithTag("conversation-list-toggle")
            .assertContentDescriptionContains(text(R.string.conversations))
            .performClick()
        compose.onNodeWithTag("conversation-list-screen").assertIsDisplayed()
        compose.onNodeWithTag("close-conversation-list").performClick()
        compose.onNodeWithTag("chat-agent-skills")
            .assertContentDescriptionContains(text(R.string.powers))
            .performClick()
        compose.onNodeWithTag("agent-skills-screen").assertIsDisplayed()
    }

    @Test
    fun phoneTaskReturnOpensItsAgentChatOnlyOncePerRun() {
        val targetAgent = starterAgentsUi().first()
        var conversationOpenRequested = false
        var workspace by mutableStateOf(
            WorkspaceUiState(
                agents = listOf(targetAgent),
                phoneTaskReturn = PhoneTaskReturnUi(
                    runId = "phone-run-1",
                    agentId = targetAgent.id,
                    conversationId = "phone-conversation-1",
                ),
                chatsByAgent = mapOf(
                    targetAgent.id to AgentChatUi(
                        conversationId = "phone-conversation-1",
                        title = "Powrót z telefonu",
                    ),
                ),
            ),
        )
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = workspace,
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onOpenConversation = { _, _, _ -> conversationOpenRequested = true },
                )
            }
        }

        compose.onNodeWithTag("agent-tab-${targetAgent.id}").assertIsSelected()
        compose.onNodeWithTag("conversation-title").assertTextContains("Powrót z telefonu")
        compose.onNodeWithTag("chat-composer").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!conversationOpenRequested) }
        compose.onNodeWithTag("chat-home").performClick()
        compose.runOnIdle {
            workspace = workspace.copy(
                agents = listOf(targetAgent.copy(subtitle = "Odświeżony opis")),
            )
        }
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
        compose.onNodeWithTag("chat-composer").assertDoesNotExist()
    }

    @Test
    fun workspaceRefreshDoesNotOverrideAgentChosenAfterPhoneReturn() {
        val atlas = starterAgentsUi().first { it.id == "starter-atlas" }
        val nova = starterAgentsUi().first { it.id == "starter-nova" }
        var workspace by mutableStateOf(
            WorkspaceUiState(
                agents = listOf(atlas, nova),
                phoneTaskReturn = PhoneTaskReturnUi(
                    runId = "phone-run-selection",
                    agentId = nova.id,
                    conversationId = "phone-conversation-selection",
                ),
            ),
        )
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = workspace,
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithTag("agent-tab-${nova.id}").assertIsSelected()
        compose.onNodeWithTag("agent-tab-${atlas.id}").performClick().assertIsSelected()
        compose.runOnIdle {
            workspace = workspace.copy(
                agents = listOf(
                    atlas.copy(subtitle = "Odświeżony Atlas"),
                    nova.copy(subtitle = "Odświeżona Nova"),
                ),
            )
        }
        compose.onNodeWithTag("agent-tab-${atlas.id}").assertIsSelected()
    }

    @Test
    fun createdAgentStateOpensItsRunningFirstTask() {
        var submitted: CreateAgentTaskDraft? = null
        var workspace by mutableStateOf(WorkspaceUiState())
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = workspace,
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = { draft ->
                        submitted = draft
                        val agent = AgentSummaryUi(
                            id = "agent-created-test",
                            name = draft.agentName,
                            initial = "O",
                            subtitle = "Własny agent",
                        )
                        workspace = workspace.copy(
                            agents = workspace.agents + agent,
                            chatsByAgent = workspace.chatsByAgent + (
                                agent.id to AgentChatUi(
                                    messages = listOf(
                                        ChatMessageUi(
                                            id = "local-first-task",
                                            role = ChatMessageRole.USER,
                                            text = draft.taskPrompt,
                                        ),
                                    ),
                                    isRunning = true,
                                )
                            ),
                            createdAgentId = agent.id,
                        )
                    },
                )
            }
        }

        compose.onNodeWithText(text(R.string.new_agent_plus)).performScrollTo().performClick()
        compose.onNodeWithTag("agent-name-field").performTextInput("Orion")
        compose.onNodeWithTag("agent-role-field-create").performTextInput("Tworzy zwięzłe raporty dla właściciela")
        compose.onNodeWithTag("task-prompt-field").performTextInput("Przygotuj raport tygodniowy")
        compose.onNodeWithTag("submit-agent-task").performScrollTo().assertIsEnabled().performClick()

        compose.runOnIdle {
            assertEquals("Orion", submitted?.agentName)
            assertEquals("Tworzy zwięzłe raporty dla właściciela", submitted?.roleDescription)
            assertEquals("Przygotuj raport tygodniowy", submitted?.taskPrompt)
        }
        compose.onNodeWithTag("agent-tab-agent-created-test").assertIsSelected()
        compose.onNodeWithText("Przygotuj raport tygodniowy").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.agent_is_replying, "Orion")).assertIsDisplayed()
    }

    @Test
    fun agentCanBeCreatedWithoutFirstTask() {
        var submitted: CreateAgentTaskDraft? = null
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = { submitted = it },
                )
            }
        }

        compose.onNodeWithText(text(R.string.new_agent_plus)).performScrollTo().performClick()
        compose.onNodeWithTag("agent-name-field").performTextInput("Orion")
        compose.onNodeWithTag("submit-agent-task")
            .assertTextContains(text(R.string.create_agent))
            .assertIsEnabled()
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        compose.runOnIdle {
            assertEquals("Orion", submitted?.agentName)
            assertEquals("", submitted?.roleDescription)
            assertEquals("", submitted?.taskPrompt)
        }
    }

    @Test
    fun emptyChatStarterOnlyFillsDraftAndShowsAtMostFourSuggestions() {
        val starters = listOf(
            "Podsumuj mój dzień",
            "Przygotuj plan tygodnia",
            "Sprawdź ostatnie wyniki",
            "Pomóż mi zacząć",
            "Ta propozycja ma być ukryta",
        )
        val agent = starterAgentsUi().first().copy(conversationStarters = starters)
        var sendCount = 0
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = WorkspaceUiState(agents = listOf(agent)),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onSendTask = { _, _ -> sendCount += 1 },
                )
            }
        }

        compose.onNodeWithTag("home-agent-${agent.id}").performClick()
        compose.onNodeWithTag("conversation-starter-0").performScrollTo().performClick()
        compose.onNodeWithTag("chat-composer").assertTextContains(starters.first())
        compose.onNodeWithTag("conversation-starter-3").assertExists()
        compose.onNodeWithTag("conversation-starter-4").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, sendCount) }
    }

    @Test
    fun ownerProfileUsesOneFreeTextFieldAndSavesItsContent() {
        var savedProfile: String? = null
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = WorkspaceUiState(ownerProfile = ""),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onSaveOwnerProfile = { savedProfile = it },
                )
            }
        }

        compose.onNodeWithTag("owner-profile-open").performClick()
        compose.onNodeWithText(text(R.string.owner_profile)).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithTag("owner-profile-text")
            .performTextInput("Mam na imię Alicja, projektuję aplikacje i interesuję się fotografią.")
        compose.onNodeWithTag("owner-profile-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                "Mam na imię Alicja, projektuję aplikacje i interesuję się fotografią.",
                savedProfile,
            )
        }
    }

    @Test
    fun disabledBridgeGuidesUserThroughSingleManualStep() {
        var configured = false
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = SetupFacts(
                        termuxInstalled = true,
                        runCommandPermissionGranted = true,
                        bridgeState = BridgeState.DISABLED,
                    ),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = { configured = true },
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.setup_title_bridge)).assertIsDisplayed()
        compose.onNodeWithTag("termux-access-guide").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.termux_step_paste)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.copy_step_open_termux)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(configured) }
    }

    @Test
    fun mobileNavigationShowsEitherChatOrBotList() {
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithText("Atlas").performClick()
        compose.onNodeWithTag("agent-tab-starter-atlas").assertIsSelected()
        compose.onNodeWithTag("chat-composer").assertIsDisplayed()
        compose.onNodeWithTag("conversation-list-toggle").performClick()
        compose.onNodeWithTag("conversation-list-screen").assertIsDisplayed()
        compose.onNodeWithTag("chat-composer").assertIsNotDisplayed()
        compose.onNodeWithTag("close-conversation-list").performClick()
        compose.onNodeWithTag("chat-composer").assertIsDisplayed()
        compose.onNodeWithTag("conversation-list-screen").assertIsNotDisplayed()
    }

    @Test
    fun conversationScreenBringsSelectedLastAgentIntoView() {
        val agents = starterAgentsUi()
        val selected = agents.last()
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = WorkspaceUiState(agents = agents),
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithTag("home-agent-${selected.id}").performScrollTo().performClick()
        compose.onNodeWithTag("conversation-list-toggle").performClick()
        compose.onNodeWithTag("agent-tab-${selected.id}")
            .assertIsSelected()
            .assertIsDisplayed()
    }

    @Test
    fun conversationListShowsFiveThenLoadsMoreWithoutAgentDuplicationOrCompletedTasks() {
        val agent = starterAgentsUi().first { it.id == "starter-atlas" }
        val conversations = (1..7).map { index ->
            RecentConversationUi(
                id = "conversation-$index",
                agent = agent,
                title = "Rozmowa $index",
                lastMessage = "Krótki podgląd rozmowy numer $index",
                updatedAtLabel = "$index min",
            )
        }
        val loadRequests = mutableListOf<Pair<String, Boolean>>()
        var openedConversationId: String? = null
        var workspace by mutableStateOf(
            WorkspaceUiState(
                agents = listOf(agent),
                conversationPagesByAgent = mapOf(
                    agent.id to AgentConversationsUi(
                        items = conversations.take(5),
                        hasMore = true,
                        loaded = true,
                    ),
                ),
            ),
        )
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = workspace,
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onLoadAgentConversations = { agentId, loadMore ->
                        loadRequests += agentId to loadMore
                        if (loadMore) {
                            workspace = workspace.copy(
                                conversationPagesByAgent = mapOf(
                                    agentId to AgentConversationsUi(
                                        items = conversations,
                                        hasMore = false,
                                        loaded = true,
                                    ),
                                ),
                            )
                        }
                    },
                    onOpenConversation = { _, conversationId, _ -> openedConversationId = conversationId },
                )
            }
        }

        compose.onNodeWithTag("home-agent-${agent.id}").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("conversation-list-toggle").performClick()
        compose.onNodeWithTag("recent-conversation-conversation-5").assertExists()
        compose.onNodeWithTag("recent-conversation-conversation-6").assertDoesNotExist()
        compose.onAllNodesWithText(agent.name).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.recently_completed).uppercase()).assertDoesNotExist()
        compose.onNodeWithTag("load-more-conversations").performScrollTo().performClick()
        compose.onNodeWithTag("recent-conversation-conversation-6").performScrollTo().assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertTrue(loadRequests.contains(agent.id to false))
            assertTrue(loadRequests.contains(agent.id to true))
            assertEquals("conversation-6", openedConversationId)
        }
    }

    @Test
    fun firstConversationPageFailureShowsRetryInsteadOfEndlessProgress() {
        val agent = starterAgentsUi().first { it.id == "starter-atlas" }
        val loadRequests = mutableListOf<Pair<String, Boolean>>()
        val workspace = WorkspaceUiState(
            agents = listOf(agent),
            conversationPagesByAgent = mapOf(
                agent.id to AgentConversationsUi(
                    errorMessage = "Nie udało się pobrać rozmów.",
                    loaded = false,
                ),
            ),
        )
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = workspace,
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onLoadAgentConversations = { agentId, loadMore -> loadRequests += agentId to loadMore },
                )
            }
        }

        compose.onNodeWithTag("home-agent-${agent.id}").performClick()
        compose.onNodeWithTag("conversation-list-toggle").performClick()
        compose.onNodeWithTag("conversation-list-error").assertIsDisplayed()
        val requestsBeforeRetry = loadRequests.size
        compose.onNodeWithTag("retry-conversations").performClick()

        compose.runOnIdle {
            assertEquals(requestsBeforeRetry + 1, loadRequests.size)
            assertEquals(agent.id to false, loadRequests.last())
        }
    }

    @Test
    fun renameAgentKeepsItsIdAndShowsThePersistedName() {
        val stableAgentId = "starter-atlas"
        var renamedAgentId: String? = null
        var renamedName: String? = null
        var workspace by mutableStateOf(
            WorkspaceUiState(agents = starterAgentsUi().filter { it.id == stableAgentId }),
        )
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = workspace,
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onRenameAgent = { agentId, name ->
                        renamedAgentId = agentId
                        renamedName = name
                        workspace = workspace.copy(
                            agents = workspace.agents.map { agent ->
                                if (agent.id == agentId) agent.copy(name = name, initial = "A") else agent
                            },
                            nameSavingAgentId = null,
                            nameSavedAgentId = agentId,
                            nameError = null,
                        )
                    },
                )
            }
        }

        compose.onNodeWithText("Atlas").performClick()
        compose.onNodeWithTag("conversation-list-toggle").performClick()
        compose.onNodeWithTag("open-agent-name").performScrollTo().performClick()
        compose.onNodeWithTag("agent-rename-screen").assertIsDisplayed()
        compose.onNodeWithTag("agent-rename-field").performTextReplacement("Aurora")
        compose.onNodeWithTag("save-agent-name").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(stableAgentId, renamedAgentId)
            assertEquals("Aurora", renamedName)
            assertEquals(stableAgentId, workspace.agents.single().id)
        }
        compose.onNodeWithTag("agent-name-saved").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("close-agent-name").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.change_look)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun agentRoleEditorSavesAndShowsReadBackValue() {
        val agentId = "starter-atlas"
        var savedAgentId: String? = null
        var savedRole: String? = null
        var workspace by mutableStateOf(
            WorkspaceUiState(
                agents = starterAgentsUi().filter { it.id == agentId }
                    .map { it.copy(roleDescription = "Pomaga w researchu") },
            ),
        )
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = readyFacts(),
                    workspace = workspace,
                    onRefresh = {},
                    onInstallTermux = {},
                    onRequestPermission = {},
                    onOpenTermux = {},
                    onConfigureTermux = {},
                    onBootstrap = {},
                    onLogin = {},
                    onCreateAgentTask = {},
                    onSaveAgentRole = { requestedAgentId, role ->
                        savedAgentId = requestedAgentId
                        savedRole = role
                        workspace = workspace.copy(
                            agents = workspace.agents.map {
                                if (it.id == requestedAgentId) it.copy(roleDescription = role) else it
                            },
                            roleSavingAgentId = null,
                            roleSavedAgentId = requestedAgentId,
                            roleError = null,
                        )
                    },
                )
            }
        }

        compose.onNodeWithTag("home-agent-$agentId").performClick()
        compose.onNodeWithTag("conversation-list-toggle").performClick()
        compose.onNodeWithTag("open-agent-role").performScrollTo().performClick()
        compose.onNodeWithTag("agent-role-screen").assertIsDisplayed()
        compose.onNodeWithTag("agent-role-field").performTextReplacement("Porównuje źródła i wskazuje różnice")
        compose.onNodeWithTag("save-agent-role").performClick()

        compose.runOnIdle {
            assertEquals(agentId, savedAgentId)
            assertEquals("Porównuje źródła i wskazuje różnice", savedRole)
        }
        compose.onNodeWithTag("agent-role-saved").assertIsDisplayed()
    }

    private fun setSetupContent(
        facts: SetupFacts,
        onRefresh: () -> Unit = {},
        onRequestPermission: () -> Unit = {},
        onOpenTermux: () -> Unit = {},
        onBootstrap: () -> Unit = {},
        onLogin: () -> Unit = {},
    ) {
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    phoneAutomationOnboardingSkipped = true,
                    facts = facts,
                    onRefresh = onRefresh,
                    onInstallTermux = {},
                    onRequestPermission = onRequestPermission,
                    onOpenTermux = onOpenTermux,
                    onConfigureTermux = {},
                    onBootstrap = onBootstrap,
                    onLogin = onLogin,
                    onCreateAgentTask = {},
                )
            }
        }
    }

    @Test
    fun optionalPhoneGuideSkipAndReentry() {
        var skipped = false
        var skippedStops by mutableStateOf<Set<String>>(emptySet())
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    facts = readyFacts(),
                    onRefresh = {}, onInstallTermux = {}, onRequestPermission = {},
                    onOpenTermux = {}, onConfigureTermux = {}, onBootstrap = {}, onLogin = {},
                    onCreateAgentTask = {}, onSkipPhoneAutomationOnboarding = { skipped = true },
                    skippedOnboardingStops = skippedStops,
                    onSkipOnboardingStop = { skippedStops = skippedStops + it },
                )
            }
        }
        compose.onNodeWithTag("trail-screen").assertIsDisplayed()
        compose.onNodeWithTag("trail-stop-phone").performScrollTo().performClick()
        compose.onNodeWithTag("trail-open-details").performScrollTo().performClick()
        compose.onNodeWithTag("trail-skip-phone").performScrollTo().performClick()
        compose.runOnIdle { assertTrue("phone" in skippedStops); assertTrue(!skipped) }
        compose.onNodeWithTag("trail-continue").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(skipped) }
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
        compose.onNodeWithTag("home-settings-open").performScrollTo().performClick()
        compose.onNodeWithTag("home-settings-advanced").performClick()
        compose.onNodeWithTag("trail-screen").assertIsDisplayed()
    }

    @Test
    fun optionalPhoneGuideRequiresConnection() {
        var connected by mutableStateOf(false)
        var opened = false
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    facts = readyFacts(), phoneAutomationEnabled = true,
                    phoneAutomationConnected = connected,
                    onRefresh = {}, onInstallTermux = {}, onRequestPermission = {},
                    onOpenTermux = {}, onConfigureTermux = {}, onBootstrap = {}, onLogin = {},
                    onCreateAgentTask = {}, onOpenPhoneAutomationSettings = { opened = true },
                )
            }
        }
        compose.onNodeWithTag("trail-screen").assertIsDisplayed()
        compose.onNodeWithTag("trail-status-phone", useUnmergedTree = true).performScrollTo().assertContentDescriptionContains(text(R.string.optional))
        compose.runOnIdle { connected = true }
        compose.onNodeWithTag("trail-status-phone", useUnmergedTree = true).performScrollTo().assertContentDescriptionContains(text(R.string.done))
        compose.runOnIdle { connected = false }
        compose.onNodeWithTag("trail-status-phone", useUnmergedTree = true).performScrollTo().assertContentDescriptionContains(text(R.string.optional))
    }

    @Test
    fun trailSkillsUsesEditorAndUpdatedWorkspace() {
        var workspace by mutableStateOf(WorkspaceUiState(
            snapshotLoaded = true,
            skillDefinitions = listOf(SkillDefinitionUi("research", "Research", "Wyszukiwanie", true)),
        ))
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    facts = readyFacts(), workspace = workspace,
                    onRefresh = {}, onInstallTermux = {}, onRequestPermission = {},
                    onOpenTermux = {}, onConfigureTermux = {}, onBootstrap = {}, onLogin = {},
                    onCreateAgentTask = {},
                    onSaveAgentSkills = { id, skills ->
                        workspace = workspace.copy(agents = workspace.agents.map {
                            if (it.id == id) it.copy(assignedSkillIds = skills) else it
                        })
                    },
                )
            }
        }
        compose.onNodeWithTag("trail-stop-skills").performScrollTo().performClick()
        compose.onNodeWithTag("trail-open-details").performScrollTo().performClick()
        compose.onNodeWithTag("trail-action-skills").performScrollTo().performClick()
        compose.onNodeWithTag("agent-skills-screen").assertIsDisplayed()
        compose.onNodeWithTag("skill-option-research").performScrollTo().performClick()
        compose.onNodeWithTag("save-agent-skills").assertIsDisplayed().performClick()
        compose.onNodeWithTag("close-agent-skills").performClick()
        compose.onNodeWithTag("trail-status-skills", useUnmergedTree = true).performScrollTo().assertContentDescriptionContains(text(R.string.done))
    }

    @Test
    fun trailMissionOnlyDraftsUntilUserSends() {
        var sent = false
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    facts = readyFacts(),
                    onRefresh = {}, onInstallTermux = {}, onRequestPermission = {},
                    onOpenTermux = {}, onConfigureTermux = {}, onBootstrap = {}, onLogin = {},
                    onCreateAgentTask = {}, onSendTask = { _, _ -> sent = true },
                )
            }
        }
        compose.onNodeWithTag("trail-stop-mission").performScrollTo().performClick()
        compose.onNodeWithTag("trail-open-details").performScrollTo().performClick()
        compose.onNodeWithTag("trail-action-mission").performScrollTo().performClick()
        compose.onNodeWithTag("chat-composer").assertTextContains(text(R.string.mission_prompt))
        compose.runOnIdle { assertTrue(!sent) }
        compose.onNodeWithTag("send-task").performClick()
        compose.runOnIdle { assertTrue(sent) }
    }

    @Test
    fun trailDetailsKeepPathPositionAndCloseWithoutAction() {
        var openedSettings = false
        compose.setContent {
            MobileBotTheme {
                MobileBotApp(
                    facts = readyFacts(),
                    onRefresh = {}, onInstallTermux = {}, onRequestPermission = {},
                    onOpenTermux = {}, onConfigureTermux = {}, onBootstrap = {}, onLogin = {},
                    onCreateAgentTask = {}, onOpenPhoneAutomationSettings = { openedSettings = true },
                )
            }
        }
        val phone = compose.onNodeWithTag("trail-stop-phone")
        phone.performScrollTo()
        val beforeSelection = phone.fetchSemanticsNode().boundsInRoot
        phone.performClick()
        compose.waitForIdle()
        assertEquals(beforeSelection, phone.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("trail-open-details").performScrollTo()
        val beforePanel = phone.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("trail-open-details").performClick()
        compose.onNodeWithTag("trail-details").assertIsDisplayed()
        compose.onNodeWithTag("trail-phone-show-steps").performScrollTo().performClick()
        compose.onNodeWithTag("trail-action-phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("trail-close-details").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(beforePanel, phone.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertTrue(!openedSettings) }
    }

    @Test
    fun profileOffersFourGraphicThemesAndAppliesSelection() {
        var theme by mutableStateOf(GraphicTheme.HEROES)
        compose.setContent {
            MobileBotTheme(graphicTheme = theme) {
                MobileBotApp(
                    facts = readyFacts(),
                    phoneAutomationOnboardingSkipped = true,
                    graphicTheme = theme,
                    onGraphicThemeChange = { theme = it },
                    onRefresh = {}, onInstallTermux = {}, onRequestPermission = {},
                    onOpenTermux = {}, onConfigureTermux = {}, onBootstrap = {}, onLogin = {},
                    onCreateAgentTask = {},
                )
            }
        }

        compose.onNodeWithTag("owner-profile-open").performClick()
        GraphicTheme.entries.forEach { entry ->
            compose.onNodeWithTag("graphic-theme-${entry.storedId}").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("graphic-theme-influencers").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(GraphicTheme.INFLUENCERS, theme) }
        compose.onNodeWithText(text(R.string.selected_check)).assertIsDisplayed()
    }

    @Test
    fun newAgentUsesOneOfFifteenThemeAvatars() {
        var submitted: CreateAgentTaskDraft? = null
        compose.setContent {
            MobileBotTheme(graphicTheme = GraphicTheme.ANIMALS) {
                MobileBotApp(
                    facts = readyFacts(),
                    phoneAutomationOnboardingSkipped = true,
                    graphicTheme = GraphicTheme.ANIMALS,
                    onRefresh = {}, onInstallTermux = {}, onRequestPermission = {},
                    onOpenTermux = {}, onConfigureTermux = {}, onBootstrap = {}, onLogin = {},
                    onCreateAgentTask = { submitted = it },
                )
            }
        }

        compose.onNodeWithText(text(R.string.new_agent_plus)).performScrollTo().performClick()
        (1..15).forEach { index ->
            compose.onNodeWithTag("avatar-choice-$index").performScrollTo().assertExists()
        }
        compose.onNodeWithTag("avatar-choice-12").performScrollTo().performClick()
        compose.onNodeWithTag("agent-name-field").performTextInput("Orion")
        compose.onNodeWithTag("submit-agent-task").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(12, submitted?.avatarIndex) }
    }

    private fun readyFacts() = SetupFacts(
        termuxInstalled = true,
        runCommandPermissionGranted = true,
        bridgeState = BridgeState.ENABLED,
        hostStatus = HostStatus(
            reachable = true,
            protocolVersion = TermuxContract.PROTOCOL_VERSION,
            environmentRevision = TermuxContract.ENVIRONMENT_REVISION,
            hostVersion = TermuxContract.HOST_VERSION,
            codexInstalled = true,
            codexAuthenticated = true,
        ),
    )
}

private fun text(id: Int, vararg args: Any): String =
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
