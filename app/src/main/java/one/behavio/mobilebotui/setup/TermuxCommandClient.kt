package one.behavio.mobilebotui.setup

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import one.behavio.mobilebotui.BuildConfig
import one.behavio.mobilebotui.automation.DevelopmentActionLogger
import one.behavio.mobilebotui.automation.DevelopmentActions
import one.behavio.mobilebotui.automation.ActionCorrelation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID
import one.behavio.mobilebotui.automation.DeviceBridgeSecrets
import androidx.annotation.StringRes
import one.behavio.mobilebotui.R
import one.behavio.mobilebotui.automation.HostApiAuth

private data class StagedBootstrapAsset(
    val fileName: String,
    val bytes: ByteArray,
    val mode: String,
)

class TermuxCommandClient(private val context: Context) {
    private val actionLog = DevelopmentActionLogger.get(context)

    suspend fun probe(): TermuxCommandResult = execute(
        script = "printf 'mobile-bot-ui-bridge-ready\\n'",
        operation = "probe",
        label = "Mobile Bot — connection test",
        timeoutMillis = 20_000,
    )

    /** Read the screen through the same authenticated helper used by agents. Return no screen content. */
    suspend fun probePhone(): TermuxCommandResult = execute(
        script = """
            node - <<'PHONE_PROBE'
            const { execFileSync } = require('node:child_process');
            try {
                const result = JSON.parse(execFileSync(process.execPath,
                    [process.env.HOME + '/.mobile-bot-ui/phone-client.mjs', 'observe'],
                    { encoding: 'utf8', timeout: 25000 }));
                const nodes = result.observation?.nodes ?? [];
                const ready = result.ok === true && result.state === 'READY' && nodes.length > 0;
                console.log(JSON.stringify({ ok: ready, state: ready ? 'READY' :
                    (result.state === 'READY' ? 'NO_READABLE_CONTENT' : result.state) }));
            } catch (_) {
                console.log(JSON.stringify({ ok: false, state: 'PHONE_PROBE_FAILED' }));
            }
            PHONE_PROBE
        """.trimIndent(),
        operation = "probe_phone",
        label = "Mobile Bot — screen reading check",
        timeoutMillis = 30_000,
    )

