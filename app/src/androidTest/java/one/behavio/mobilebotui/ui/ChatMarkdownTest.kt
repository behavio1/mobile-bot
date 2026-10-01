package one.behavio.mobilebotui.ui

import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatMarkdownTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replyRendersMarkdownAndUpdatesInBothModes() {
        var dark by mutableStateOf(true)
        var body by mutableStateOf("**Porównanie")
        val markdown = """
            ## Porównanie ofert
            **Warto sprawdzić koszty naprawy.** Cena zakupu to nie cały koszt.

            | Oferta | Przebieg | Cena i stan |
            |---|---|---|
            | Twoja oferta | 158 tys. km | **65 tys. zł**, uszkodzone zawieszenie |
            | Oferta 2 | 89 tys. km | **124 tys. zł**, bez napraw |

            ### Przed decyzją
            - Sprawdź historię auta.
            - Porównaj pełne koszty.

            [Zobacz źródło](https://example.com/oferta)
        """.trimIndent()
        compose.setContent { MobileBotTheme {
            MaterialTheme(colorScheme=GraphicTheme.HEROES.definition.colors(dark)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal=16.dp, vertical=40.dp)) {
                        ChatMessage(ChatMessageUi("markdown", ChatMessageRole.AGENT, body),
                            starterAgentsUi()[2].copy(avatarIndex=3))
                    }
                }
            }
        } }
        compose.onNodeWithTag("markdown-content").assertExists()
        compose.runOnIdle { body = markdown }
        for (mode in listOf(true, false)) {
            compose.runOnIdle { dark = mode }
            compose.waitForIdle()
            compose.runOnIdle {
                fun textViews(view: View): List<TextView> = when(view) {
                    is TextView -> listOf(view)
                    is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
                    else -> emptyList()
                }
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
                val text = textViews(activity.window.decorView).first { it.text.contains("Porównanie ofert") }.text as Spanned
                assertFalse(text.contains("**")); assertFalse(text.contains("##")); assertFalse(text.contains("|---"))
                val spans = text.getSpans(0, text.length, Any::class.java).map { it.javaClass.simpleName }
                assertTrue(spans.toString(), spans.any { it.contains("Heading") })
                assertTrue(spans.toString(), spans.any { it.contains("StrongEmphasis") })
                assertTrue(spans.toString(), spans.any { it.contains("TableRow") })
                assertTrue(spans.toString(), spans.any { it.contains("Link") })
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = java.io.File(instrumentation.targetContext.filesDir, "chat-markdown-${if(mode) "dark" else "light"}.png")
            instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
        }
    }
}
