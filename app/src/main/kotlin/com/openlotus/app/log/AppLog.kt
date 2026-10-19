package com.openlotus.app.log

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single place for debug output. Everything also goes to logcat, and the last
 * [MAX_LINES] lines are kept in memory so the Log tab in the app can show them
 * without a cable.
 */
object AppLog {

    private const val TAG = "OpenLotus"
    private const val MAX_LINES = 1000

    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    // The log lines feed a Compose list, so they must be written on the main
    // thread. BLE callbacks land on binder threads, so we post the add over.
    private val main = Handler(Looper.getMainLooper())

    val lines = mutableStateListOf<String>()

    /** Normal events, kept in memory and in logcat. */
    fun d(tag: String, message: String) {
        Log.d("$TAG/$tag", message)
        record("D", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e("$TAG/$tag", message)
        } else {
            Log.e("$TAG/$tag", message, throwable)
        }
        val suffix = throwable?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""
        record("E", tag, message + suffix)
    }

    /** Noisy events, logcat only. Used for things that fire many times a second. */
    fun v(tag: String, message: String) {
        Log.v("$TAG/$tag", message)
    }

    private fun record(level: String, tag: String, message: String) {
        val line = synchronized(lock) { "${stamp.format(Date())} $level/$tag $message" }
        main.post {
            lines.add(line)
            while (lines.size > MAX_LINES) {
                lines.removeAt(0)
            }
        }
    }

    fun clear() {
        // Post so a clear lands after any log lines already queued up.
        main.post { lines.clear() }
    }
}
