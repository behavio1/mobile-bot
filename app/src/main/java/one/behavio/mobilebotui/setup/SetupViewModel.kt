package one.behavio.mobilebotui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import androidx.annotation.StringRes
import one.behavio.mobilebotui.R

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PrerequisiteRepository(application)
    private val _facts = MutableStateFlow(SetupFacts())
    val facts: StateFlow<SetupFacts> = _facts.asStateFlow()

    private var refreshJob: Job? = null

    private fun text(@StringRes id: Int): String = getApplication<Application>().getString(id)
    private var localPrepareJob: Job? = null

    fun refresh() {
        if (_facts.value.busy || refreshJob?.isActive == true) return
        val previous = _facts.value
        _facts.update { it.copy(checking = true, detail = text(R.string.setup_checking_ready)) }
        refreshJob = viewModelScope.launch {
            do {
                val inspected = repository.inspect { detail ->
                    _facts.update { it.copy(detail = detail) }
                }
                _facts.update { current ->
                    if (resolveSetupStage(inspected) == SetupStage.BOOTSTRAP_REQUIRED &&
                        resolveSetupStage(previous) == SetupStage.BOOTSTRAP_REQUIRED)
                        inspected.copy(detail = previous.detail ?: inspected.detail)
                    else inspected
                }
                if (inspected.busy) delay(2_000)
            } while (inspected.busy)
        }
    }

    fun bootstrap() {
        if (_facts.value.busy || _facts.value.checking) return
        val runtime = _facts.value.selectedRuntime
        refreshJob?.cancel()
        _facts.update { it.copy(busy = true, detail = text(R.string.setup_checking_ready)) }
        viewModelScope.launch {
            val result = runCatching {
                repository.bootstrap(runtime) { detail ->
                    _facts.update { current ->
                        if (current.busy) current.copy(detail = detail) else current
                    }
                }
            }
            result.fold(
                onSuccess = { commandResult ->
                    if (commandResult.succeeded) {
                        var inspected = repository.inspect()
                        while (inspected.busy) {
                            _facts.value = inspected
                            delay(2_000)
                            inspected = repository.inspect()
                        }
                        _facts.value = inspected
                    } else {
                        _facts.value = repository.inspect().afterPreparationFailure(::text, preparationFailureDetail(commandResult.stdout))
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _facts.value = repository.inspect().afterPreparationFailure(
                        ::text,
                        if (error is BootstrapInterruptedException) R.string.setup_interrupted else null,
                    )
                },
            )
        }
    }

    fun selectRuntime(runtime: RuntimeKind) {
        if (_facts.value.busy || _facts.value.checking || localPrepareJob?.isActive == true) return
        _facts.update { current ->
            current.copy(
                selectedRuntime = runtime,
                hostStatus = current.hostStatus?.copy(selectedRuntime = runtime),
                detail = null,
            )
        }
        viewModelScope.launch {
            repository.selectRuntime(runtime)
            refresh()
        }
    }

    fun prepareLocalAi() {
        if (localPrepareJob?.isActive == true || _facts.value.busy || _facts.value.checking) return
        _facts.update { it.copy(detail = text(R.string.local_ai_downloading)) }
        localPrepareJob = viewModelScope.launch {
            val accepted = runCatching { repository.prepareLocalAi() }.getOrDefault(false)
            if (!accepted) {
                _facts.update { it.copy(detail = text(R.string.local_ai_download_failed)) }
                return@launch
            }
            repeat(720) {
                delay(2_000)
                val inspected = repository.inspect()
                _facts.value = inspected
                if (inspected.hostStatus?.localAiReady == true || inspected.hostStatus?.localAiErrorCode != null) return@launch
            }
            _facts.update { it.copy(detail = text(R.string.local_ai_download_slow)) }
        }
    }

    fun login() {
        viewModelScope.launch {
            _facts.value = _facts.value.copy(busy = true, detail = text(R.string.codex_login_starting))
            runCatching { repository.startCodexLogin() }
            _facts.value = repository.inspect()
        }
    }
}
