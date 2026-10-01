package one.behavio.mobilebotui.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.behavio.mobilebotui.automation.ActionCorrelation
import one.behavio.mobilebotui.automation.ActionLogger
import one.behavio.mobilebotui.automation.ActionMetadata
import one.behavio.mobilebotui.automation.DevelopmentActions
import one.behavio.mobilebotui.automation.HostApiAuth
import one.behavio.mobilebotui.automation.NoOpActionLogger
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class WorkspaceAgent(
    val id: String,
    val name: String,
    val initial: String,
    val subtitle: String,
    val assignedSkillIds: List<String> = emptyList(),
    val roleDescription: String = "",
    val conversationStarters: List<String> = emptyList(),
    val conversationCount: Int? = null,
)

data class WorkspaceSkillDefinition(
    val id: String,
    val name: String,
    val summary: String,
    val assignable: Boolean,
)

data class WorkspaceConversation(
    val id: String,
    val agentId: String,
    val title: String,
    val lastMessage: String,
    val updatedAt: String,
)

data class WorkspaceConversationPage(
    val conversations: List<WorkspaceConversation>,
    val hasMore: Boolean,
)

data class WorkspaceTask(
    val id: String,
    val conversationId: String,
    val agentId: String,
    val title: String,
    val resultSummary: String,
    val completedAt: String,
)

data class WorkspaceMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String,
)

data class WorkspaceAutomation(
    val id: String,
    val agentId: String,
    val conversationId: String,
    val name: String,
    val intervalMinutes: Int,
    val status: String,
    val nextRunAt: String,
    val lastRunAt: String?,
    val lastOutcome: String?,
    val lastSummary: String?,
    val scheduledAt: String?,
    val startedAt: String?,
    val delayMillis: Long?,
    val waitingSince: String?,
    val deferredReason: String?,
    val skippedOccurrences: Int,
)

data class WorkspaceSnapshot(
    val customThemes: String = "[]",
    val ownerProfile: String,
    val skillDefinitions: List<WorkspaceSkillDefinition>,
    val agents: List<WorkspaceAgent>,
    val conversations: List<WorkspaceConversation>,
    val completedTasks: List<WorkspaceTask>,
    val automations: List<WorkspaceAutomation>,
)

data class RunResult(
    val runId: String,
    val conversationId: String,
    val title: String,
    val reply: String,
)

data class AgentDocumentEntry(val name: String, val path: String, val directory: Boolean, val size: Long, val modifiedAt: String)
data class AgentDocument(val path: String, val content: String, val size: Long, val modifiedAt: String)

class WorkspaceApiException(val status: Int, val code: String) : Exception(code)

