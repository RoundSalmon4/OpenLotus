package com.openlotus.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.openlotus.app.log.AppLog
import com.openlotus.protocol.Commands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One step of a sequence: a color and how long to hold it. */
data class SequenceStep(val color: Int, val dwellMs: Long)

/**
 * Walks a list of steps and writes a static color frame for each, holding it for
 * that step's dwell. The strip has no firmware sequence mode, so the alternation
 * is driven from the phone.
 *
 * Frames go out through [send] so this class stays free of BLE and Android
 * graphics, which keeps it unit testable.
 */
class SequencePlayer(private val send: (ByteArray) -> Unit) {

    private companion object {
        const val TAG = "sequence"
    }

    var playing by mutableStateOf(false)
        private set

    var currentIndex by mutableStateOf(-1)
        private set

    private var job: Job? = null

    fun toggle(scope: CoroutineScope, steps: List<SequenceStep>, startIndex: Int = 0) {
        AppLog.d(TAG, "toggle(steps=${steps.size}, playing=$playing)")
        if (playing) stop() else start(scope, steps, startIndex)
    }

    fun start(scope: CoroutineScope, steps: List<SequenceStep>, startIndex: Int = 0) {
        if (steps.isEmpty()) {
            AppLog.e(TAG, "start called with no steps")
            return
        }
        AppLog.d(TAG, "start with ${steps.size} steps, from $startIndex")
        stop()
        playing = true
        job = scope.launch {
            var index = ((startIndex % steps.size) + steps.size) % steps.size
            while (isActive) {
                currentIndex = index
                val step = steps[index]
                AppLog.v(TAG, "tick $index ${hex(step.color)} for ${step.dwellMs}ms")
                send(
                    Commands.color(
                        (step.color shr 16) and 0xFF,
                        (step.color shr 8) and 0xFF,
                        step.color and 0xFF,
                    )
                )
                delay(step.dwellMs)
                index = (index + 1) % steps.size
            }
        }
        AppLog.d(TAG, "player job started")
    }

    fun stop() {
        AppLog.d(TAG, "stop(playing=$playing)")
        job?.cancel()
        job = null
        playing = false
        currentIndex = -1
    }

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}
