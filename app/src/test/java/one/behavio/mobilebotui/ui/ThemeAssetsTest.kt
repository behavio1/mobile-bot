package one.behavio.mobilebotui.ui

import one.behavio.mobilebotui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThemeAssetsTest {
    @Test
    fun eachThemeExposesFifteenDistinctAvatarResources() {
        GraphicTheme.entries.forEach { theme ->
            val resources = (1..15).map { themeAvatarResource(theme, it) }
            assertEquals("${theme.displayName} should have 15 unique avatars", 15, resources.toSet().size)
        }
    }

    @Test
    fun avatarIndexIsClampedToSupportedRange() {
        GraphicTheme.entries.forEach { theme ->
            assertEquals(themeAvatarResource(theme, 1), themeAvatarResource(theme, -10))
            assertEquals(themeAvatarResource(theme, 15), themeAvatarResource(theme, 100))
        }
    }

    @Test
    fun storedThemeIdsRoundTripAndUnknownValueUsesHeroes() {
        GraphicTheme.entries.forEach { theme ->
            assertSame(theme, GraphicTheme.fromStoredId(theme.storedId))
        }
        assertSame(GraphicTheme.HEROES, GraphicTheme.fromStoredId("unknown"))
    }

    @Test
    fun coreNavigationAssetsDifferAcrossGeneratedThemes() {
        assertNotEquals(themeHomeResource(GraphicTheme.OFFICE), themeHomeResource(GraphicTheme.ANIMALS))
        assertNotEquals(themePowersResource(GraphicTheme.ANIMALS), themePowersResource(GraphicTheme.INFLUENCERS))
    }

    @Test
    fun eachThemeUsesItsOwnSendButtonResource() {
        assertEquals(R.drawable.control_send_pine_comic, themeSendResource(GraphicTheme.HEROES))
        assertEquals(R.drawable.control_send_office_editorial, themeSendResource(GraphicTheme.OFFICE))
        assertEquals(R.drawable.control_send_friendly_animals, themeSendResource(GraphicTheme.ANIMALS))
        assertEquals(R.drawable.control_send_creator_pop, themeSendResource(GraphicTheme.INFLUENCERS))
    }
}
