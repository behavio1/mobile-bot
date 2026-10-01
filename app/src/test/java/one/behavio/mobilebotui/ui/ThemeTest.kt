package one.behavio.mobilebotui.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test fun lightThemeTextHasReadableContrast() = GraphicTheme.builtIns.forEach { checkTextContrast(it.definition.light) }
    @Test fun darkThemeTextHasReadableContrast() = GraphicTheme.builtIns.forEach { checkTextContrast(it.definition.dark) }

    private fun checkTextContrast(colors: ColorScheme) {
        with(colors) {
            listOf(
                onBackground to background,
                onSurface to surface,
                onSurfaceVariant to surface,
                onSurfaceVariant to surfaceVariant,
                onSurfaceVariant to primaryContainer,
                onSurfaceVariant to secondaryContainer,
                primary to background,
                primary to surface,
                primary to primaryContainer,
                onPrimary to primary,
                onPrimaryContainer to primaryContainer,
                onSecondary to secondary,
                onSecondaryContainer to secondaryContainer,
                tertiary to surface,
                onTertiary to tertiary,
                onTertiaryContainer to tertiaryContainer,
                error to surface,
                onError to error,
                onErrorContainer to errorContainer,
                inverseOnSurface to inverseSurface,
            ).forEach { (foreground, background) ->
                val ratio = contrast(foreground, background)
                assertTrue("Contrast $ratio for $foreground on $background is below 4.5", ratio >= 4.5)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
