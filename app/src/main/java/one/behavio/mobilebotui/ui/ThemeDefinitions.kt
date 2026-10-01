package one.behavio.mobilebotui.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.behavio.mobilebotui.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

data class GraphicTheme(val storedId: String, val displayName: String, val description: String,
    val customDefinition: ThemeDefinition? = null,
    @StringRes val nameRes: Int? = null, @StringRes val descriptionRes: Int? = null) {
    val label: String @Composable get() = nameRes?.let { stringResource(it) } ?: displayName
    val summary: String @Composable get() = descriptionRes?.let { stringResource(it) } ?: description
    companion object {
        val HEROES = GraphicTheme("heroes", "Superheroes", "A comic-book base full of energy",
            nameRes = R.string.theme_heroes, descriptionRes = R.string.theme_heroes_description)
        val OFFICE = GraphicTheme("office", "Office", "A warm, well-organized team",
            nameRes = R.string.theme_office, descriptionRes = R.string.theme_office_description)
        val ANIMALS = GraphicTheme("animals", "Animals", "A friendly, cheerful crew",
            nameRes = R.string.theme_animals, descriptionRes = R.string.theme_animals_description)
        val INFLUENCERS = GraphicTheme("influencers", "Influencers", "A colorful world of creators",
            nameRes = R.string.theme_influencers, descriptionRes = R.string.theme_influencers_description)
        val builtIns = listOf(HEROES, OFFICE, ANIMALS, INFLUENCERS)
        val entries = androidx.compose.runtime.mutableStateListOf<GraphicTheme>().apply { addAll(builtIns) }
        fun fromStoredId(value: String?): GraphicTheme = entries.firstOrNull { it.storedId == value } ?: HEROES
    }
}

// The complete visual contract. Keep theme-specific choices in this file.
data class ThemeArtwork(val avatars: List<Int>, val home: Int, val conversations: Int,
    val powers: Int, val files: Int, val send: Int, val guide: Int)
enum class CardTreatment { FOIL, EDITORIAL, SOFT, POP }
data class AgentCardStyle(val treatment: CardTreatment, val radius: Dp, val border: Dp,
    val elevation: Dp, val inset: Dp, val gridGap: Dp)
data class ThemeDefinition(val light: ColorScheme, val dark: ColorScheme,
    val typography: Typography, val shapes: Shapes, val card: AgentCardStyle,
    val artwork: ThemeArtwork) {
    fun colors(darkMode: Boolean) = if (darkMode) dark else light
}
val LocalThemeDefinition = staticCompositionLocalOf { GraphicTheme.HEROES.definition }

internal fun themeTypography(family: FontFamily, headingWeight: FontWeight, tracking: Float): Typography {
    fun heading(size: Int, line: Int) = TextStyle(fontFamily = family, fontWeight = headingWeight,
        fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)
    fun body(size: Int, line: Int) = TextStyle(fontFamily = FontFamily.SansSerif,
        fontSize = size.sp, lineHeight = line.sp)
    return Typography(
        displayLarge = heading(44, 52), displayMedium = heading(38, 46), displaySmall = heading(34, 42),
        headlineLarge = heading(30, 38), headlineMedium = heading(27, 34), headlineSmall = heading(24, 31),
        titleLarge = heading(22, 29), titleMedium = heading(18, 24), titleSmall = heading(16, 22),
        bodyLarge = body(16, 24), bodyMedium = body(14, 21), bodySmall = body(12, 18),
        labelLarge = body(14, 20).copy(fontWeight = FontWeight.SemiBold),
        labelMedium = body(12, 18).copy(fontWeight = FontWeight.SemiBold),
        labelSmall = body(11, 16).copy(fontWeight = FontWeight.Medium))
}
internal fun themeShapes(small: Int, medium: Int, large: Int) = Shapes(
    extraSmall = RoundedCornerShape((small / 2).dp), small = RoundedCornerShape(small.dp),
    medium = RoundedCornerShape(medium.dp), large = RoundedCornerShape(large.dp),
    extraLarge = RoundedCornerShape((large + 6).dp))

