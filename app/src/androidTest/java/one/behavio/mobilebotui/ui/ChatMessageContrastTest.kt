package one.behavio.mobilebotui.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ChatMessageContrastTest {
    @get:Rule val compose = createComposeRule()

    @Test fun userMessageIsReadableInLightAndDarkModes() {
        var dark by mutableStateOf(false)
        compose.setContent {
            MobileBotTheme {
                MaterialTheme(colorScheme = GraphicTheme.HEROES.definition.colors(dark)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 60.dp)) {
                            ChatMessage(ChatMessageUi("contrast", ChatMessageRole.USER,
                                "Wejdź na WhatsApp i pokaż mi trzy ostatnie wiadomości."), null)
                        }
                    }
                }
            }
        }
        for (mode in listOf(false, true)) {
            compose.runOnIdle { dark = mode }
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("user-message").captureToImage().toPixelMap()
            val background = pixels[pixels.width / 2, 2].luminance()
            var bestContrast = 1f
            // The central strip excludes rounded corners and samples the actual rendered letters.
            for (x in pixels.width / 4 until pixels.width * 3 / 4) {
                for (y in 2 until pixels.height - 2) {
                    val foreground = pixels[x, y].luminance()
                    bestContrast = max(bestContrast, (max(background, foreground) + 0.05f) / (min(background, foreground) + 0.05f))
                }
            }
            assertTrue("Rendered message contrast in dark=$mode: $bestContrast", bestContrast >= 4.5f)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = java.io.File(context.filesDir, "chat-contrast-${if (mode) "dark" else "light"}.png")
            file.outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    @Test fun agentReplyUsesFullWidthBelowCompactHeader() {
        compose.setContent {
            MobileBotTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 60.dp)) {
                        ChatMessage(ChatMessageUi("reply", ChatMessageRole.AGENT,
                            "Przygotowałam porównanie ofert.\n\nNajważniejsze informacje znajdziesz poniżej. Dłuższa odpowiedź korzysta z całej szerokości ekranu, bez pustej kolumny pod zdjęciem.", "12:30"), starterAgentsUi()[2])
                    }
                }
            }
        }
        val block = compose.onNodeWithTag("agent-message-block").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithTag("agent-message").fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("agent-message-header").fetchSemanticsNode().boundsInRoot
        assertTrue("Reply starts at the left edge", kotlin.math.abs(text.left - block.left) < 1f)
        assertTrue("Reply uses the available width", kotlin.math.abs(text.right - block.right) < 1f)
        assertTrue("Avatar does not indent the body", text.top > header.bottom)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.filesDir, "agent-reply-width.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

}
