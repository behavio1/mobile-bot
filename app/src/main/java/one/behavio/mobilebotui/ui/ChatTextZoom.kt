package one.behavio.mobilebotui.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal fun chatTextScale(step: Int): Float = 1f + step.coerceIn(-2, 2) * 0.1f

@Composable
internal fun Modifier.chatTextZoom(step: Int, onStep: (Int) -> Unit): Modifier {
    val currentStep = rememberUpdatedState(step)
    val update = rememberUpdatedState(onStep)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            var nextStep = currentStep.value
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed && it.previousPressed } >= 2) {
                    zoom *= event.calculateZoom()
                    while (zoom >= 1.15f) {
                        nextStep = (nextStep + 1).coerceAtMost(2)
                        update.value(nextStep)
                        zoom /= 1.15f
                    }
                    while (zoom <= 1f / 1.15f) {
                        nextStep = (nextStep - 1).coerceAtLeast(-2)
                        update.value(nextStep)
                        zoom *= 1.15f
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