// Every surface role is explicit, so Material controls cannot leak the default purple palette.
internal fun themePalette(dark: Boolean, primary: Long, container: Long, accent: Long,
    background: Long, surface: Long, raised: Long, ink: Long, muted: Long): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val text = Color(ink)
    val onAccent = Color(0xFF201923)
    return base.copy(
        primary = Color(primary), onPrimary = if (dark) onAccent else Color.White,
        primaryContainer = Color(container), onPrimaryContainer = text,
        secondary = Color(accent), onSecondary = onAccent,
        secondaryContainer = Color(container), onSecondaryContainer = text,
        tertiary = Color(primary), onTertiary = if (dark) onAccent else Color.White,
        tertiaryContainer = Color(container), onTertiaryContainer = text,
        background = Color(background), onBackground = text,
        surface = Color(surface), onSurface = text,
        surfaceVariant = Color(raised), onSurfaceVariant = Color(muted),
        surfaceDim = Color(background), surfaceBright = Color(raised),
        surfaceContainerLowest = Color(background), surfaceContainerLow = Color(surface),
        surfaceContainer = Color(surface), surfaceContainerHigh = Color(raised),
        surfaceContainerHighest = Color(raised), surfaceTint = Color(primary),
        outline = Color(muted), outlineVariant = Color(muted).copy(alpha = 0.35f),
        inverseSurface = if (dark) Color(0xFFF5F2F7) else Color(0xFF242129),
        inverseOnSurface = if (dark) Color(0xFF242129) else Color(0xFFF5F2F7),
        inversePrimary = if (dark) Color(container) else Color(accent))
}

private val heroAvatars = listOf(
    R.drawable.theme_heroes_avatar_01, R.drawable.theme_heroes_avatar_02, R.drawable.theme_heroes_avatar_03,
    R.drawable.theme_heroes_avatar_04, R.drawable.theme_heroes_avatar_05, R.drawable.theme_heroes_avatar_06,
    R.drawable.theme_heroes_avatar_07, R.drawable.theme_heroes_avatar_08, R.drawable.theme_heroes_avatar_09,
    R.drawable.theme_heroes_avatar_10, R.drawable.theme_heroes_avatar_11, R.drawable.theme_heroes_avatar_12,
    R.drawable.theme_heroes_avatar_13, R.drawable.theme_heroes_avatar_14, R.drawable.theme_heroes_avatar_15,
)
private val officeAvatars = listOf(
    R.drawable.theme_office_avatar_01, R.drawable.theme_office_avatar_02, R.drawable.theme_office_avatar_03,
    R.drawable.theme_office_avatar_04, R.drawable.theme_office_avatar_05, R.drawable.theme_office_avatar_06,
    R.drawable.theme_office_avatar_07, R.drawable.theme_office_avatar_08, R.drawable.theme_office_avatar_09,
    R.drawable.theme_office_avatar_10, R.drawable.theme_office_avatar_11, R.drawable.theme_office_avatar_12,
    R.drawable.theme_office_avatar_13, R.drawable.theme_office_avatar_14, R.drawable.theme_office_avatar_15,
)
private val animalAvatars = listOf(
    R.drawable.theme_animals_avatar_01, R.drawable.theme_animals_avatar_02, R.drawable.theme_animals_avatar_03,
    R.drawable.theme_animals_avatar_04, R.drawable.theme_animals_avatar_05, R.drawable.theme_animals_avatar_06,
    R.drawable.theme_animals_avatar_07, R.drawable.theme_animals_avatar_08, R.drawable.theme_animals_avatar_09,
    R.drawable.theme_animals_avatar_10, R.drawable.theme_animals_avatar_11, R.drawable.theme_animals_avatar_12,
    R.drawable.theme_animals_avatar_13, R.drawable.theme_animals_avatar_14, R.drawable.theme_animals_avatar_15,
)
private val influencerAvatars = listOf(
    R.drawable.theme_influencers_avatar_01, R.drawable.theme_influencers_avatar_02, R.drawable.theme_influencers_avatar_03,
    R.drawable.theme_influencers_avatar_04, R.drawable.theme_influencers_avatar_05, R.drawable.theme_influencers_avatar_06,
    R.drawable.theme_influencers_avatar_07, R.drawable.theme_influencers_avatar_08, R.drawable.theme_influencers_avatar_09,
    R.drawable.theme_influencers_avatar_10, R.drawable.theme_influencers_avatar_11, R.drawable.theme_influencers_avatar_12,
    R.drawable.theme_influencers_avatar_13, R.drawable.theme_influencers_avatar_14, R.drawable.theme_influencers_avatar_15,
)


