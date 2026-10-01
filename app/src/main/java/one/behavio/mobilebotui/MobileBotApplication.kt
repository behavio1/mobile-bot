package one.behavio.mobilebotui

import android.app.Application
import one.behavio.mobilebotui.automation.HostApiAuth

class MobileBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        HostApiAuth.init(this)
    }
}
