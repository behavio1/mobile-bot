package one.behavio.mobilebotui.automation

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.net.HttpURLConnection
import java.util.Locale

/**
 * Adds the device token that the Termux host requires on every API route except /health,
 * and the phone language the host uses for its own texts.
 */
object HostApiAuth {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** The host speaks Polish and English; every other phone language gets English. */
    fun hostLanguage(): String {
        // A per-app language chosen in Android settings wins over the system language.
        val context = appContext
        val appLocales = if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
        } else null
        val language = appLocales?.takeUnless { it.isEmpty }?.get(0)?.language
            ?: LocaleList.getDefault().get(0)?.language
            ?: Locale.getDefault().language
        return if (language == "pl") "pl" else "en"
    }

    fun authorize(connection: HttpURLConnection) {
        connection.setRequestProperty("Accept-Language", hostLanguage())
        val context = appContext ?: return
        connection.setRequestProperty("Authorization", "Bearer ${DeviceBridgeSecrets.getOrCreate(context)}")
    }
}
