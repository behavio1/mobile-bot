package one.behavio.mobilebotui.ui

import android.content.Context

object VisualPreferences {
    private const val PREFERENCES = "visual_preferences"
    private const val THEME = "graphic_theme"

    fun theme(context: Context): GraphicTheme = GraphicTheme.fromStoredId(
        preferences(context).getString(THEME, null),
    )

    fun saveTheme(context: Context, theme: GraphicTheme) {
        preferences(context).edit().putString(THEME, theme.storedId).apply()
    }

    fun avatarIndex(context: Context, agentId: String): Int = preferences(context)
        .getInt("agent_avatar.$agentId", defaultAvatarIndex(agentId))
        .coerceIn(1, 15)

    fun saveAvatarIndex(context: Context, agentId: String, avatarIndex: Int): Boolean =
        preferences(context).edit()
            .putInt("agent_avatar.$agentId", avatarIndex.coerceIn(1, 15))
            .commit()

    fun agentOrder(context: Context): List<String> = preferences(context)
        .getString("agent_order", "").orEmpty().split(",").filter { it.isNotEmpty() }

    fun saveAgentOrder(context: Context, ids: List<String>): Boolean = preferences(context).edit()
        .putString("agent_order", ids.joinToString(",")).commit()

    fun hiddenAgents(context: Context): Set<String> = preferences(context)
        .getStringSet("hidden_agents", emptySet()).orEmpty().toSet()

    fun saveHiddenAgents(context: Context, ids: Set<String>): Boolean = preferences(context).edit()
        .putStringSet("hidden_agents", ids).commit()

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private fun defaultAvatarIndex(agentId: String): Int = when (agentId) {
        "starter-atlas" -> 1
        "starter-nova" -> 2
        "starter-echo" -> 3
        "agent-programista" -> 4
        "agent-mail" -> 5
        "agent-shopping" -> 6
        else -> 1
    }
}
