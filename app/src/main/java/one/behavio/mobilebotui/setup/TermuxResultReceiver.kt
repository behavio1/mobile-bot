package one.behavio.mobilebotui.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

data class TermuxCommandResult(
    val requestId: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val errorCode: Int?,
    val errorMessage: String?,
) {
    val succeeded: Boolean
        get() = errorMessage.isNullOrBlank() && (exitCode == null || exitCode == 0)
}

internal object TermuxResultBus {
    private val results = MutableSharedFlow<TermuxCommandResult>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun publish(result: TermuxCommandResult) {
        results.tryEmit(result)
    }

    suspend fun await(requestId: String, timeoutMillis: Long): TermuxCommandResult =
        withTimeout(timeoutMillis) { results.first { it.requestId == requestId } }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val bundle = intent.getBundleExtra(TermuxContract.RESULT_BUNDLE)
        TermuxResultBus.publish(
            TermuxCommandResult(
                requestId = requestId,
                stdout = bundle?.getString(TermuxContract.RESULT_STDOUT).orEmpty(),
                stderr = bundle?.getString(TermuxContract.RESULT_STDERR).orEmpty(),
                exitCode = bundle?.takeIf { it.containsKey(TermuxContract.RESULT_EXIT_CODE) }
                    ?.getInt(TermuxContract.RESULT_EXIT_CODE),
                errorCode = bundle?.takeIf { it.containsKey(TermuxContract.RESULT_ERROR_CODE) }
                    ?.getInt(TermuxContract.RESULT_ERROR_CODE),
                errorMessage = bundle?.getString(TermuxContract.RESULT_ERROR_MESSAGE),
            ),
        )
    }

    companion object {
        const val EXTRA_REQUEST_ID = "one.behavio.mobilebotui.request_id"
    }
}