    suspend fun bootstrap(runtime: RuntimeKind = RuntimeKind.CODEX, onProgress: (String) -> Unit = {}): TermuxCommandResult {
        val bootstrapId = UUID.randomUUID().toString()
        val setupTrace = ActionCorrelation(traceId = bootstrapId)
        actionLog.record(DevelopmentActions.SETUP_PREPARATION, mapOf("phase" to "staging"), setupTrace)
        val template = context.assets.open("bootstrap.sh").bufferedReader().use { it.readText() }
        val stagedAssets = listOf(
            StagedBootstrapAsset("host.mjs", context.assets.open("host.mjs").use { it.readBytes() }, "700"),
            StagedBootstrapAsset("generatedSkills.js", context.assets.open("generatedSkills.js").use { it.readBytes() }, "600"),
            StagedBootstrapAsset("themePackages.js", context.assets.open("themePackages.js").use { it.readBytes() }, "600"),
            StagedBootstrapAsset("locale.js", context.assets.open("locale.js").use { it.readBytes() }, "600"),
            StagedBootstrapAsset("skillWorkshop.js", context.assets.open("skillWorkshop.js").use { it.readBytes() }, "600"),
            StagedBootstrapAsset("localAi.js", context.assets.open("localAi.js").use { it.readBytes() }, "600"),
            StagedBootstrapAsset("piRuntime.js", context.assets.open("piRuntime.js").use { it.readBytes() }, "600"),
            StagedBootstrapAsset("phone-client.mjs", context.assets.open("phone-client.mjs").use { it.readBytes() }, "700"),
            StagedBootstrapAsset("automation-client.mjs", context.assets.open("automation-client.mjs").use { it.readBytes() }, "700"),
            StagedBootstrapAsset(
                "device-token",
                DeviceBridgeSecrets.getOrCreate(context).toByteArray(Charsets.UTF_8),
                "600",
            ),
        )
        onProgress(context.getString(R.string.setup_preparing_files))
        val prepareResult = execute(
            script = """
                set -eu
                stage_root="${'$'}HOME/.mobile-bot-ui/bootstrap-stage"
                rm -rf "${'$'}stage_root"
                mkdir -p "${'$'}stage_root/$bootstrapId"
                chmod 700 "${'$'}stage_root" "${'$'}stage_root/$bootstrapId"
            """.trimIndent(),
            operation = "bootstrap_stage_prepare",
            label = "Mobile Bot — preparing files",
            timeoutMillis = 20_000,
        )
        if (!prepareResult.succeeded) return prepareResult
        for ((index, asset) in stagedAssets.withIndex()) {
            onProgress(context.getString(R.string.setup_preparing_files_progress, index + 1, stagedAssets.size))
            val uploadResult = stageBootstrapAsset(bootstrapId, asset)
            if (!uploadResult.succeeded) return uploadResult
        }
        val script = "export MOBILE_BOT_RUNTIME_SELECTION='${runtime.wireValue}'\n" + template
            .replace("__CODEX_VERSION__", TermuxContract.CODEX_VERSION)
            .replace("__HOST_VERSION__", TermuxContract.HOST_VERSION)
            .replace("__BOOTSTRAP_ID__", bootstrapId)
        onProgress(context.getString(R.string.setup_checking_components))
        return coroutineScope {
            val progress = launch {
                var previous: String? = null
                while (true) {
                    delay(2_000)
                    try {
                        val rawStatus = readPreparationStatus()
                        if (rawStatus == "$bootstrapId:interrupted") throw BootstrapInterruptedException()
                        val detail = bootstrapProgressDetail(rawStatus, bootstrapId)?.let(context::getString)
                        if (detail != null && detail != previous) {
                            actionLog.record(DevelopmentActions.SETUP_PREPARATION,
                                mapOf("phase" to rawStatus.removePrefix("$bootstrapId:")), setupTrace)
                            onProgress(detail)
                            previous = detail
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException || error is BootstrapInterruptedException) throw error
                        // A delayed status read must not cancel the installation command.
                    }
                }
            }
            try {
                execute(
                    script = script,
                    operation = "bootstrap",
                    label = "Mobile Bot — app setup",
                    timeoutMillis = 15 * 60_000L,
                )
            } finally {
                progress.cancelAndJoin()
            }
        }
    }

    suspend fun activePreparationDetail(): String? {
        val raw = readPreparationStatus()
        if (raw.substringAfter(":") in setOf("complete", "failed", "interrupted")) return null
        val id = raw.substringBefore(":", "")
        return if (id.isNotBlank()) (bootstrapProgressDetail(raw, id) ?: R.string.setup_in_progress).let(context::getString) else null
    }

    private suspend fun readPreparationStatus(): String {
        val result = execute(
            script = """
                root="${'$'}HOME/.mobile-bot-ui"
                status=${'$'}(cat "${'$'}root/bootstrap.status" 2>/dev/null || true)
                case "${'$'}status" in *:complete|*:failed|'') printf '%s' "${'$'}status"; exit 0 ;; esac
                alive=false
                if [ -r "${'$'}root/bootstrap.active" ]; then
                  read -r pid boot ticks id <"${'$'}root/bootstrap.active"
                  case "${'$'}pid" in *[!0-9]*|'') pid=0 ;; esac
                  if [ "${'$'}boot" = "${'$'}(cat /proc/sys/kernel/random/boot_id)" ] && [ -r "/proc/${'$'}pid/stat" ]; then
                    current_ticks=${'$'}(awk '{print ${'$'}22}' "/proc/${'$'}pid/stat")
                    process_state=${'$'}(awk '{print ${'$'}3}' "/proc/${'$'}pid/stat")
                    if [ "${'$'}ticks" = "${'$'}current_ticks" ] && [ "${'$'}process_state" != Z ]; then alive=true; fi
                  fi
                fi
                if [ "${'$'}alive" = true ]; then printf '%s' "${'$'}status"
                else printf '%s:interrupted' "${'$'}{status%%:*}"; fi
            """.trimIndent(),
            operation = "bootstrap_active_check", label = "Mobile Bot — setup status",
            timeoutMillis = 10_000, logLifecycle = false,
        )
        return result.stdout.trim()
    }

