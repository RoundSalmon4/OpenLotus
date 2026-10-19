package com.openlotus.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.openlotus.app.ble.BleManager
import com.openlotus.protocol.Commands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.graphics.Color as AndroidColor

/**
 * Walks a color list and writes a static color frame every [dwellMs]. The strip
 * has no firmware sequence mode, so the alternation is driven from the phone.
 */
class SequencePlayer(private val ble: BleManager) {

    var playing by mutableStateOf(false)
        private set

    var currentIndex by mutableStateOf(-1)
        private set

    private var job: Job? = null

    fun toggle(scope: CoroutineScope, colors: List<Int>, dwellMs: Long) {
        if (playing) stop() else start(scope, colors, dwellMs)
    }

    fun start(scope: CoroutineScope, colors: List<Int>, dwellMs: Long) {
        if (colors.isEmpty()) return
        stop()
        playing = true
        job = scope.launch {
            var index = 0
            while (isActive) {
                currentIndex = index
                val argb = colors[index]
                ble.send(
                    Commands.color(
                        AndroidColor.red(argb),
                        AndroidColor.green(argb),
                        AndroidColor.blue(argb),
                    )
                )
                delay(dwellMs)
                index = (index + 1) % colors.size
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        playing = false
        currentIndex = -1
    }
}
