package one.behavio.mobilebotui.automation

import android.content.Context
import java.security.SecureRandom

object DeviceBridgeSecrets {
    private const val PREFERENCES = "device_bridge"
    private const val TOKEN_KEY = "token"

    fun getOrCreate(context: Context): String {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences.getString(TOKEN_KEY, null)?.takeIf { it.length >= 32 }?.let { return it }
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        preferences.edit().putString(TOKEN_KEY, token).apply()
        return token
    }
}
