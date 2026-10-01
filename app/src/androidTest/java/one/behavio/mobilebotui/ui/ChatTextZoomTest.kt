package one.behavio.mobilebotui.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatTextZoomTest {
    @get:Rule val compose = createComposeRule()
    @Test fun pinchOverMarkdownSupportsFiveBoundedSizes() {
        var step by mutableStateOf(0)
        compose.setContent { MobileBotTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().chatTextZoom(step) { step = it }.padding(16.dp)) {
                ChatMessage(ChatMessageUi("zoom", ChatMessageRole.AGENT,
                    "**Rozmiar odpowiedzi** można zmienić dwoma palcami. To tekst sprawdzający gest nad natywnym widokiem Markdown."),
                    starterAgentsUi().first(), textScale=chatTextScale(step))
            }
        } } }
        fun pinch(outward: Boolean) {
            compose.onNodeWithTag("markdown-content").performTouchInput {
                val start = if (outward) 20f else 70f
                val end = if (outward) 70f else 20f
                down(0, center - Offset(start, 0f)); down(1, center + Offset(start, 0f))
                moveTo(0, center - Offset(end, 0f), delayMillis=120)
                moveTo(1, center + Offset(end, 0f), delayMillis=120)
                up(0); up(1)
            }
            compose.waitForIdle()
        }
        pinch(true)
        compose.runOnIdle { assertEquals(2, step); assertEquals(1.2f, chatTextScale(step), 0.001f) }
        pinch(true)
        compose.runOnIdle { assertEquals(2, step) }
        pinch(false)
        compose.runOnIdle { assertEquals(-2, step); assertEquals(0.8f, chatTextScale(step), 0.001f) }
        pinch(false)
        compose.runOnIdle { assertEquals(-2, step) }
    }
}
