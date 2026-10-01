package one.behavio.mobilebotui.ui

import androidx.annotation.DrawableRes

@DrawableRes
fun themeAvatarResource(theme: GraphicTheme, avatarIndex: Int): Int =
    theme.definition.artwork.avatars[avatarIndex.coerceIn(1, 15) - 1]
@DrawableRes
fun themeHomeResource(theme: GraphicTheme): Int = theme.definition.artwork.home
@DrawableRes
fun themeConversationsResource(theme: GraphicTheme): Int = theme.definition.artwork.conversations
@DrawableRes
fun themePowersResource(theme: GraphicTheme): Int = theme.definition.artwork.powers
@DrawableRes
fun themeFilesResource(theme: GraphicTheme): Int = theme.definition.artwork.files
@DrawableRes
fun themeSendResource(theme: GraphicTheme): Int = theme.definition.artwork.send
