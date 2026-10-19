package com.openlotus.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreHelperTest {

    @Test
    fun sequenceRoundTripsWithDwell() {
        val original = listOf(0xFF0000 to 800, 0x00FF00 to 1200, 0x0000FF to 500)
        val json = sequenceToJson(original)
        val back = jsonToSequence(json)
        assertEquals(original, back)
    }

    @Test
    fun emptyListRoundTrips() {
        val json = sequenceToJson(emptyList())
        val back = jsonToSequence(json)
        assertEquals(emptyList<Pair<Int, Int>>(), back)
    }

    @Test
    fun invalidJsonReturnsEmpty() {
        assertEquals(emptyList<Pair<Int, Int>>(), jsonToSequence("{not json"))
        assertEquals(emptyList<Pair<Int, Int>>(), jsonToSequence(""))
    }

    @Test
    fun colorIsMaskedAndDwellIsClamped() {
        val back = jsonToSequence("[4294967295:99999, -1:-5]")
        assertEquals(2, back.size)
        assertEquals(0xFFFFFF, back[0].first)
        assertEquals(3000, back[0].second)
        assertEquals(0xFFFFFF, back[1].first)
        assertEquals(100, back[1].second)
    }

    @Test
    fun missingDwellFallsBackToDefault() {
        val back = jsonToSequence("[16711680]")
        assertEquals(1, back.size)
        assertEquals(16711680, back[0].first)
        assertEquals(800, back[0].second)
    }
}
