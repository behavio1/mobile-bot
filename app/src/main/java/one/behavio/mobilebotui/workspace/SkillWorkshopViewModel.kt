package one.behavio.mobilebotui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.annotation.StringRes
import one.behavio.mobilebotui.R

data class SkillBuildUi(
    val id: String, val agentId: String, val description: String, val skillId: String,
    val status: String, val name: String, val summary: String, val question: String, val evidence: String,
)
data class SkillWorkshopUiState(
    val builds: List<SkillBuildUi> = emptyList(), val selectedId: String? = null,
    val loading: Boolean = true, val saving: Boolean = false, @StringRes val error: Int? = null, @StringRes val loadError: Int? = null,
)

class SkillWorkshopViewModel : ViewModel() {
    private val client = WorkspaceClient()
    private val mutableState = MutableStateFlow(SkillWorkshopUiState())
    val state = mutableState.asStateFlow()
    private var requestId = UUID.randomUUID().toString()
    private var pendingDescription: String? = null
    suspend fun watch() {
            while (true) {
                try {
                    val builds = client.skillBuilds()
                    mutableState.value = mutableState.value.copy(builds = builds, loading = false, loadError = null)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { mutableState.value = mutableState.value.copy(loading = false, loadError = R.string.skill_progress_refresh_failed) }
                delay(3_000)
            }
    }
    fun select(id: String?) { mutableState.value = mutableState.value.copy(selectedId = id, error = null) }
    fun create(agentId: String, description: String) {
        if (mutableState.value.saving) return
        if (pendingDescription != description) { requestId = UUID.randomUUID().toString(); pendingDescription = description }
        submit { client.createSkillBuild(agentId, requestId, description) }
    }
    fun resume(id: String, answer: String) { if (!mutableState.value.saving) submit { client.resumeSkillBuild(id, answer) } }
    private fun submit(action: suspend () -> SkillBuildUi) {
        mutableState.value = mutableState.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val build = action()
                mutableState.value = mutableState.value.copy(selectedId = build.id,
                    builds = listOf(build) + mutableState.value.builds.filter { it.id != build.id }, saving = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = when ((error as? WorkspaceApiException)?.code) {
                    "codex_not_authenticated" -> R.string.skill_error_codex_login
                    "skill_workshop_unavailable" -> R.string.skill_error_update_environment
                    "invalid_skill_request" -> R.string.skill_error_describe
                    "skill_answer_required" -> R.string.skill_error_answer_required
                    else -> R.string.skill_error_send
                }
                mutableState.value = mutableState.value.copy(saving = false, error = message)
            }
        }
    }
}
