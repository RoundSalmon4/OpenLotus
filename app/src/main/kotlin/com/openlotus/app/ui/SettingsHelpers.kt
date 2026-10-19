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
            val v = when (val o = array.opt(i)) {
                is Number -> o.toLong().toInt()
                else -> array.optString(i).takeIf { it.isNotBlank() }?.toLongOrNull()?.toInt() ?: 0
            }
            add(v.coerceIn(0, 0xFFFFFF))
        }
    }
} catch (e: Exception) {
    AppLog.e(TAG, "jsonToSequence failed", e)
    emptyList()
}
