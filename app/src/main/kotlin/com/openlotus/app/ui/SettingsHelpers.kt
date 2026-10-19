package com.openlotus.app.ui

import com.openlotus.app.log.AppLog

private const val TAG = "sequence-json"
private const val MASK = 0xFFFFFFL

/**
 * The saved sequence is just a list of 24 bit colors, so it is stored as a plain
 * array of numbers like `[16711680,65280]`. This is valid JSON and stays easy to
 * read, but it is parsed by hand on purpose: the framework org.json classes are
 * stubs in JVM unit tests, so we cannot rely on them here.
 */
fun sequenceToJson(colors: List<Int>): String = try {
    colors.joinToString(prefix = "[", postfix = "]", separator = ",") { (it.toLong() and MASK).toString() }
} catch (e: Exception) {
    AppLog.e(TAG, "sequenceToJson failed", e)
    "[]"
}

fun jsonToSequence(json: String): List<Int> = try {
    val body = json.trim().removePrefix("[").removeSuffix("]")
    if (body.isBlank()) {
        emptyList()
    } else {
        body.split(',')
            .mapNotNull { parseColor(it.trim()) }
    }
} catch (e: Exception) {
    AppLog.e(TAG, "jsonToSequence failed", e)
    emptyList()
}

private fun parseColor(token: String): Int? {
    if (token.isEmpty()) return null
    val value = if (token.startsWith("0x", ignoreCase = true)) {
        token.substring(2).toLongOrNull(16)
    } else {
        token.toLongOrNull()
    } ?: return null
    return (value and MASK).toInt()
}