class WorkspaceClient(
    private val baseUrl: String = "http://127.0.0.1:8767",
    private val actionLog: ActionLogger = NoOpActionLogger,
) {
    private fun JSONObject.toSkillBuild() = SkillBuildUi(
        getString("id"), getString("agentId"), getString("description"), getString("skillId"),
        getString("status"), getString("name"), getString("summary"), getString("question"), getString("evidence"),
    )
    suspend fun skillBuilds(): List<SkillBuildUi> = withContext(Dispatchers.IO) {
        val builds = request("GET", "/skill-builds").getJSONArray("builds")
        List(builds.length()) { builds.getJSONObject(it).toSkillBuild() }
    }
    suspend fun createSkillBuild(agentId: String, requestId: String, description: String): SkillBuildUi = withContext(Dispatchers.IO) {
        request("POST", "/skill-builds", JSONObject().put("agentId", agentId).put("requestId", requestId).put("description", description).toString())
            .getJSONObject("build").toSkillBuild()
    }
    suspend fun resumeSkillBuild(id: String, answer: String): SkillBuildUi = withContext(Dispatchers.IO) {
        request("POST", "/skill-builds/$id/resume", JSONObject().put("answer", answer).toString()).getJSONObject("build").toSkillBuild()
    }
    suspend fun skillInstructions(id: String): String = withContext(Dispatchers.IO) {
        request("GET", "/skills/$id").getString("content")
    }

    suspend fun agentFiles(agentId: String, path: String): List<AgentDocumentEntry> = withContext(Dispatchers.IO) {
        val query = java.net.URLEncoder.encode(path, "UTF-8")
        val array = request("GET", "/agents/$agentId/files?path=$query").getJSONArray("entries")
        List(array.length()) { index -> array.getJSONObject(index).let {
            AgentDocumentEntry(it.getString("name"), it.getString("path"), it.getBoolean("directory"),
                it.getLong("size"), it.getString("modifiedAt"))
        } }
    }

    suspend fun agentDocument(agentId: String, path: String): AgentDocument = withContext(Dispatchers.IO) {
        val query = java.net.URLEncoder.encode(path, "UTF-8")
        request("GET", "/agents/$agentId/file?path=$query").let {
            AgentDocument(it.getString("path"), it.getString("content"), it.getLong("size"), it.getString("modifiedAt"))
        }
    }

    suspend fun deleteAgent(agentId: String) = withContext(Dispatchers.IO) {
        request("DELETE", "/agents/$agentId")
        Unit
    }

    suspend fun workspace(traceId: String? = null): WorkspaceSnapshot = withContext(Dispatchers.IO) {
        val json = request("GET", "/workspace", traceId = traceId)
        val agents = json.getJSONArray("agents").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        item.toWorkspaceAgent(),
                    )
                }
            }
        }
        val skillDefinitions = json.getJSONArray("skillDefinitions").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        WorkspaceSkillDefinition(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            summary = item.getString("summary"),
                            assignable = item.getBoolean("assignable"),
                        ),
                    )
                }
            }
        }
        val conversations = json.getJSONArray("recentConversations").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        WorkspaceConversation(
                            id = item.getString("id"),
                            agentId = item.getString("agentId"),
                            title = item.getString("title"),
                            lastMessage = item.optString("lastMessage"),
                            updatedAt = item.getString("updatedAt"),
                        ),
                    )
                }
            }
        }
        val tasks = json.getJSONArray("recentCompletedTasks").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        WorkspaceTask(
                            id = item.getString("id"),
                            conversationId = item.getString("conversationId"),
                            agentId = item.getString("agentId"),
                            title = item.getString("title"),
                            resultSummary = item.optString("resultSummary"),
                            completedAt = item.getString("completedAt"),
                        ),
                    )
                }
            }
        }
        val automations = json.optJSONArray("automations")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toWorkspaceAutomation())
                }
            }
        }.orEmpty()
        WorkspaceSnapshot(
            customThemes = json.optJSONArray("customThemes")?.toString() ?: "[]",
            ownerProfile = json.optString("ownerProfile"),
            skillDefinitions = skillDefinitions,
            agents = agents,
            conversations = conversations,
            completedTasks = tasks,
            automations = automations,
        )
    }

    suspend fun updateOwnerProfile(about: String, traceId: String? = null): String = withContext(Dispatchers.IO) {
        val normalized = about.trim()
        val json = request(
            "POST",
            "/owner-profile",
            JSONObject().put("about", normalized).toString(),
            traceId,
            mapOf("profileLength" to normalized.length),
        )
        json.getString("ownerProfile")
    }

    suspend fun agentConversations(agentId: String, offset: Int = 0): WorkspaceConversationPage = withContext(Dispatchers.IO) {
        val json = request("GET", "/agents/$agentId/conversations?offset=$offset&limit=5")
        val rows = json.getJSONArray("conversations")
        WorkspaceConversationPage(
            conversations = List(rows.length()) { index ->
                val item = rows.getJSONObject(index)
                WorkspaceConversation(
                    id = item.getString("id"),
                    agentId = item.getString("agentId"),
                    title = item.getString("title"),
                    lastMessage = item.optString("lastMessage"),
                    updatedAt = item.getString("updatedAt"),
                )
            },
            hasMore = json.getBoolean("hasMore"),
        )
    }

    suspend fun createAgent(name: String, traceId: String? = null, roleDescription: String = ""): WorkspaceAgent = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        val json = request(
            "POST",
            "/agents",
            JSONObject().put("name", normalized).apply {
                if (roleDescription.isNotBlank()) put("roleDescription", roleDescription.trim())
            }.toString(),
            traceId,
            mapOf("nameLength" to normalized.length),
        ).getJSONObject("agent")
        json.toWorkspaceAgent()
    }

    suspend fun renameAgent(
        agentId: String,
        name: String,
        traceId: String? = null,
    ): WorkspaceAgent = withContext(Dispatchers.IO) {
        val normalized = name.trim()
        val json = request(
            "PUT",
            "/agents/$agentId/name",
            JSONObject().put("name", normalized).toString(),
            traceId,
            mapOf("agentId" to agentId, "nameLength" to normalized.length),
        ).getJSONObject("agent")
        json.toWorkspaceAgent()
    }

    suspend fun updateAgentRole(
        agentId: String,
        roleDescription: String,
        traceId: String? = null,
    ): WorkspaceAgent = withContext(Dispatchers.IO) {
        request(
            "PUT",
            "/agents/$agentId/role",
            JSONObject().put("roleDescription", roleDescription.trim()).toString(),
            traceId,
            mapOf("agentId" to agentId, "roleLength" to roleDescription.trim().length),
        ).getJSONObject("agent").toWorkspaceAgent()
    }

    suspend fun updateAgentSkills(
        agentId: String,
        skillIds: List<String>,
        traceId: String? = null,
    ): WorkspaceAgent = withContext(Dispatchers.IO) {
        val json = request(
            "PUT",
            "/agents/$agentId/skills",
            JSONObject().put("skillIds", JSONArray(skillIds)).toString(),
            traceId,
            mapOf("agentId" to agentId, "assignmentCount" to skillIds.size),
        ).getJSONObject("agent")
        json.toWorkspaceAgent()
    }

    suspend fun updateAutomationEnabled(
        automationId: String,
        agentId: String,
        enabled: Boolean,
        traceId: String? = null,
    ): WorkspaceAutomation = withContext(Dispatchers.IO) {
        val json = request(
            "PATCH",
            "/automations/$automationId/enabled",
            JSONObject().put("enabled", enabled).toString(),
            traceId,
            mapOf("automationId" to automationId, "agentId" to agentId, "enabled" to enabled),
        ).getJSONObject("automation")
        json.toWorkspaceAutomation()
    }

    suspend fun messages(agentId: String, conversationId: String, traceId: String? = null): List<WorkspaceMessage> =
        withContext(Dispatchers.IO) {
        val json = request(
            "GET",
            "/conversations/$conversationId/messages",
            traceId = traceId,
            metadata = mapOf("agentId" to agentId, "conversationId" to conversationId),
        )
        json.getJSONArray("messages").let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        WorkspaceMessage(
                            id = item.getString("id"),
                            role = item.getString("role"),
                            content = item.getString("content"),
                            createdAt = item.getString("createdAt"),
                        ),
                    )
                }
            }
        }
    }

    suspend fun run(agentId: String, prompt: String, conversationId: String?, traceId: String? = null): RunResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("agentId", agentId)
                .put("prompt", prompt)
                .also { if (conversationId != null) it.put("conversationId", conversationId) }
            val json = request(
                "POST",
                "/runs",
                body.toString(),
                traceId,
                mapOf(
                    "agentId" to agentId,
                    "conversationId" to conversationId,
                    "promptLength" to prompt.length,
                ),
            )
            RunResult(
                runId = json.getString("runId"),
                conversationId = json.getString("conversationId"),
                title = json.getString("title"),
                reply = json.getString("reply"),
            )
        }

    suspend fun reconcileAutomations(traceId: String? = null) = withContext(Dispatchers.IO) {
        request("POST", "/v5/scheduler/reconcile", "{}", traceId)
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        traceId: String? = null,
        metadata: ActionMetadata = emptyMap(),
    ): JSONObject {
        val requestId = UUID.randomUUID().toString()
        val correlation = ActionCorrelation(traceId = traceId ?: requestId, actionId = requestId)
        val startedAtNanos = System.nanoTime()
        val commonMetadata = metadata + mapOf(
            "requestId" to requestId,
            "method" to method,
            "path" to path,
            "bodyBytes" to (body?.toByteArray(Charsets.UTF_8)?.size ?: 0),
        )
        actionLog.record(
            DevelopmentActions.WORKSPACE_ACTION_STARTED,
            commonMetadata,
            correlation,
        )
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 3_000
        connection.readTimeout = when {
            method == "POST" && path == "/runs" -> 50 * 60_000
            method == "POST" -> 10 * 60_000
            method == "PUT" && (path.endsWith("/role") || path.endsWith("/skills")) -> 3 * 60_000
            else -> 5_000
        }
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-Mobile-Bot-Trace-Id", correlation.traceId)
        HostApiAuth.authorize(connection)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                throw WorkspaceApiException(status, json.optString("error", "host_error"))
            }
            actionLog.record(
                DevelopmentActions.WORKSPACE_ACTION_FINISHED,
                commonMetadata + mapOf(
                    "status" to status,
                    "succeeded" to true,
                    "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
                    "errorCode" to null,
                ),
                correlation,
            )
            return json
        } catch (error: Throwable) {
            actionLog.record(
                DevelopmentActions.WORKSPACE_ACTION_FINISHED,
                commonMetadata + mapOf(
                    "status" to (error as? WorkspaceApiException)?.status,
                    "succeeded" to false,
                    "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
                    "errorType" to error.javaClass.simpleName,
                    "errorCode" to workspaceErrorCode(error),
                ),
                correlation,
            )
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun workspaceErrorCode(error: Throwable): String = when (error) {
        is WorkspaceApiException -> error.code
        is java.net.SocketTimeoutException -> "host_timeout"
        is java.net.ConnectException -> "host_unreachable"
        else -> "workspace_request_failed"
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    optString(name).takeIf { !isNull(name) && it.isNotEmpty() && it != "null" }

private fun JSONObject.toWorkspaceAgent(): WorkspaceAgent {
    val assigned = optJSONArray("assignedSkillIds")?.let { array ->
        buildList {
            for (index in 0 until array.length()) add(array.getString(index))
        }
    }.orEmpty()
    return WorkspaceAgent(
        id = getString("id"),
        name = getString("name"),
        initial = getString("initial"),
        subtitle = getString("subtitle"),
        assignedSkillIds = assigned,
        conversationCount = if (has("conversationCount")) getInt("conversationCount") else null,
        roleDescription = optString("roleDescription", ""),
        conversationStarters = optJSONArray("conversationStarters")?.let { array ->
            List(array.length()) { array.getString(it) }
        }.orEmpty(),
    )
}

private fun JSONObject.toWorkspaceAutomation() = WorkspaceAutomation(
    id = getString("id"),
    agentId = getString("agentId"),
    conversationId = getString("conversationId"),
    name = getString("name"),
    intervalMinutes = getInt("intervalMinutes"),
    status = getString("status"),
    nextRunAt = getString("nextRunAt"),
    lastRunAt = optNullableString("lastRunAt"),
    lastOutcome = optNullableString("lastOutcome"),
    lastSummary = optNullableString("lastSummary"),
    scheduledAt = optNullableString("scheduledAt"),
    startedAt = optNullableString("startedAt"),
    delayMillis = optLong("delayMillis").takeIf { !isNull("delayMillis") },
    waitingSince = optNullableString("waitingSince"),
    deferredReason = optNullableString("deferredReason"),
    skippedOccurrences = optInt("skippedOccurrences", 0),
)
