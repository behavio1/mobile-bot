package one.behavio.mobilebotui.setup

import one.behavio.mobilebotui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupStateTest {
    @Test
    fun `failed preparation preserves verified Termux connection`() {
        val facts = readyFacts(hostStatus = readyHost().copy(reachable = false)).afterPreparationFailure({ "text-$it" })
        assertEquals(BridgeState.ENABLED, facts.bridgeState)
        assertEquals(SetupStage.BOOTSTRAP_REQUIRED, resolveSetupStage(facts))
    }

    @Test
    fun `successful runtime found after failed callback does not request reinstall`() {
        val facts = readyFacts().afterPreparationFailure({ "text-$it" })
        assertEquals(SetupStage.READY, resolveSetupStage(facts))
        assertNull(facts.detail)
    }

    @Test
    fun `unknown installation state keeps the setup gate closed`() {
        assertEquals(SetupStage.CHECKING, resolveSetupStage(SetupFacts()))
    }

    @Test
    fun `active setup operation has priority over every discovered problem`() {
        val facts = SetupFacts(
            termuxInstalled = false,
            bridgeState = BridgeState.FAILED,
            busy = true,
        )

        assertEquals(SetupStage.BOOTSTRAPPING, resolveSetupStage(facts))
    }

    @Test
    fun `missing Termux blocks every later stage`() {
        val facts = SetupFacts(
            termuxInstalled = false,
            runCommandPermissionGranted = true,
            bridgeState = BridgeState.ENABLED,
            hostStatus = readyHost(),
        )

        assertEquals(SetupStage.TERMUX_MISSING, resolveSetupStage(facts))
    }

    @Test
    fun `bridge permission is required before probing Host`() {
        val facts = SetupFacts(
            termuxInstalled = true,
            runCommandPermissionGranted = false,
            bridgeState = BridgeState.ENABLED,
            hostStatus = readyHost(),
        )

        assertEquals(SetupStage.RUN_COMMAND_PERMISSION_MISSING, resolveSetupStage(facts))
    }

    @Test
    fun `unknown bridge result keeps the setup gate checking`() {
        val facts = SetupFacts(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
            bridgeState = BridgeState.UNKNOWN,
        )

        assertEquals(SetupStage.CHECKING, resolveSetupStage(facts))
    }

    @Test
    fun `disabled Termux bridge requires the guided manual step`() {
        val facts = SetupFacts(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
            bridgeState = BridgeState.DISABLED,
        )

        assertEquals(SetupStage.TERMUX_BRIDGE_DISABLED, resolveSetupStage(facts))
    }

    @Test
    fun `failed Termux probe requires user attention`() {
        val facts = SetupFacts(
            termuxInstalled = true,
            runCommandPermissionGranted = true,
            bridgeState = BridgeState.FAILED,
        )

        assertEquals(SetupStage.TERMUX_BRIDGE_FAILED, resolveSetupStage(facts))
    }

    @Test
    fun `unreachable Host requires bootstrap`() {
        val facts = readyFacts(
            hostStatus = readyHost().copy(reachable = false),
        )

        assertEquals(SetupStage.BOOTSTRAP_REQUIRED, resolveSetupStage(facts))
    }

    @Test
    fun `incompatible protocol requires bootstrap`() {
        val facts = readyFacts(
            hostStatus = readyHost().copy(protocolVersion = TermuxContract.PROTOCOL_VERSION - 1),
        )

        assertEquals(SetupStage.BOOTSTRAP_REQUIRED, resolveSetupStage(facts))
    }

    @Test
    fun `incompatible environment revision requires bootstrap`() {
        val facts = readyFacts(
            hostStatus = readyHost().copy(environmentRevision = TermuxContract.ENVIRONMENT_REVISION - 1),
        )

        assertEquals(SetupStage.BOOTSTRAP_REQUIRED, resolveSetupStage(facts))
    }

    @Test
    fun `missing Codex executable requires bootstrap`() {
        val facts = readyFacts(
            hostStatus = readyHost().copy(codexInstalled = false, codexAuthenticated = false),
        )

        assertEquals(SetupStage.BOOTSTRAP_REQUIRED, resolveSetupStage(facts))
    }

    @Test
    fun `reachable Host still requires Codex authentication`() {
        val facts = readyFacts(
            hostStatus = readyHost().copy(codexAuthenticated = false),
        )

        assertEquals(SetupStage.CODEX_LOGIN_REQUIRED, resolveSetupStage(facts))
    }

    @Test
    fun `all verified prerequisites unlock app`() {
        val facts = readyFacts()

        assertEquals(SetupStage.READY, resolveSetupStage(facts))
    }

    @Test
    fun `previous Host release requires bootstrap before workspace actions`() {
        val facts = readyFacts(
            hostStatus = readyHost().copy(
                hostVersion = "0.5.7",
                environmentRevision = 13,
            ),
        )

        assertEquals(SetupStage.BOOTSTRAP_REQUIRED, resolveSetupStage(facts))
    }

    @Test
    fun `only the exact install contract is a current runtime`() {
        assertEquals(true, readyHost().isCurrentRuntime())
        assertEquals(false, readyHost().copy(reachable = false).isCurrentRuntime())
        assertEquals(false, readyHost().copy(protocolVersion = 0).isCurrentRuntime())
        assertEquals(false, readyHost().copy(environmentRevision = 0).isCurrentRuntime())
        assertEquals(false, readyHost().copy(hostVersion = "0.0.0").isCurrentRuntime())
        assertEquals(false, readyHost().copy(codexInstalled = false).isCurrentRuntime())
    }

    @Test
    fun `bootstrap progress accepts only the current run and known real phases`() {
        val bootstrapId = "bootstrap-current"

        assertEquals(
            R.string.bootstrap_runtime_packages,
            bootstrapProgressDetail("$bootstrapId:runtime_packages\n", bootstrapId),
        )
        assertEquals(
            R.string.bootstrap_codex,
            bootstrapProgressDetail("$bootstrapId:codex", bootstrapId),
        )
        assertEquals(
            R.string.bootstrap_checking,
            bootstrapProgressDetail("$bootstrapId:checking", bootstrapId),
        )
        assertNull(bootstrapProgressDetail("bootstrap-old:codex", bootstrapId))
        assertNull(bootstrapProgressDetail("$bootstrapId:invented_phase", bootstrapId))
    }

    private fun readyFacts(hostStatus: HostStatus = readyHost()) = SetupFacts(
        termuxInstalled = true,
        runCommandPermissionGranted = true,
        bridgeState = BridgeState.ENABLED,
        hostStatus = hostStatus,
    )

    private fun readyHost() = HostStatus(
        reachable = true,
        protocolVersion = TermuxContract.PROTOCOL_VERSION,
        environmentRevision = TermuxContract.ENVIRONMENT_REVISION,
        hostVersion = TermuxContract.HOST_VERSION,
        codexInstalled = true,
        codexAuthenticated = true,
    )
}
