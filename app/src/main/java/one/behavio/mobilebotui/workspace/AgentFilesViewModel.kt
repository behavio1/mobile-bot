package one.behavio.mobilebotui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI
import androidx.annotation.StringRes
import one.behavio.mobilebotui.R

data class AgentFilesState(
    val folder: String = "",
    val filePath: String? = null,
    val entries: List<AgentDocumentEntry> = emptyList(),
    val document: AgentDocument? = null,
    val loading: Boolean = true,
    @StringRes val error: Int? = null,
)

/** Resolves only relative Markdown links within this agent's document collection. */
internal fun resolveDocumentLink(currentFile: String, link: String): String? = runCatching {
    val uri = URI(link)
    if (uri.isAbsolute || uri.rawAuthority != null || uri.path.startsWith("/") || uri.path.contains('\\')) return null
    val segments = currentFile.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
    if (uri.path.isEmpty()) return currentFile
    for (segment in uri.path.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
    }
    val resolved = segments.joinToString("/")
    if (!Regex("(?i).*\\.(md|markdown)$").matches(resolved)) null else resolved
}.getOrNull()

class AgentFilesViewModel : ViewModel() {
    private val client = WorkspaceClient()
    private val mutableState = MutableStateFlow(AgentFilesState())
    val state = mutableState.asStateFlow()
    private var agentId = ""
    private var job: Job? = null
    private val history = mutableListOf<String>()

    fun start(id: String) {
        if (agentId != id) { agentId = id; history.clear(); mutableState.value = AgentFilesState() }
        refresh()
    }

    fun folder(path: String) {
        history.clear()
        mutableState.value = AgentFilesState(folder = path)
        refresh()
    }

    fun open(path: String) {
        mutableState.value.filePath?.let { history.add(it) }
        mutableState.value = mutableState.value.copy(filePath = path, document = null, error = null)
        refresh()
    }

    fun back(): Boolean {
        val current = mutableState.value
        if (current.filePath != null) {
            val previous = history.removeLastOrNull()
            mutableState.value = current.copy(filePath = previous, document = null, error = null)
        } else if (current.folder.isNotEmpty()) {
            mutableState.value = AgentFilesState(folder = current.folder.substringBeforeLast('/', ""))
        } else return false
        refresh()
        return true
    }

    fun refresh() {
        if (agentId.isEmpty()) return
        job?.cancel()
        val target = mutableState.value
        val id = agentId
        mutableState.value = target.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val next = if (target.filePath != null) target.copy(document = client.agentDocument(id, target.filePath))
                else target.copy(entries = client.agentFiles(id, target.folder))
                mutableState.value = next.copy(loading = false)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = when ((error as? WorkspaceApiException)?.code) {
                    "document_not_found" -> R.string.files_error_not_found
                    "document_too_large" -> R.string.files_error_too_large
                    "document_invalid_text" -> R.string.files_error_invalid_text
                    "invalid_document_path", "markdown_only" -> R.string.files_error_markdown_only
                    "agent_not_found" -> R.string.files_error_agent_missing
                    else -> R.string.files_error_load
                }
                mutableState.value = target.copy(loading = false, error = message)
            }
        }
    }
}
