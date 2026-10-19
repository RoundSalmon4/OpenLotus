package com.openlotus.app.ui

import com.openlotus.app.log.AppLog
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreHelperTest {

    @Test
    fun sequenceRoundTrips() {
        val original = listOf(0xFF0000, 0x00FF00, 0x0000FF)
        val json = sequenceToJson(original)
        val back = jsonToSequence(json)
        assertEquals(original, back)
    }

    @Test
    fun emptyListRoundTrips() {
        val json = sequenceToJson(emptyList())
        val back = jsonToSequence(json)
        assertEquals(emptyList<Int>(), back)
    }

    @Test
    fun invalidJsonReturnsEmpty() {
        assertEquals(emptyList<Int>(), jsonToSequence("{not json"))
        assertEquals(emptyList<Int>(), jsonToSequence(""))
    }

    @Test
    fun clampsJsonNumbers() {
        val json = "[4294967295, -1]"
        val back = jsonToSequence(json)
        assertEquals(2, back.size)
        assertEquals(0xFFFFFF, back[0])
        assertEquals(0xFFFFFF, back[1])
    }

    @Test
    fun jsonFallbackOnUnexpected() {
        // ensure we don't crash
        assertEquals(emptyList<Int>(), jsonToSequence("null"))
    }
}
