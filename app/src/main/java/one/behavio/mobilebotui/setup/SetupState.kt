package one.behavio.mobilebotui.setup

import androidx.annotation.StringRes
import one.behavio.mobilebotui.R

enum class BridgeState {
    UNKNOWN,
    ENABLED,
    DISABLED,
    FAILED,
}

enum class RuntimeKind(val wireValue: String) {
    CODEX("codex"),
    PI_LOCAL("pi-local"),
}

data class HostStatus(
    val reachable: Boolean,
    val protocolVersion: Int? = null,
    val environmentRevision: Int? = null,
    val hostVersion: String? = null,
    val codexInstalled: Boolean = false,
    val codexVersion: String? = null,
    val codexAuthenticated: Boolean = false,
    val selectedRuntime: RuntimeKind = RuntimeKind.CODEX,
    val localAiStatus: String? = null,
    val localAiModelInstalled: Boolean = false,
    val localAiReady: Boolean = false,
    val localAiBytesDownloaded: Long = 0,
    val localAiTotalBytes: Long = 0,
    val localAiErrorCode: String? = null,
)

data class SetupFacts(
    val termuxInstalled: Boolean? = null,
    val runCommandPermissionGranted: Boolean = false,
    val bridgeState: BridgeState = BridgeState.UNKNOWN,
    val hostStatus: HostStatus? = null,
    val busy: Boolean = false,
    val checking: Boolean = false,
    val detail: String? = null,
    val selectedRuntime: RuntimeKind = RuntimeKind.CODEX,
)

enum class SetupStage {
    CHECKING,
    TERMUX_MISSING,
    RUN_COMMAND_PERMISSION_MISSING,
    TERMUX_BRIDGE_DISABLED,
    TERMUX_BRIDGE_FAILED,
    BOOTSTRAP_REQUIRED,
    BOOTSTRAPPING,
    CODEX_LOGIN_REQUIRED,
    LOCAL_AI_REQUIRED,
    LOCAL_AI_PREPARING,
    LOCAL_AI_FAILED,
    READY,
}

internal fun HostStatus.isCurrentRuntime(): Boolean =
    reachable &&
        protocolVersion == TermuxContract.PROTOCOL_VERSION &&
        environmentRevision == TermuxContract.ENVIRONMENT_REVISION &&
        hostVersion == TermuxContract.HOST_VERSION &&
        (codexInstalled || selectedRuntime == RuntimeKind.PI_LOCAL)

fun resolveSetupStage(facts: SetupFacts): SetupStage = when {
    facts.busy -> SetupStage.BOOTSTRAPPING
    facts.termuxInstalled == null -> SetupStage.CHECKING
    !facts.termuxInstalled -> SetupStage.TERMUX_MISSING
    !facts.runCommandPermissionGranted -> SetupStage.RUN_COMMAND_PERMISSION_MISSING
    facts.bridgeState == BridgeState.UNKNOWN -> SetupStage.CHECKING
    facts.bridgeState == BridgeState.DISABLED -> SetupStage.TERMUX_BRIDGE_DISABLED
    facts.bridgeState == BridgeState.FAILED -> SetupStage.TERMUX_BRIDGE_FAILED
    facts.hostStatus?.isCurrentRuntime() != true -> SetupStage.BOOTSTRAP_REQUIRED
    facts.selectedRuntime == RuntimeKind.PI_LOCAL -> when {
        facts.hostStatus.localAiErrorCode != null -> SetupStage.LOCAL_AI_FAILED
        !facts.hostStatus.localAiModelInstalled -> SetupStage.LOCAL_AI_REQUIRED
        !facts.hostStatus.localAiReady -> SetupStage.LOCAL_AI_PREPARING
        else -> SetupStage.READY
    }
    !facts.hostStatus.codexAuthenticated -> SetupStage.CODEX_LOGIN_REQUIRED
    else -> SetupStage.READY
}

// A preparation failure is not evidence that the Termux connection failed.
internal fun SetupFacts.afterPreparationFailure(text: (Int) -> String, @StringRes failureDetail: Int? = null): SetupFacts =
    if (hostStatus?.isCurrentRuntime() == true) copy(busy = false)
    else copy(busy = false, detail = if (bridgeState == BridgeState.ENABLED)
        text(failureDetail ?: R.string.setup_failed_bridge_ok)
        else detail ?: text(R.string.setup_failed_bridge_check))

@StringRes
internal fun preparationFailureDetail(output: String): Int? = when {
    output.contains("phase=runtime_packages") -> R.string.setup_failed_packages
    output.contains("phase=codex_check") -> R.string.setup_failed_codex_check
    output.contains("phase=codex") -> R.string.setup_failed_codex
    output.contains("phase=mobile_bot_files") -> R.string.setup_failed_files
    output.contains("phase=checking") -> R.string.setup_failed_start
    else -> null
}
