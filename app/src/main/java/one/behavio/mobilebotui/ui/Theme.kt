package one.behavio.mobilebotui.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalGraphicTheme = staticCompositionLocalOf { GraphicTheme.HEROES }

@Composable
fun MobileBotTheme(graphicTheme: GraphicTheme = GraphicTheme.HEROES, content: @Composable () -> Unit) {
    val definition = graphicTheme.definition
    CompositionLocalProvider(LocalGraphicTheme provides graphicTheme, LocalThemeDefinition provides definition) {
        MaterialTheme(colorScheme = definition.colors(isSystemInDarkTheme()),
            typography = definition.typography, shapes = definition.shapes, content = content)
    }
}
