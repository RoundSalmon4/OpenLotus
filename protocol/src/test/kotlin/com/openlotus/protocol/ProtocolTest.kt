package com.openlotus.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTest {

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun powerOnMatchesStockApp() {
        assertArrayEquals(bytes(0x7E, 0x04, 0x04, 0x01, 0x00, 0x01, 0xFF, 0x00, 0xEF), Commands.power(true))
    }

    @Test
    fun powerOffMatchesStockApp() {
        assertArrayEquals(bytes(0x7E, 0x04, 0x04, 0x00, 0x00, 0x00, 0xFF, 0x00, 0xEF), Commands.power(false))
    }

    @Test
    fun brightnessPlacesValueBeforeMode() {
        assertArrayEquals(bytes(0x7E, 0x04, 0x01, 50, 1, 0xFF, 0xFF, 0x00, 0xEF), Commands.brightness(50, LightMode.RGB))
    }

    @Test
    fun brightnessClampsToHundred() {
        assertEquals(100, Commands.brightness(250)[3].toInt() and 0xFF)
        assertEquals(0, Commands.brightness(-4)[3].toInt() and 0xFF)
    }

    @Test
    fun colorUsesFlagSixteen() {
        assertArrayEquals(bytes(0x7E, 0x07, 0x05, 0x03, 255, 128, 0, 0x10, 0xEF), Commands.color(255, 128, 0))
    }

    @Test
    fun musicColorUsesFlagThirtyTwo() {
        assertArrayEquals(bytes(0x7E, 0x07, 0x05, 0x03, 1, 2, 3, 0x20, 0xEF), Commands.musicColor(1, 2, 3))
    }

    @Test
    fun colorTemperature() {
        assertArrayEquals(bytes(0x7E, 0x06, 0x05, 0x02, 40, 60, 0xFF, 0x08, 0xEF), Commands.colorTemperature(40, 60))
    }

    @Test
    fun singleColor() {
        assertArrayEquals(bytes(0x7E, 0x05, 0x05, 0x01, 128, 0xFF, 0xFF, 0x08, 0xEF), Commands.singleColor(128))
    }

    @Test
    fun modeWritesIndexPlusBase() {
        assertArrayEquals(bytes(0x7E, 0x05, 0x03, 0x80, 0x03, 0xFF, 0xFF, 0x00, 0xEF), Commands.mode(Mode.STATIC_RED))
        assertArrayEquals(bytes(0x7E, 0x05, 0x03, 0x9C, 0x03, 0xFF, 0xFF, 0x00, 0xEF), Commands.mode(Mode.STROBE_WHITE))
        assertEquals(29, Mode.entries.size)
        assertEquals(0x87, Mode.JUMP_THREE.code)
        assertEquals(0x95, Mode.STROBE_SEVEN.code)
    }

    @Test
    fun speed() {
        assertArrayEquals(bytes(0x7E, 0x04, 0x02, 60, 0xFF, 0xFF, 0xFF, 0x00, 0xEF), Commands.speed(60))
    }

    @Test
    fun pinSequence() {
        assertArrayEquals(bytes(0x7E, 0x06, 0x81, 1, 2, 3, 0xFF, 0x00, 0xEF), Commands.pinSequence(1, 2, 3))
    }

    @Test
    fun micSensitivityAndSwitch() {
        assertArrayEquals(bytes(0x7E, 0x04, 0x06, 70, 0xFF, 0xFF, 0xFF, 0x00, 0xEF), Commands.micSensitivity(70))
        assertArrayEquals(bytes(0x7E, 0x04, 0x07, 1, 0xFF, 0xFF, 0xFF, 0x00, 0xEF), Commands.micEnabled(true))
    }

    @Test
    fun systemTime() {
        assertArrayEquals(bytes(0x7E, 0x07, 0x83, 13, 45, 30, 3, 0xFF, 0xEF), Commands.systemTime(13, 45, 30, 3))
    }

    @Test
    fun scheduleFillsAllNineBytes() {
        assertArrayEquals(bytes(0x7E, 0x08, 0x82, 8, 30, 0, 1, 0x7F, 0xEF), Commands.schedule(8, 30, 0, 1, 0x7F))
    }

    @Test
    fun legacyFramesMatchCommunityDocs() {
        assertArrayEquals(bytes(0x7E, 0x00, 0x04, 0xF0, 0x00, 0x01, 0xFF, 0x00, 0xEF), LegacyCommands.powerOn())
        assertArrayEquals(bytes(0x7E, 0x00, 0x04, 0x00, 0x00, 0x00, 0xFF, 0x00, 0xEF), LegacyCommands.powerOff())
        assertArrayEquals(bytes(0x7E, 0x00, 0x05, 0x03, 10, 20, 30, 0x00, 0xEF), LegacyCommands.color(10, 20, 30))
        assertArrayEquals(bytes(0x7E, 0x00, 0x01, 100, 0x00, 0x00, 0x00, 0x00, 0xEF), LegacyCommands.brightness(100))
        assertArrayEquals(bytes(0x7E, 0x00, 0x03, 0x87, 0x03, 0x00, 0x00, 0x00, 0xEF), LegacyCommands.mode(0x87))
        assertArrayEquals(bytes(0x7E, 0x00, 0x02, 25, 0x00, 0x00, 0x00, 0x00, 0xEF), LegacyCommands.speed(25))
    }

    @Test
    fun everyModeFitsInOneByte() {
        Mode.entries.forEach { mode ->
            assertEquals(mode.code, Commands.mode(mode)[3].toInt() and 0xFF)
        }
    }
}