private val definitions: Map<GraphicTheme, ThemeDefinition> = mapOf(
    GraphicTheme.HEROES to ThemeDefinition(
        light = themePalette(false, 0xFF173C37, 0xFFD5EAE4, 0xFFF2C85B, 0xFFF7F6F2, 0xFFFFFEFA, 0xFFE9EFEB, 0xFF17211F, 0xFF4E625C),
        dark = themePalette(true, 0xFF9FD4CA, 0xFF28504A, 0xFFF2C85B, 0xFF0C1915, 0xFF172B23, 0xFF284035, 0xFFF0F5F2, 0xFFBBCDC4),
        typography = themeTypography(FontFamily.SansSerif, FontWeight.ExtraBold, 0.1f), shapes = themeShapes(10, 14, 20),
        card = AgentCardStyle(CardTreatment.FOIL, 18.dp, 1.dp, 5.dp, 5.dp, 14.dp),
        artwork = ThemeArtwork(heroAvatars, R.drawable.navigation_home, R.drawable.conversations_comic,
            R.drawable.trail_skills, R.drawable.agent_file_folder, R.drawable.control_send_pine_comic, R.drawable.atlas_guide_friendly)),
    GraphicTheme.OFFICE to ThemeDefinition(
        light = themePalette(false, 0xFF234C8A, 0xFFDFE9F9, 0xFFAAC9F2, 0xFFF0F3F8, 0xFFFFFFFF, 0xFFE6ECF5, 0xFF17263E, 0xFF4F6078),
        dark = themePalette(true, 0xFFA9C9FF, 0xFF263F65, 0xFFB6CEF0, 0xFF101827, 0xFF192439, 0xFF26364F, 0xFFEFF4FF, 0xFFBFCCE0),
        typography = themeTypography(FontFamily.Serif, FontWeight.SemiBold, 0.0f), shapes = themeShapes(4, 8, 12),
        card = AgentCardStyle(CardTreatment.EDITORIAL, 8.dp, 1.dp, 1.dp, 0.dp, 16.dp),
        artwork = ThemeArtwork(officeAvatars, R.drawable.theme_office_home, R.drawable.theme_office_conversations,
            R.drawable.theme_office_powers, R.drawable.theme_office_files, R.drawable.control_send_office_editorial, R.drawable.theme_office_guide)),
    GraphicTheme.ANIMALS to ThemeDefinition(
        light = themePalette(false, 0xFF97472C, 0xFFF9DFCB, 0xFFF3BD64, 0xFFFFF5E5, 0xFFFFFCF4, 0xFFF3E5D2, 0xFF3D291E, 0xFF735846),
        dark = themePalette(true, 0xFFF4B496, 0xFF63412F, 0xFFF6CE80, 0xFF261B15, 0xFF35261D, 0xFF49362A, 0xFFFFF3E5, 0xFFD9C2AF),
        typography = themeTypography(FontFamily.SansSerif, FontWeight.Bold, 0.3f), shapes = themeShapes(16, 24, 30),
        card = AgentCardStyle(CardTreatment.SOFT, 30.dp, 2.dp, 2.dp, 5.dp, 14.dp),
        artwork = ThemeArtwork(animalAvatars, R.drawable.theme_animals_home, R.drawable.theme_animals_conversations,
            R.drawable.theme_animals_powers, R.drawable.theme_animals_files, R.drawable.control_send_friendly_animals, R.drawable.theme_animals_guide)),
    GraphicTheme.INFLUENCERS to ThemeDefinition(
        light = themePalette(false, 0xFF752CB8, 0xFFF0DDFB, 0xFFF7AFD8, 0xFFFCF3FF, 0xFFFFFAFF, 0xFFF0E2F5, 0xFF30133F, 0xFF71567B),
        dark = themePalette(true, 0xFFE0AEFF, 0xFF512169, 0xFFFFB3DB, 0xFF210D2C, 0xFF32163F, 0xFF492450, 0xFFFFF0FF, 0xFFDDBFE3),
        typography = themeTypography(FontFamily.SansSerif, FontWeight.Black, -0.4f), shapes = themeShapes(8, 18, 26),
        card = AgentCardStyle(CardTreatment.POP, 24.dp, 2.dp, 4.dp, 3.dp, 14.dp),
        artwork = ThemeArtwork(influencerAvatars, R.drawable.theme_influencers_home, R.drawable.theme_influencers_conversations,
            R.drawable.theme_influencers_powers, R.drawable.theme_influencers_files, R.drawable.control_send_creator_pop, R.drawable.theme_influencers_guide)),
)

val GraphicTheme.definition: ThemeDefinition get() = customDefinition ?: definitions.getValue(this)

// Onboarding roles share the active palette; completion remains semantically green.
internal val TrailBackground: Color @Composable get() = MaterialTheme.colorScheme.background
internal val TrailText: Color @Composable get() = MaterialTheme.colorScheme.onBackground
internal val TrailMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val TrailBlue: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
internal val TrailBlueBorder: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val TrailBlueShadow: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
internal val TrailYellow: Color @Composable get() = MaterialTheme.colorScheme.secondary
internal val TrailYellowBorder: Color @Composable get() = MaterialTheme.colorScheme.outline
internal val TrailYellowShadow: Color @Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
internal val TrailGreen = Color(0xFFA7D99B)
internal val TrailGreenBorder = Color(0xFF80B874)
internal val TrailGreenShadow = Color(0xFF467841)
internal val Bubble: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
internal val BubbleInk: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
internal val BubbleMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
