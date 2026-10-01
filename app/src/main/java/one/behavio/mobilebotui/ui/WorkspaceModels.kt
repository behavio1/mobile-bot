package one.behavio.mobilebotui.ui

data class AgentSummaryUi(
    val id: String,
    val name: String,
    val initial: String,
    val subtitle: String,
    val assignedSkillIds: List<String> = emptyList(),
    val roleDescription: String = "",
    val conversationStarters: List<String> = emptyList(),
    val avatarIndex: Int = 1,
    val conversationCount: Int? = null,
)

data class SkillDefinitionUi(
    val id: String,
    val name: String,
    val summary: String,
    val assignable: Boolean,
)

data class RecentConversationUi(
    val id: String,
    val agent: AgentSummaryUi,
    val title: String,
    val lastMessage: String,
    val updatedAtLabel: String,
)

data class AgentConversationsUi(
    val items: List<RecentConversationUi> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loaded: Boolean = false,
)

data class RecentTaskUi(
    val id: String,
    val conversationId: String,
    val agent: AgentSummaryUi,
    val title: String,
    val resultSummary: String,
    val completedAtLabel: String,
)

data class AutomationUi(
    val id: String,
    val conversationId: String,
    val agent: AgentSummaryUi,
    val name: String,
    val intervalMinutes: Int,
    val statusLabel: String,
    val nextRunAtLabel: String,
    val lastSummary: String?,
    val scheduledAtLabel: String?,
    val startedAtLabel: String?,
    val delayLabel: String?,
    val waitingDurationLabel: String?,
    val deferredReasonLabel: String?,
    val skippedOccurrences: Int,
    val enabled: Boolean = true,
)

enum class ChatMessageRole { USER, AGENT }

data class ChatMessageUi(
    val id: String,
    val role: ChatMessageRole,
    val text: String,
    val timeLabel: String = "",
)

data class AgentChatUi(
    val conversationId: String? = null,
    val title: String = "",
    val messages: List<ChatMessageUi> = emptyList(),
    val isLoading: Boolean = false,
    val isRunning: Boolean = false,
    val errorMessage: String? = null,
)

data class PhoneTaskReturnUi(val runId: String, val agentId: String, val conversationId: String)

data class WorkspaceUiState(
    val deletingAgentId: String? = null,
    val deleteAgentError: String? = null,
    val customThemes: String = "[]",
    val phoneTaskReturn: PhoneTaskReturnUi? = null,
    val snapshotLoaded: Boolean = false,
    val ownerProfile: String = "",
    val ownerProfileSaving: Boolean = false,
    val ownerProfileSaved: Boolean = false,
    val ownerProfileError: String? = null,
    val agents: List<AgentSummaryUi> = starterAgentsUi(),
    val skillDefinitions: List<SkillDefinitionUi> = emptyList(),
    val recentConversations: List<RecentConversationUi> = emptyList(),
    val conversationPagesByAgent: Map<String, AgentConversationsUi> = emptyMap(),
    val recentCompletedTasks: List<RecentTaskUi> = emptyList(),
    val automations: List<AutomationUi> = emptyList(),
    val chatsByAgent: Map<String, AgentChatUi> = emptyMap(),
    val agentTaskCreating: Boolean = false,
    val agentTaskError: String? = null,
    val createdAgentId: String? = null,
    val skillsSavingAgentId: String? = null,
    val skillsSavedAgentId: String? = null,
    val skillsError: String? = null,
    val nameSavingAgentId: String? = null,
    val nameSavedAgentId: String? = null,
    val nameError: String? = null,
    val roleSavingAgentId: String? = null,
    val roleSavedAgentId: String? = null,
    val roleError: String? = null,
    val automationSavingIds: Set<String> = emptySet(),
    val automationErrors: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
)

data class CreateAgentTaskDraft(
    val agentName: String,
    val taskPrompt: String,
    val roleDescription: String = "",
    val avatarIndex: Int = 1,
)

fun starterAgentsUi(): List<AgentSummaryUi> = listOf(
    AgentSummaryUi(
        id = "starter-atlas",
        name = "Atlas",
        initial = "A",
        subtitle = "",
    ),
    AgentSummaryUi(
        id = "starter-nova",
        name = "Nova",
        initial = "N",
        subtitle = "",
    ),
    AgentSummaryUi(
        id = "starter-echo",
        name = "Echo",
        initial = "E",
        subtitle = "",
    ),
    AgentSummaryUi(
        id = "agent-mail",
        name = "Mail",
        initial = "@",
        subtitle = "",
    ),
    AgentSummaryUi(
        id = "agent-shopping",
        name = "Shopping",
        initial = "S",
        subtitle = "",
    ),
)
