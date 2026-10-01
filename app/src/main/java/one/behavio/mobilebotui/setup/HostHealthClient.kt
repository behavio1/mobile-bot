package one.behavio.mobilebotui.setup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import one.behavio.mobilebotui.automation.HostApiAuth
import java.net.HttpURLConnection
import java.net.URL

class HostHealthClient {
    suspend fun read(): HostStatus = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(HEALTH_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 1_500
            connection.readTimeout = 1_500
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (connection.responseCode != 200) return@runCatching HostStatus(reachable = false)
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val codex = json.optJSONObject("codex")
                val runtime = json.optJSONObject("runtime")
                val selectedRuntime = runtime?.optString("selected")
                    ?.let { value -> RuntimeKind.entries.firstOrNull { it.wireValue == value } }
                    ?: RuntimeKind.CODEX
                val localAi = runtime?.optJSONObject("localAi")
                // JSONObject.optString returns the literal string "null" for a
                // JSON null on some Android releases. Keep nullable health
                // fields genuinely nullable so a clean not_installed state does
                // not appear as a preparation failure and modelPath=null is not
                // mistaken for an installed model.
                fun nullableString(value: Any?): String? =
                    (value as? String)?.takeUnless { it.isBlank() || it == "null" }
                HostStatus(
                    reachable = true,
                    protocolVersion = json.optInt("protocolVersion"),
                    environmentRevision = json.optInt("environmentRevision"),
                    hostVersion = nullableString(json.opt("hostVersion")),
                    codexInstalled = codex?.optBoolean("installed", false) ?: false,
                    codexVersion = nullableString(codex?.opt("version")),
                    codexAuthenticated = codex?.optBoolean("authenticated", false) ?: false,
                    selectedRuntime = selectedRuntime,
                    localAiStatus = nullableString(localAi?.opt("status")),
                    localAiModelInstalled = nullableString(localAi?.opt("modelPath")) != null,
                    localAiReady = nullableString(localAi?.opt("status")) == "ready",
                    localAiBytesDownloaded = localAi?.optLong("bytesDownloaded", 0) ?: 0,
                    localAiTotalBytes = localAi?.optLong("totalBytes", 0) ?: 0,
                    localAiErrorCode = nullableString(localAi?.opt("errorCode")),
                )
            } finally {
                connection.disconnect()
            }
        }.getOrElse { HostStatus(reachable = false) }
    }

    suspend fun selectRuntime(runtime: RuntimeKind): Boolean = withContext(Dispatchers.IO) {
        request("PUT", "/runtime", JSONObject().put("selected", runtime.wireValue))
    }

    suspend fun prepareLocalAi(): Boolean = withContext(Dispatchers.IO) {
        request("POST", "/runtime/local/prepare", null)
    }

    private fun request(method: String, path: String, body: JSONObject?): Boolean {
        val connection = URL("${BASE_URL}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.setRequestProperty("Accept", "application/json")
        HostApiAuth.authorize(connection)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        return try {
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val HEALTH_URL = "http://127.0.0.1:8767/health"
        private const val BASE_URL = "http://127.0.0.1:8767"
    }
}
