package one.behavio.mobilebotui.workspace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import one.behavio.mobilebotui.automation.ActionCorrelation
import one.behavio.mobilebotui.automation.DevelopmentActionLogger
import one.behavio.mobilebotui.automation.DevelopmentActions
import one.behavio.mobilebotui.ui.AgentChatUi
import one.behavio.mobilebotui.ui.AgentConversationsUi
import one.behavio.mobilebotui.ui.AgentSummaryUi
import one.behavio.mobilebotui.ui.AutomationUi
import one.behavio.mobilebotui.ui.ChatMessageRole
import one.behavio.mobilebotui.ui.ChatMessageUi
import one.behavio.mobilebotui.ui.CreateAgentTaskDraft
import one.behavio.mobilebotui.ui.RecentConversationUi
import one.behavio.mobilebotui.ui.RecentTaskUi
import one.behavio.mobilebotui.ui.SkillDefinitionUi
import one.behavio.mobilebotui.ui.WorkspaceUiState
import one.behavio.mobilebotui.ui.VisualPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.annotation.StringRes
import one.behavio.mobilebotui.R

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private fun text(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
    private val actionLog = DevelopmentActionLogger.get(application)
    private val client = WorkspaceClient(
        actionLog = actionLog,
    )
    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()
    private var starterRefreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { client.workspace() }
                .onSuccess(::applySnapshot)
                .onFailure { _state.update { it.copy(loading = false) } }
        }
    }

    fun returnFromPhoneTask(runId: String, agentId: String, conversationId: String) {
        if (_state.value.phoneTaskReturn?.runId == runId) return
        val chat = _state.value.chatsByAgent[agentId]
        _state.update { it.copy(phoneTaskReturn = one.behavio.mobilebotui.ui.PhoneTaskReturnUi(runId, agentId, conversationId)) }
        // An in-flight HTTP run delivers its own result after the bridge confirms the return.
        // Do not race that response with an extra readback when this Activity owns the request.
        if (chat?.isRunning == true) return

        // A cold Activity starts with an empty snapshot. Hydrate it before resolving the title,
        // otherwise the conversation remains permanently labelled with the fallback "Rozmowa".
        viewModelScope.launch {
            runCatching { client.workspace() }.onSuccess(::applySnapshot)
            openConversation(
                agentId = agentId,
                conversationId = conversationId,
                title = returnedConversationTitle(_state.value, agentId, conversationId, text(R.string.conversation_fallback_title)),
            )
        }
    }

    fun selectAgent(agentId: String, newConversation: Boolean = false) {
        refreshMissingStarters(agentId)
        _state.update { current ->
            if (!newConversation && current.chatsByAgent.containsKey(agentId)) current
            else current.copy(
                chatsByAgent = current.chatsByAgent + (agentId to AgentChatUi()),
            )
        }
    }

    private fun refreshMissingStarters(agentId: String) {
        starterRefreshJob?.cancel()
        if (_state.value.agents.firstOrNull { it.id == agentId }?.conversationStarters?.size == 4) return
        starterRefreshJob = viewModelScope.launch {
            // Existing agents are filled once by the Host after upgrade. Stop polling
            // when the suggestions arrive or the bounded initialization window ends.
            repeat(40) {
                delay(3_000)
                if (_state.value.chatsByAgent[agentId]?.messages?.isNotEmpty() == true) return@launch
                runCatching { client.workspace() }.onSuccess(::applySnapshot)
                if (_state.value.agents.firstOrNull { it.id == agentId }?.conversationStarters?.size == 4) return@launch
            }
        }
    }

    fun loadAgentConversations(agentId: String, loadMore: Boolean = false) {
        val existing = _state.value.conversationPagesByAgent[agentId] ?: AgentConversationsUi()
        if (existing.isLoading || (loadMore && !existing.hasMore)) return
        val offset = if (loadMore) existing.items.size else 0
        _state.update { current ->
            current.copy(conversationPagesByAgent = current.conversationPagesByAgent +
                (agentId to existing.copy(isLoading = true, errorMessage = null)))
        }
        viewModelScope.launch {
            runCatching { client.agentConversations(agentId, offset) }
                .onSuccess { page ->
                    _state.update { current ->
                        val agent = current.agents.firstOrNull { it.id == agentId } ?: return@update current
                        val incoming = page.conversations.map {
                            RecentConversationUi(it.id, agent, it.title, it.lastMessage.toDisplayText(), friendlyDate(it.updatedAt))
                        }
                        val items = if (loadMore) (existing.items + incoming).distinctBy { it.id } else incoming
                        current.copy(conversationPagesByAgent = current.conversationPagesByAgent +
                            (agentId to AgentConversationsUi(items = items, hasMore = page.hasMore, loaded = true)))
                    }
                }.onFailure {
                    _state.update { current ->
                        current.copy(conversationPagesByAgent = current.conversationPagesByAgent +
                            (agentId to existing.copy(isLoading = false, errorMessage = text(R.string.conversations_load_failed))))
                    }
                }
        }
    }

    fun saveOwnerProfile(about: String, traceId: String? = null) {
        val normalized = about.trim()
        if (normalized.length > 2_000 || _state.value.ownerProfileSaving) return
        _state.update {
            it.copy(ownerProfileSaving = true, ownerProfileSaved = false, ownerProfileError = null)
        }
        viewModelScope.launch {
            runCatching { client.updateOwnerProfile(normalized, traceId) }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(
                            ownerProfile = saved,
                            ownerProfileSaving = false,
                            ownerProfileSaved = true,
                            ownerProfileError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            ownerProfileSaving = false,
                            ownerProfileSaved = false,
                            ownerProfileError = error.toUserMessage(),
                        )
                    }
                }
        }
    }

    fun createAgentAndSend(draft: CreateAgentTaskDraft, traceId: String? = null) {
        val name = draft.agentName.trim()
        val prompt = draft.taskPrompt.trim()
        if (name.isEmpty() || _state.value.agentTaskCreating) return
        if (name.length > 120) {
            _state.update { it.copy(agentTaskError = text(R.string.agent_name_too_long)) }
            return
        }
        if (draft.roleDescription.trim().length > 8_000) {
            _state.update { it.copy(agentTaskError = text(R.string.agent_role_too_long)) }
            return
        }
        if (prompt.length > 32_000) {
            _state.update { it.copy(agentTaskError = text(R.string.agent_first_task_too_long)) }
            return
        }
        _state.update {
            it.copy(agentTaskCreating = true, agentTaskError = null, createdAgentId = null)
        }
        viewModelScope.launch {
            runCatching { client.createAgent(name, traceId, draft.roleDescription) }
                .onSuccess { agent ->
                    VisualPreferences.saveAvatarIndex(getApplication(), agent.id, draft.avatarIndex)
                    val agentUi = AgentSummaryUi(
                        agent.id,
                        agent.name,
                        agent.initial,
                        agent.subtitle,
                        agent.assignedSkillIds,
                        agent.roleDescription,
                        agent.conversationStarters,
                        draft.avatarIndex,
                    )
                    _state.update { current ->
                        current.copy(
                            agents = current.agents.filterNot { it.id == agent.id } + agentUi,
                            chatsByAgent = current.chatsByAgent + (agent.id to AgentChatUi()),
                            agentTaskCreating = false,
                            agentTaskError = null,
                            createdAgentId = agent.id,
                        )
                    }
                    actionLog.record(
                        DevelopmentActions.AGENT_CREATED,
                        mapOf("agentId" to agent.id, "nameLength" to agent.name.length),
                        ActionCorrelation(traceId = traceId ?: UUID.randomUUID().toString()),
                    )
                    if (prompt.isNotEmpty()) send(agent.id, prompt, traceId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            agentTaskCreating = false,
                            agentTaskError = error.toUserMessage(),
                            createdAgentId = null,
                        )
                    }
                }
        }
    }

    fun saveAgentSkills(agentId: String, skillIds: List<String>, traceId: String? = null) {
        if (_state.value.skillsSavingAgentId != null) return
        val allowedIds = _state.value.skillDefinitions
            .filter { it.assignable }
            .mapTo(mutableSetOf()) { it.id }
        if (skillIds.size != skillIds.distinct().size || skillIds.any { it !in allowedIds }) {
            _state.update { it.copy(skillsError = text(R.string.skill_unavailable_selected), skillsSavedAgentId = null) }
            return
        }
        _state.update {
            it.copy(skillsSavingAgentId = agentId, skillsSavedAgentId = null, skillsError = null)
        }
        viewModelScope.launch {
            runCatching {
                client.updateAgentSkills(agentId, skillIds, traceId)
                client.workspace(traceId)
            }.onSuccess { snapshot ->
                applySnapshot(snapshot)
                _state.update {
                    it.copy(skillsSavingAgentId = null, skillsSavedAgentId = agentId, skillsError = null)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        skillsSavingAgentId = null,
                        skillsSavedAgentId = null,
                        skillsError = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun saveAgentRole(agentId: String, requestedRole: String, traceId: String? = null) {
        if (_state.value.roleSavingAgentId != null) return
        val role = requestedRole.trim()
        if (role.isEmpty() || role.length > 8_000) {
            _state.update { it.copy(roleError = text(R.string.agent_role_limit), roleSavedAgentId = null) }
            return
        }
        _state.update { it.copy(roleSavingAgentId = agentId, roleSavedAgentId = null, roleError = null) }
        viewModelScope.launch {
            runCatching {
                client.updateAgentRole(agentId, role, traceId)
                client.workspace(traceId)
            }.onSuccess { snapshot ->
                applySnapshot(snapshot)
                _state.update { it.copy(roleSavingAgentId = null, roleSavedAgentId = agentId, roleError = null) }
            }.onFailure { error ->
                _state.update { it.copy(roleSavingAgentId = null, roleSavedAgentId = null, roleError = error.toUserMessage()) }
            }
        }
    }

    fun renameAgent(agentId: String, requestedName: String, traceId: String? = null, avatarIndex: Int? = null) {
        if (_state.value.nameSavingAgentId != null) return
        val name = requestedName.trim()
        if (name.isEmpty() || name.length > 120 || name.any { it.code < 32 || it.code == 127 }) {
            _state.update {
                it.copy(nameError = text(R.string.agent_name_invalid), nameSavedAgentId = null)
            }
            return
        }
        _state.update {
            it.copy(nameSavingAgentId = agentId, nameSavedAgentId = null, nameError = null)
        }
        viewModelScope.launch {
            runCatching {
                if (_state.value.agents.firstOrNull { it.id == agentId }?.name != name) {
                    client.renameAgent(agentId, name, traceId)
                }
                if (avatarIndex != null) {
                    withContext(Dispatchers.IO) {
                        check(VisualPreferences.saveAvatarIndex(getApplication(), agentId, avatarIndex)) { text(R.string.agent_photo_save_failed) }
                    }
                }
                client.workspace(traceId)
            }.onSuccess { snapshot ->
                applySnapshot(snapshot)
                _state.update {
                    it.copy(nameSavingAgentId = null, nameSavedAgentId = agentId, nameError = null)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        nameSavingAgentId = null,
                        nameSavedAgentId = null,
                        nameError = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun deleteAgent(agentId: String) {
        if (_state.value.deletingAgentId != null) return
        _state.update { it.copy(deletingAgentId = agentId, deleteAgentError = null) }
        viewModelScope.launch {
            val result = runCatching { client.deleteAgent(agentId) }
            val snapshot = runCatching { client.workspace() }.getOrNull()
            if (snapshot != null) applySnapshot(snapshot)
            val confirmed = result.isSuccess || snapshot?.agents?.none { it.id == agentId } == true
            _state.update { current ->
                if (confirmed) current.copy(
                    deletingAgentId = null, deleteAgentError = null,
                    agents = current.agents.filterNot { it.id == agentId },
                    chatsByAgent = current.chatsByAgent - agentId,
                    conversationPagesByAgent = current.conversationPagesByAgent - agentId,
                    automations = current.automations.filterNot { it.agent.id == agentId },
                    recentConversations = current.recentConversations.filterNot { it.agent.id == agentId },
                    recentCompletedTasks = current.recentCompletedTasks.filterNot { it.agent.id == agentId },
                ) else current.copy(deletingAgentId = null, deleteAgentError =
                    if ((result.exceptionOrNull() as? WorkspaceApiException)?.code == "agent_busy")
                        text(R.string.agent_delete_busy)
                    else text(R.string.agent_delete_failed))
            }
        }
    }

    fun setAutomationEnabled(automationId: String, enabled: Boolean, traceId: String? = null) {
        val current = _state.value
        val automation = current.automations.firstOrNull { it.id == automationId } ?: return
        if (automationId in current.automationSavingIds || automation.enabled == enabled) return
        _state.update {
            it.copy(
                automationSavingIds = it.automationSavingIds + automationId,
                automationErrors = it.automationErrors - automationId,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                client.updateAutomationEnabled(automationId, automation.agent.id, enabled, traceId)
                client.workspace(traceId)
            }
            result.onSuccess { snapshot ->
                applySnapshot(snapshot)
                _state.update {
                    it.copy(
                        automationSavingIds = it.automationSavingIds - automationId,
                        automationErrors = it.automationErrors - automationId,
                    )
                }
            }.onFailure { error ->
                val readback = runCatching { client.workspace(traceId) }.getOrNull()
                if (readback != null) applySnapshot(readback)
                val confirmed = readback?.automations
                    ?.firstOrNull { it.id == automationId }
                    ?.let { (it.status != "paused") == enabled } == true
                _state.update {
                    it.copy(
                        automationSavingIds = it.automationSavingIds - automationId,
                        automationErrors = if (confirmed) {
                            it.automationErrors - automationId
                        } else {
                            it.automationErrors + (automationId to error.toUserMessage())
                        },
                    )
                }
            }
        }
    }

    fun openConversation(agentId: String, conversationId: String, title: String, traceId: String? = null) {
        _state.update { current ->
            current.copy(
                chatsByAgent = current.chatsByAgent + (
                    agentId to AgentChatUi(
                        conversationId = conversationId,
                        title = title,
                        isLoading = true,
                    )
                ),
            )
        }
        viewModelScope.launch {
            runCatching { client.messages(agentId, conversationId, traceId) }
                .onSuccess { messages ->
                    _state.update { current ->
                        val chat = current.chatsByAgent[agentId] ?: AgentChatUi()
                        if (chat.conversationId != conversationId) return@update current
                        current.copy(
                            chatsByAgent = current.chatsByAgent + (
                                agentId to chat.copy(
                                    messages = messages.map(::toUiMessage),
                                    isLoading = false,
                                    errorMessage = null,
                                )
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { current ->
                        val chat = current.chatsByAgent[agentId] ?: AgentChatUi()
                        current.copy(
                            chatsByAgent = current.chatsByAgent + (
                                agentId to chat.copy(
                                    isLoading = false,
                                    errorMessage = error.toUserMessage(),
                                )
                            ),
                        )
                    }
                }
        }
    }

    fun send(agentId: String, prompt: String, traceId: String? = null) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty()) return
        val currentChat = _state.value.chatsByAgent[agentId] ?: AgentChatUi()
        if (currentChat.isRunning) return
        val userMessage = ChatMessageUi(
            id = "local-${UUID.randomUUID()}",
            role = ChatMessageRole.USER,
            text = trimmedPrompt,
            timeLabel = currentTimeLabel(),
        )
        _state.update { current ->
            val chat = current.chatsByAgent[agentId] ?: AgentChatUi()
            current.copy(
                chatsByAgent = current.chatsByAgent + (
                    agentId to chat.copy(
                        title = if (chat.conversationId == null && chat.messages.isEmpty()) {
                            trimmedPrompt.replace(Regex("\\s+"), " ").take(64)
                        } else {
                            chat.title
                        },
                        messages = chat.messages + userMessage,
                        isRunning = true,
                        errorMessage = null,
                    )
                ),
            )
        }

        viewModelScope.launch {
            runCatching { client.run(agentId, trimmedPrompt, currentChat.conversationId, traceId) }
                .onSuccess { result ->
                    _state.update { current ->
                        val chat = current.chatsByAgent[agentId] ?: AgentChatUi()
                        current.copy(
                            chatsByAgent = current.chatsByAgent + (
                                agentId to chat.copy(
                                    conversationId = result.conversationId,
                                    title = result.title,
                                    messages = chat.messages + ChatMessageUi(
                                        id = "run-${result.runId}",
                                        role = ChatMessageRole.AGENT,
                                        text = result.reply,
                                        timeLabel = currentTimeLabel(),
                                    ),
                                    isRunning = false,
                                    errorMessage = null,
                                )
                            ),
                        )
                    }
                    runCatching { client.workspace() }.onSuccess(::applySnapshot)
                }
                .onFailure { error ->
                    _state.update { current ->
                        val chat = current.chatsByAgent[agentId] ?: AgentChatUi()
                        current.copy(
                            chatsByAgent = current.chatsByAgent + (
                                agentId to chat.copy(
                                    isRunning = false,
                                    errorMessage = error.toUserMessage(),
                                )
                            ),
                        )
                    }
                }
        }
    }

    private fun applySnapshot(snapshot: WorkspaceSnapshot) {
        _state.update { current ->
            val agents = snapshot.agents.map {
                AgentSummaryUi(
                    it.id,
                    it.name,
                    it.initial,
                    it.subtitle,
                    it.assignedSkillIds,
                    it.roleDescription,
                    it.conversationStarters,
                    VisualPreferences.avatarIndex(getApplication(), it.id),
                    it.conversationCount,
                )
            }
            val agentsById = agents.associateBy { it.id }
            current.copy(
                snapshotLoaded = true,
                customThemes = snapshot.customThemes,
                ownerProfile = snapshot.ownerProfile,
                agents = agents,
                skillDefinitions = snapshot.skillDefinitions.map {
                    SkillDefinitionUi(it.id, it.name, it.summary, it.assignable)
                },
                recentConversations = snapshot.conversations.mapNotNull { conversation ->
                    val agent = agentsById[conversation.agentId] ?: return@mapNotNull null
                    RecentConversationUi(
                        id = conversation.id,
                        agent = agent,
                        title = conversation.title,
                        lastMessage = conversation.lastMessage.toDisplayText(),
                        updatedAtLabel = friendlyDate(conversation.updatedAt),
                    )
                },
                recentCompletedTasks = snapshot.completedTasks.mapNotNull { task ->
                    val agent = agentsById[task.agentId] ?: return@mapNotNull null
                    RecentTaskUi(
                        id = task.id,
                        conversationId = task.conversationId,
                        agent = agent,
                        title = task.title,
                        resultSummary = task.resultSummary.toDisplayText(),
                        completedAtLabel = friendlyDate(task.completedAt),
                    )
                },
                automations = snapshot.automations.mapNotNull { automation ->
                    val agent = agentsById[automation.agentId] ?: return@mapNotNull null
                    AutomationUi(
                        id = automation.id,
                        conversationId = automation.conversationId,
                        agent = agent,
                        name = automation.name,
                        intervalMinutes = automation.intervalMinutes,
                        statusLabel = when {
                            automation.status == "paused" -> text(R.string.automation_status_paused)
                            automation.deferredReason == "WAITING_FOR_UNLOCK" -> text(R.string.automation_status_waiting_unlock)
                            automation.deferredReason != null -> text(R.string.automation_status_delayed)
                            automation.status == "running" -> text(R.string.automation_status_running)
                            else -> text(R.string.automation_status_active)
                        },
                        nextRunAtLabel = friendlyDate(automation.nextRunAt),
                        lastSummary = automation.lastSummary?.toDisplayText(),
                        scheduledAtLabel = automation.scheduledAt?.let(::friendlyDate),
                        startedAtLabel = automation.startedAt?.let(::friendlyDate),
                        delayLabel = automation.delayMillis?.let(::friendlyDuration),
                        waitingDurationLabel = automation.waitingSince?.let { waitingSince ->
                            runCatching {
                                friendlyDuration(
                                    Instant.now().toEpochMilli() - Instant.parse(waitingSince).toEpochMilli(),
                                )
                            }.getOrNull()
                        },
                        deferredReasonLabel = when (automation.deferredReason) {
                            "WAITING_FOR_UNLOCK" -> text(R.string.automation_reason_unlock)
                            "SERVICE_DISABLED" -> text(R.string.automation_reason_service_disabled)
                            "RUNTIME_UNAVAILABLE" -> text(R.string.automation_reason_host_unavailable)
                            "UNKNOWN" -> text(R.string.automation_reason_unknown)
                            null -> null
                            else -> automation.deferredReason
                        },
                        skippedOccurrences = automation.skippedOccurrences,
                        enabled = automation.status != "paused",
                    )
                },
                loading = false,
            )
        }
    }

    private fun toUiMessage(message: WorkspaceMessage) = ChatMessageUi(
        id = message.id,
        role = if (message.role == "user") ChatMessageRole.USER else ChatMessageRole.AGENT,
        text = message.content.toDisplayText(),
        timeLabel = friendlyTime(message.createdAt),
    )

    private fun Throwable.toUserMessage(): String = when (this) {
        is WorkspaceApiException -> when (code) {
            "agent_busy" -> text(R.string.error_agent_busy)
            "codex_run_failed" -> text(R.string.error_run_failed)
            "conversation_not_found" -> text(R.string.error_conversation_missing)
            "invalid_agent_role" -> text(R.string.error_role_invalid)
            "starter_generation_failed" -> text(R.string.error_starters_failed)
            "invalid_agent_name" -> text(R.string.agent_name_invalid)
            "invalid_skill_assignments" -> text(R.string.error_skills_invalid)
            "skill_definition_not_found" -> text(R.string.error_skill_missing)
            "automation_not_found" -> text(R.string.error_automation_missing)
            "invalid_automation_enabled" -> text(R.string.error_automation_toggle)
            else -> text(R.string.error_host_code, code)
        }
        else -> text(R.string.error_host_unreachable)
    }

    private fun friendlyDate(value: String): String = runCatching {
        val instant = Instant.parse(value)
        val local = instant.atZone(ZoneId.systemDefault())
        val today = java.time.LocalDate.now()
        when (local.toLocalDate()) {
            today -> text(R.string.date_today_at, local.format(DateTimeFormatter.ofPattern("HH:mm")))
            today.minusDays(1) -> text(R.string.date_yesterday)
            else -> local.format(DateTimeFormatter.ofPattern(text(R.string.date_short_pattern)))
        }
    }.getOrDefault("")

    private fun friendlyTime(value: String): String = runCatching {
        Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("")

    private fun friendlyDuration(value: Long): String {
        val totalMinutes = value.coerceAtLeast(0) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> text(R.string.duration_hours_minutes, hours, minutes)
            hours > 0 -> text(R.string.duration_hours, hours)
            totalMinutes > 0 -> text(R.string.duration_minutes, totalMinutes)
            else -> text(R.string.duration_under_minute)
        }
    }

    private fun currentTimeLabel(): String =
        java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    private fun String.toDisplayText(): String = this
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
}

internal fun returnedConversationTitle(
    state: WorkspaceUiState,
    agentId: String,
    conversationId: String,
    fallback: String,
): String = state.recentConversations
    .firstOrNull { it.id == conversationId && it.agent.id == agentId }
    ?.title
    ?: state.recentCompletedTasks
        .firstOrNull { it.conversationId == conversationId && it.agent.id == agentId }
        ?.title
    ?: state.chatsByAgent[agentId]
        ?.takeIf { it.conversationId == conversationId }
        ?.title
    ?: fallback
