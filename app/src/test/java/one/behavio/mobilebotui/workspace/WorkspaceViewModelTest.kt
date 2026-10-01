package one.behavio.mobilebotui.workspace

import one.behavio.mobilebotui.ui.AgentChatUi
import one.behavio.mobilebotui.ui.AgentSummaryUi
import one.behavio.mobilebotui.ui.RecentConversationUi
import one.behavio.mobilebotui.ui.WorkspaceUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceViewModelTest {
    @Test
    fun `returned conversation title uses hydrated snapshot instead of stale local fallback`() {
        val agent = AgentSummaryUi(
            id = "agent-programista",
            name = "Programista",
            initial = "P",
            subtitle = "Tworzenie aplikacji",
        )
        val state = WorkspaceUiState(
            agents = listOf(agent),
            recentConversations = listOf(
                RecentConversationUi(
                    id = "conversation-phone-task",
                    agent = agent,
                    title = "Zbuduj aplikację zegara",
                    lastMessage = "Gotowe",
                    updatedAtLabel = "Teraz",
                ),
            ),
            chatsByAgent = mapOf(
                agent.id to AgentChatUi(
                    conversationId = "conversation-phone-task",
                    title = "Rozmowa",
                ),
            ),
        )

        assertEquals(
            "Zbuduj aplikację zegara",
            returnedConversationTitle(state, agent.id, "conversation-phone-task", "Conversation"),
        )
    }
}
