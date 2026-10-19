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

/**
 * Walks a color list and writes a static color frame every [dwellMs]. The strip
 * has no firmware sequence mode, so the alternation is driven from the phone.
 *
 * The frames go out through [send] so this class stays free of BLE and Android
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

    fun toggle(scope: CoroutineScope, colors: List<Int>, dwellMs: Long) {
        AppLog.d(TAG, "toggle(colors=${colors.size}, dwell=${dwellMs}ms, playing=$playing)")
        if (playing) stop() else start(scope, colors, dwellMs)
    }

    fun start(scope: CoroutineScope, colors: List<Int>, dwellMs: Long, startIndex: Int = 0) {
        if (colors.isEmpty()) {
            AppLog.e(TAG, "start called with no colors")
            return
        }
        AppLog.d(TAG, "start with ${colors.size} colors, dwell ${dwellMs}ms, from $startIndex")
        stop()
        playing = true
        job = scope.launch {
            var index = ((startIndex % colors.size) + colors.size) % colors.size
            while (isActive) {
                currentIndex = index
                val argb = colors[index]
                AppLog.v(TAG, "tick $index ${hex(argb)}")
                send(
                    Commands.color(
                        (argb shr 16) and 0xFF,
                        (argb shr 8) and 0xFF,
                        argb and 0xFF,
                    )
                )
                delay(dwellMs)
                index = (index + 1) % colors.size
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