    private suspend fun stageBootstrapAsset(
        bootstrapId: String,
        asset: StagedBootstrapAsset,
    ): TermuxCommandResult {
        val encoded = android.util.Base64.encodeToString(asset.bytes, android.util.Base64.NO_WRAP)
        val encodedChunks = encoded.chunked(64_000)
        var latestResult: TermuxCommandResult? = null
        for ((index, chunk) in encodedChunks.withIndex()) {
            latestResult = execute(
                script = """
                    set -eu
                    target="${'$'}HOME/.mobile-bot-ui/bootstrap-stage/$bootstrapId/${asset.fileName}.b64"
                    ${if (index == 0) ": > \"${'$'}target\"" else ":"}
                    printf '%s' '$chunk' >>"${'$'}target"
                """.trimIndent(),
                operation = "bootstrap_stage_upload_${asset.fileName}",
                label = "Mobile Bot — copying files",
                timeoutMillis = 20_000,
            )
            if (!latestResult.succeeded) return latestResult
        }
        return execute(
            script = """
                set -eu
                stage="${'$'}HOME/.mobile-bot-ui/bootstrap-stage/$bootstrapId"
                temporary="${'$'}stage/${asset.fileName}.tmp"
                base64 --decode "${'$'}stage/${asset.fileName}.b64" >"${'$'}temporary"
                test "${'$'}(wc -c <"${'$'}temporary" | tr -d ' ')" = "${asset.bytes.size}"
                mv "${'$'}temporary" "${'$'}stage/${asset.fileName}"
                chmod ${asset.mode} "${'$'}stage/${asset.fileName}"
                rm -f "${'$'}stage/${asset.fileName}.b64"
            """.trimIndent(),
            operation = "bootstrap_stage_verify_${asset.fileName}",
            label = "Mobile Bot — checking files",
            timeoutMillis = 20_000,
        )
    }

