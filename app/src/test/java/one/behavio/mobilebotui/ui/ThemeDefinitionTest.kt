package one.behavio.mobilebotui.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ThemeDefinitionTest {
    @Test fun everyThemeHasCompleteArtworkAndDistinctStyle() {
        val definitions = GraphicTheme.entries.map { it.definition }
        definitions.forEach {
            assertEquals(15, it.artwork.avatars.size)
            assertEquals(15, it.artwork.avatars.toSet().size)
            assertTrue((it.artwork.avatars + listOf(it.artwork.home, it.artwork.conversations,
                it.artwork.powers, it.artwork.files, it.artwork.send, it.artwork.guide)).all { id -> id != 0 })
        }
        assertEquals(definitions.size, definitions.map { it.artwork.guide }.toSet().size)
        assertEquals(definitions.size, definitions.map { it.card.treatment }.toSet().size)
        assertEquals(definitions.size, definitions.map { it.light.primary }.toSet().size)
        assertEquals(definitions.size, definitions.map { it.dark.background }.toSet().size)
    }

    @Test fun readableTextInAllEightVariants() {
        GraphicTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val c = theme.definition.colors(dark)
                listOf("body" to (c.onSurface to c.surface),
                    "background" to (c.onBackground to c.background),
                    "muted" to (c.onSurfaceVariant to c.surfaceVariant),
                    "button" to (c.onPrimary to c.primary),
                    "selected and bubble" to (c.onPrimaryContainer to c.primaryContainer),
                    "accent" to (c.onSecondary to c.secondary)).forEach { (role, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue("$theme dark=$dark $role contrast=$ratio", ratio >= 4.5f)
                }
            }
        }
    }
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance(); val second = b.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
