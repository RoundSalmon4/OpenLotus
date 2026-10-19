package com.openlotus.app.ui

import com.openlotus.app.log.AppLog

private const val TAG = "sequence-json"
private const val MASK = 0xFFFFFFL
private const val MIN_DWELL = 100
private const val MAX_DWELL = 3000

/**
 * The saved sequence is a list of colors with a per-color hold time, stored as
 * `[color:dwell,color:dwell]`. This is close to JSON and easy to read, but it is
 * parsed by hand on purpose: the framework org.json classes are stubs in JVM
 * unit tests, so we cannot rely on them here.
 */
fun sequenceToJson(items: List<Pair<Int, Int>>): String = try {
    items.joinToString(prefix = "[", postfix = "]", separator = ",") { (color, dwell) ->
        "${color.toLong() and MASK}:${clampDwell(dwell)}"
    }
} catch (e: Exception) {
    AppLog.e(TAG, "sequenceToJson failed", e)
    "[]"
}

fun jsonToSequence(json: String): List<Pair<Int, Int>> = try {
    val body = json.trim().removePrefix("[").removeSuffix("]")
    if (body.isBlank()) {
        emptyList()
    } else {
        body.split(',').mapNotNull { parseStep(it.trim()) }
    }
} catch (e: Exception) {
    AppLog.e(TAG, "jsonToSequence failed", e)
    emptyList()
}

private fun parseStep(token: String): Pair<Int, Int>? {
    if (token.isEmpty()) return null
    val parts = token.split(':')
    val color = parseNumber(parts.getOrNull(0)) ?: return null
    val dwell = parseNumber(parts.getOrNull(1)) ?: 800
    return (color and MASK.toInt()) to clampDwell(dwell)
}

private fun parseNumber(token: String?): Int? {
    if (token.isNullOrBlank()) return null
    val value = if (token.startsWith("0x", ignoreCase = true)) {
        token.substring(2).toLongOrNull(16)
    } else {
        token.toLongOrNull()
    } ?: return null
    return (value and MASK).toInt()
}

private fun clampDwell(value: Int): Int = value.coerceIn(MIN_DWELL, MAX_DWELL)