    suspend fun syncDeviceBridgeToken(): TermuxCommandResult {
        val tokenBase64 = android.util.Base64.encodeToString(
            DeviceBridgeSecrets.getOrCreate(context).toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        return execute(
            script = """
                set -eu
                target="${'$'}HOME/.mobile-bot-ui/device-token"
                temporary="${'$'}{target}.tmp.${'$'}${'$'}"
                trap 'rm -f "${'$'}temporary"' EXIT HUP INT TERM
                mkdir -p "${'$'}HOME/.mobile-bot-ui"
                printf '%s' '$tokenBase64' | base64 --decode >"${'$'}temporary"
                chmod 600 "${'$'}temporary"
                mv "${'$'}temporary" "${'$'}target"
                chmod 600 "${'$'}target"
                trap - EXIT HUP INT TERM
                printf 'device_bridge_token_synced\n'
            """.trimIndent(),
            operation = "sync_device_bridge_token",
            label = "Mobile Bot — phone connection",
            timeoutMillis = 20_000,
        )
    }

    suspend fun startCodexLogin(): TermuxCommandResult = execute(
        script = "",
        operation = "codex_login",
        label = "Mobile Bot — Codex sign-in",
        background = false,
        timeoutMillis = 15 * 60_000L,
        arguments = arrayOf("-lc", "exec \"\$HOME/.mobile-bot-ui/codex-device-login.sh\""),
    )

    suspend fun startHost() = withContext(Dispatchers.Main) {
        val verbose = if (BuildConfig.DEBUG) "1" else "0"
        val requestId = UUID.randomUUID().toString()
        val correlation = ActionCorrelation(traceId = requestId, actionId = requestId)
        val command = """
            host="${'$'}HOME/.mobile-bot-ui/host.mjs"
            local_ai_server="${'$'}HOME/.mobile-bot-ui/local-ai/bin/llama-server"
            pid_file="${'$'}HOME/.mobile-bot-ui/host.pid"
            current_boot_id=${'$'}(cat /proc/sys/kernel/random/boot_id 2>/dev/null || true)
            stop_runtime_pid() {
              target_pid="${'$'}1"
              stop_mode="${'$'}{2:-graceful}"
              case "${'$'}target_pid" in
                *[!0-9]*|'') return 0 ;;
              esac
              if [ "${'$'}stop_mode" = force ]; then
                kill -KILL "${'$'}target_pid" 2>/dev/null || true
                return 0
              fi
              kill -TERM "${'$'}target_pid" 2>/dev/null || return 0
              for _ in 1 2 3 4 5; do
                kill -0 "${'$'}target_pid" 2>/dev/null || return 0
                sleep 1
              done
              kill -KILL "${'$'}target_pid" 2>/dev/null || true
            }
            for process_dir in /proc/[0-9]*; do
              [ -r "${'$'}process_dir/cmdline" ] || continue
              pid=${'$'}{process_dir##*/}
              [ "${'$'}pid" = "${'$'}${'$'}" ] && continue
              args=${'$'}(tr '\000' ' ' <"${'$'}process_dir/cmdline" 2>/dev/null || true)
              case "${'$'}args" in
                *"${'$'}local_ai_server"*) stop_runtime_pid "${'$'}pid" force ;;
              esac
            done
            if [ -r "${'$'}pid_file" ]; then
              read -r recorded_pid recorded_boot_id <"${'$'}pid_file" || true
              case "${'$'}{recorded_pid:-}" in
                *[!0-9]*|'') ;;
                *)
                  if [ -z "${'$'}{recorded_boot_id:-}" ] || [ "${'$'}recorded_boot_id" = "${'$'}current_boot_id" ]; then
                    stop_runtime_pid "${'$'}recorded_pid"
                  fi
                  ;;
              esac
            fi
            rm -f "${'$'}pid_file"
            for process_dir in /proc/[0-9]*; do
              [ -r "${'$'}process_dir/cmdline" ] || continue
              pid=${'$'}{process_dir##*/}
              [ "${'$'}pid" = "${'$'}${'$'}" ] && continue
              args=${'$'}(tr '\000' ' ' <"${'$'}process_dir/cmdline" 2>/dev/null || true)
              case "${'$'}args" in
                *"${'$'}host"*) stop_runtime_pid "${'$'}pid" ;;
                *"${'$'}local_ai_server"*) stop_runtime_pid "${'$'}pid" force ;;
              esac
            done
            sleep 1
            printf '%s %s\n' "${'$'}${'$'}" "${'$'}current_boot_id" >"${'$'}pid_file.tmp"
            mv "${'$'}pid_file.tmp" "${'$'}pid_file"
            chmod 600 "${'$'}pid_file"
            exec env MOBILE_BOT_VERBOSE_LOGS='$verbose' \
              MOBILE_BOT_LOCALE='${HostApiAuth.hostLanguage()}' \
              MOBILE_BOT_ACTION_LOG_PATH="${'$'}HOME/.mobile-bot-ui/host-development-actions.jsonl" \
              MOBILE_BOT_HOST_START_TRACE_ID='$requestId' \
              MOBILE_BOT_HOST_START_ACTION_ID='$requestId' \
              node "${'$'}host"
        """.trimIndent()
        actionLog.record(
            DevelopmentActions.HOST_START_REQUESTED,
            mapOf(
                "requestId" to requestId,
                "hostVersion" to TermuxContract.HOST_VERSION,
                "verboseLogs" to BuildConfig.DEBUG,
            ),
            correlation,
        )
        val intent = Intent(TermuxContract.ACTION_RUN_COMMAND).apply {
            component = ComponentName(
                TermuxContract.PACKAGE_NAME,
                TermuxContract.RUN_COMMAND_SERVICE,
            )
            putExtra(TermuxContract.EXTRA_COMMAND_PATH, TermuxContract.BASH)
            putExtra(TermuxContract.EXTRA_ARGUMENTS, arrayOf("-lc", command))
            putExtra(TermuxContract.EXTRA_WORKDIR, TermuxContract.HOME)
            putExtra(TermuxContract.EXTRA_BACKGROUND, true)
            putExtra(TermuxContract.EXTRA_COMMAND_LABEL, "Mobile Bot — local host")
            putExtra(
                TermuxContract.EXTRA_COMMAND_DESCRIPTION,
                "Keeps the app connected to Codex on this phone.",
            )
        }
        context.startForegroundService(intent)
    }

    private suspend fun execute(
        script: String,
        operation: String,
        label: String,
        background: Boolean = true,
        timeoutMillis: Long,
        arguments: Array<String> = arrayOf("-s"),
        logLifecycle: Boolean = true,
    ): TermuxCommandResult = coroutineScope {
        val requestId = UUID.randomUUID().toString()
        val correlation = ActionCorrelation(traceId = requestId, actionId = requestId)
        val startedAtNanos = System.nanoTime()
        if (logLifecycle) {
            actionLog.record(
                DevelopmentActions.TERMUX_COMMAND_STARTED,
                mapOf(
                    "requestId" to requestId,
                    "operation" to operation,
                    "background" to background,
                    "timeoutMillis" to timeoutMillis,
                ),
                correlation,
            )
        }
        val resultIntent = Intent(context, TermuxResultReceiver::class.java)
            .putExtra(TermuxResultReceiver.EXTRA_REQUEST_ID, requestId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val command = Intent(TermuxContract.ACTION_RUN_COMMAND).apply {
            component = ComponentName(
                TermuxContract.PACKAGE_NAME,
                TermuxContract.RUN_COMMAND_SERVICE,
            )
            putExtra(TermuxContract.EXTRA_COMMAND_PATH, TermuxContract.BASH)
            putExtra(TermuxContract.EXTRA_ARGUMENTS, arguments)
            putExtra(TermuxContract.EXTRA_STDIN, script)
            putExtra(TermuxContract.EXTRA_WORKDIR, TermuxContract.HOME)
            putExtra(TermuxContract.EXTRA_BACKGROUND, background)
            putExtra(TermuxContract.EXTRA_PENDING_INTENT, pendingIntent)
            putExtra(TermuxContract.EXTRA_COMMAND_LABEL, label)
            putExtra(
                TermuxContract.EXTRA_COMMAND_DESCRIPTION,
                "Command started by the Mobile Bot app at the user's request.",
            )
        }

        val waiting = async { TermuxResultBus.await(requestId, timeoutMillis) }
        withContext(Dispatchers.Main) {
            // Android 16 blocks a normal cross-app service start when Termux is
            // in the background. RunCommandService promotes itself immediately.
            context.startForegroundService(command)
            if (!background) {
                context.packageManager.getLaunchIntentForPackage(TermuxContract.PACKAGE_NAME)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
        }
        try {
            waiting.await().also { result ->
                if (logLifecycle) {
                    actionLog.record(
                        DevelopmentActions.TERMUX_COMMAND_FINISHED,
                        mapOf(
                            "requestId" to requestId,
                            "operation" to operation,
                            "succeeded" to result.succeeded,
                            "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
                            "exitCode" to result.exitCode,
                            "errorCode" to result.errorCode?.takeUnless { result.succeeded && it == -1 },
                        ),
                        correlation,
                    )
                }
            }
        } catch (error: Throwable) {
            if (logLifecycle) {
                actionLog.record(
                    DevelopmentActions.TERMUX_COMMAND_FINISHED,
                    mapOf(
                        "requestId" to requestId,
                        "operation" to operation,
                        "succeeded" to false,
                        "durationMillis" to (System.nanoTime() - startedAtNanos) / 1_000_000,
                        "errorType" to error.javaClass.simpleName,
                        "errorCode" to when (error) {
                            is kotlinx.coroutines.TimeoutCancellationException -> "termux_command_timeout"
                            else -> "termux_command_failed"
                        },
                    ),
                    correlation,
                )
            }
            throw error
        }
    }
}

@StringRes
internal fun bootstrapProgressDetail(rawStatus: String, bootstrapId: String): Int? {
    val status = rawStatus.trim()
    val prefix = "$bootstrapId:"
    if (!status.startsWith(prefix)) return null
    return when (status.removePrefix(prefix)) {
        "runtime_packages" -> R.string.bootstrap_runtime_packages
        "runtime_packages_retry" -> R.string.bootstrap_runtime_packages_retry
        "codex_check" -> R.string.bootstrap_codex_check
        "codex" -> R.string.bootstrap_codex
        "pi" -> R.string.bootstrap_pi
        "local_engine" -> R.string.bootstrap_local_engine
        "mobile_bot_files" -> R.string.bootstrap_mobile_bot_files
        "checking" -> R.string.bootstrap_checking
        "complete" -> R.string.bootstrap_complete
        "failed" -> R.string.bootstrap_failed
        else -> null
    }
}

internal class BootstrapInterruptedException : Exception("bootstrap_process_interrupted")
