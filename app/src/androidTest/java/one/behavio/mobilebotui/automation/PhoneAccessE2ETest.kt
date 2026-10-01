package one.behavio.mobilebotui.automation

import android.Manifest
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import one.behavio.mobilebotui.MainActivity
import one.behavio.mobilebotui.setup.TermuxCommandClient
import one.behavio.mobilebotui.setup.TermuxContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real installed Termux + enabled Accessibility required; no fake executor. */
@RunWith(AndroidJUnit4::class)
class PhoneAccessE2ETest {
    @Test
    fun installedApplicationsCanBeListedAndOpenedOnSupportedAndroid() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("Enable the service in Android settings before this test", PhoneAutomationService.isEnabled(context))
        ActivityScenario.launch(MainActivity::class.java).use {
            rebindEnabledPhoneService(context, automation)
            assertTrue(withTimeoutOrNull(20_000) { PhoneAutomationService.connected.first { it } } == true)
            fun call(action: String, packageName: String? = null, query: String? = null): JSONObject {
                val connection = java.net.URL("http://127.0.0.1:8768/v1/actions").openConnection() as java.net.HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 20_000
                    connection.setRequestProperty("Authorization", "Bearer ${DeviceBridgeSecrets.getOrCreate(context)}")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    val body = JSONObject().put("action", action)
                    packageName?.let { body.put("packageName", it) }
                    query?.let { body.put("query", it) }
                    connection.outputStream.use { stream -> stream.write(body.toString().toByteArray()) }
                    return JSONObject(connection.inputStream.bufferedReader().use { reader -> reader.readText() })
                } finally { connection.disconnect() }
            }
            val listed = call("list_apps")
            assertTrue(listed.getBoolean("ok"))
            val apps = listed.getJSONArray("apps")
            assertTrue((0 until apps.length()).any { index -> apps.getJSONObject(index).getString("packageName") == context.packageName })
            val filtered = call("list_apps", query = context.packageName)
            assertEquals(1, filtered.getInt("appCount"))
            assertEquals(context.packageName, filtered.getJSONArray("apps").getJSONObject(0).getString("packageName"))
            assertEquals(apps.length(), filtered.getInt("totalAppCount"))
            assertEquals(0, call("list_apps", query="not-installed-qa-unique").getInt("appCount"))
            val launcher = call("home")
            assertTrue(launcher.toString(), launcher.getBoolean("ok"))
            assertTrue(launcher.has("observation"))
            val opened = call("open_app", context.packageName)
            assertTrue(opened.getBoolean("ok"))
            assertEquals(context.packageName, opened.getJSONObject("observation").getString("packageName"))
            assertEquals("APP_NOT_FOUND", call("open_app", "one.behavio.nonexistent.qa").getString("state"))
        }
    }

    @Test
    fun onboardingProbeReadsPhoneThroughTermuxWithoutReturningScreenContent() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Enable phone Accessibility before this live E2E test", PhoneAutomationService.isEnabled(context))
        uiAutomation.grantRuntimePermission(
            context.packageName, TermuxContract.RUN_COMMAND_PERMISSION,
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            rebindEnabledPhoneService(context, uiAutomation)
            val serviceConnected = withTimeoutOrNull(20_000) {
                PhoneAutomationService.connected.first { connected -> connected }
            } == true
            assertTrue("Enabled phone Accessibility service did not bind", serviceConnected)

            val client = TermuxCommandClient(context)
            assertTrue("Token sync failed", client.syncDeviceBridgeToken().succeeded)
            val result = client.probePhone()
            assertTrue("Termux command failed", result.succeeded)
            val body = JSONObject(result.stdout.trim())
            assertTrue("Screen probe failed: ${body.optString("state")}", body.getBoolean("ok"))
            assertEquals("READY", body.getString("state"))
            assertEquals(setOf("ok", "state"), body.keys().asSequence().toSet())
        }
    }

    private fun rebindEnabledPhoneService(context: Context, uiAutomation: UiAutomation) {
        val resolver = context.contentResolver
        val setting = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val original = Settings.Secure.getString(resolver, setting).orEmpty()
        val component = ComponentName(context, PhoneAutomationService::class.java).flattenToString()
        val entries = original.split(':').filter(String::isNotBlank)
        assertTrue(
            "Phone Accessibility registration disappeared before fixture setup",
            entries.any { it.equals(component, ignoreCase = true) },
        )
        val withoutPhoneService = entries
            .filterNot { it.equals(component, ignoreCase = true) }
            .joinToString(":")

        uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            assertTrue(
                "Could not temporarily remove the phone Accessibility registration",
                Settings.Secure.putString(resolver, setting, withoutPhoneService),
            )
            SystemClock.sleep(500)
        } finally {
            var restored = false
            try {
                restored = Settings.Secure.putString(resolver, setting, original)
            } finally {
                uiAutomation.dropShellPermissionIdentity()
            }
            assertTrue("Could not restore the phone Accessibility registration", restored)
        }
    }
}
