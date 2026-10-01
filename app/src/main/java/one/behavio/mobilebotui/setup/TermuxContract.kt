package one.behavio.mobilebotui.setup

/**
 * Public RUN_COMMAND contract from termux/termux-app. The source revision used
 * for verification is recorded in development/test-reports.
 */
object TermuxContract {
    const val PACKAGE_NAME = "com.termux"
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"

    const val RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERROR_CODE = "err"
    const val RESULT_ERROR_MESSAGE = "errmsg"

    const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    const val HOME = "/data/data/com.termux/files/home"

    const val ENABLE_EXTERNAL_APPS_COMMAND =
        "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> " +
            "~/.termux/termux.properties && termux-reload-settings"

    const val VERIFIED_TERMUX_VERSION = "0.118.3"
    const val TERMUX_INSTALL_URL =
        "https://github.com/termux/termux-app/releases/download/v0.118.3/" +
            "termux-app_v0.118.3%2Bgithub-debug_arm64-v8a.apk"
    const val CODEX_VERSION = "0.153.4"
    const val HOST_VERSION = "0.5.50"
    const val PROTOCOL_VERSION = 5
    const val ENVIRONMENT_REVISION = 56
}
