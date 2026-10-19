package com.openlotus.app.ui

import org.json.JSONArray
import com.openlotus.app.log.AppLog

private const val TAG = "sequence-json"

fun sequenceToJson(colors: List<Int>): String = try {
    JSONArray().apply {
        colors.forEach { put(it and 0xFFFFFF) }
    }.toString()
} catch (e: Exception) {
    AppLog.e(TAG, "sequenceToJson failed", e)
    "[]"
}

fun jsonToSequence(json: String): List<Int> = try {
    if (json.isBlank()) return emptyList()
    val array = JSONArray(json)
    buildList(array.length()) {
        for (i in 0 until array.length()) {
            val raw = array.opt(i)
            val v = when (raw) {
                is Number -> raw.toLong()
                is String -> raw.trim().lowercase().removePrefix("0x").toLongOrNull(16) ?: raw.toLongOrNull() ?: 0L
                else -> 0L
            }
            add((v.toLong() and 0xFFFFFFL).toInt())
        }
    }
} catch (e: Exception) {
    AppLog.e(TAG, "jsonToSequence failed", e)
    emptyList()
}
