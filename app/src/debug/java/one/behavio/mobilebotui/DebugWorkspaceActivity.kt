package one.behavio.mobilebotui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import one.behavio.mobilebotui.setup.BridgeState
import one.behavio.mobilebotui.setup.HostStatus
import one.behavio.mobilebotui.setup.SetupFacts
import one.behavio.mobilebotui.ui.MobileBotApp
import one.behavio.mobilebotui.ui.MobileBotTheme
import one.behavio.mobilebotui.ui.WorkspaceUiState

class DebugWorkspaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MobileBotTheme {
                MobileBotApp(
                    facts = SetupFacts(
                        termuxInstalled = true,
                        runCommandPermissionGranted = true,
                        bridgeState = BridgeState.ENABLED,
                        hostStatus = HostStatus(
                            reachable = true,
                            codexInstalled = true,
                            codexAuthenticated = true,
                        ),
                    ),
                    workspace = WorkspaceUiState(),
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
    }
}
