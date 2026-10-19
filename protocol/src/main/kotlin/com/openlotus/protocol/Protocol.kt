package com.openlotus.protocol

/**
 * Frame helpers for the ELK-BLEDOM family.
 *
 * Every command is 9 bytes. The first byte is 0x7E, the last is 0xEF. The byte
 * positions differ per command, so each builder spells the frame out instead of
 * trying to force everything through one template. Values below were checked
 * against the stock app and the public protocol write-ups.
 */
object Frame {
    const val START: Int = 0x7E
    const val END: Int = 0xEF
    const val NONE: Int = 0xFF

    fun of(vararg bytes: Int): ByteArray {
        require(bytes.size == 9) { "frame must be 9 bytes" }
        require(bytes[0] == START && bytes[8] == END) { "bad frame markers" }
        return ByteArray(9) { bytes[it].toByte() }
    }
}

enum class LightMode(val code: Int) {
    ALL(0),
    RGB(1),
    WHITE(2),
    COLOR_TEMPERATURE(3),
    LASER(4),
}

/**
 * The 29 lighting modes exposed by the stock app. The wire value is always
 * index + 0x80.
 */
enum class Mode(val code: Int) {
    STATIC_RED(0x80),
    STATIC_BLUE(0x81),
    STATIC_GREEN(0x82),
    STATIC_CYAN(0x83),
    STATIC_YELLOW(0x84),
    STATIC_PURPLE(0x85),
    STATIC_WHITE(0x86),
    JUMP_THREE(0x87),
    JUMP_SEVEN(0x88),
    CROSSFADE_THREE(0x89),
    CROSSFADE_SEVEN(0x8A),
    GRADUAL_RED(0x8B),
    GRADUAL_GREEN(0x8C),
    GRADUAL_BLUE(0x8D),
    GRADUAL_YELLOW(0x8E),
    GRADUAL_CYAN(0x8F),
    GRADUAL_PURPLE(0x90),
    GRADUAL_WHITE(0x91),
    CROSSFADE_RED_GREEN(0x92),
    CROSSFADE_RED_BLUE(0x93),
    CROSSFADE_GREEN_BLUE(0x94),
    STROBE_SEVEN(0x95),
    STROBE_RED(0x96),
    STROBE_GREEN(0x97),
    STROBE_BLUE(0x98),
    STROBE_YELLOW(0x99),
    STROBE_CYAN(0x9A),
    STROBE_PURPLE(0x9B),
    STROBE_WHITE(0x9C),
}

/**
 * Frames as sent by the stock app (v6.5.10).
 */
object Commands {
    fun power(on: Boolean): ByteArray {
        val v = if (on) 1 else 0
        return Frame.of(0x7E, 0x04, 0x04, v, 0x00, v, 0xFF, 0x00, 0xEF)
    }

    fun brightness(value: Int, mode: LightMode = LightMode.ALL): ByteArray =
        Frame.of(0x7E, 0x04, 0x01, clamp(value, 0, 100), mode.code, 0xFF, 0xFF, 0x00, 0xEF)

    fun color(red: Int, green: Int, blue: Int): ByteArray =
        Frame.of(0x7E, 0x07, 0x05, 0x03, clamp(red, 0, 255), clamp(green, 0, 255), clamp(blue, 0, 255), 0x10, 0xEF)

    /** Same as [color] but tagged as music driven (flag 0x20). */
    fun musicColor(red: Int, green: Int, blue: Int): ByteArray =
        Frame.of(0x7E, 0x07, 0x05, 0x03, clamp(red, 0, 255), clamp(green, 0, 255), clamp(blue, 0, 255), 0x20, 0xEF)

    fun colorTemperature(warm: Int, cold: Int): ByteArray =
        Frame.of(0x7E, 0x06, 0x05, 0x02, clamp(warm, 0, 255), clamp(cold, 0, 255), 0xFF, 0x08, 0xEF)

    fun singleColor(value: Int): ByteArray =
        Frame.of(0x7E, 0x05, 0x05, 0x01, clamp(value, 0, 255), 0xFF, 0xFF, 0x08, 0xEF)

    fun mode(mode: Mode): ByteArray =
        Frame.of(0x7E, 0x05, 0x03, mode.code, 0x03, 0xFF, 0xFF, 0x00, 0xEF)

    fun speed(value: Int): ByteArray =
        Frame.of(0x7E, 0x04, 0x02, clamp(value, 0, 100), 0xFF, 0xFF, 0xFF, 0x00, 0xEF)

    fun pinSequence(red: Int, green: Int, blue: Int): ByteArray =
        Frame.of(0x7E, 0x06, 0x81, clamp(red, 1, 3), clamp(green, 1, 3), clamp(blue, 1, 3), 0xFF, 0x00, 0xEF)

    fun micSensitivity(value: Int): ByteArray =
        Frame.of(0x7E, 0x04, 0x06, clamp(value, 0, 100), 0xFF, 0xFF, 0xFF, 0x00, 0xEF)

    fun micEnabled(on: Boolean): ByteArray =
        Frame.of(0x7E, 0x04, 0x07, if (on) 1 else 0, 0xFF, 0xFF, 0xFF, 0x00, 0xEF)

    fun systemTime(hour: Int, minute: Int, second: Int, weekday: Int): ByteArray =
        Frame.of(0x7E, 0x07, 0x83, clamp(hour, 0, 23), clamp(minute, 0, 59), clamp(second, 0, 59), clamp(weekday, 0, 7), 0xFF, 0xEF)

    fun schedule(hour: Int, minute: Int, second: Int, mode: Int, weekdays: Int): ByteArray =
        Frame.of(0x7E, 0x08, 0x82, clamp(hour, 0, 23), clamp(minute, 0, 59), clamp(second, 0, 59), clamp(mode, 0, 0xFF), clamp(weekdays, 0, 0xFF), 0xEF)

    private fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}

/**
 * Older frames documented by the ELK-BLEDOM community. Kept around so we can
 * switch dialects once hardware testing decides which one a given strip wants.
 */
object LegacyCommands {
    fun powerOn(): ByteArray = Frame.of(0x7E, 0x00, 0x04, 0xF0, 0x00, 0x01, 0xFF, 0x00, 0xEF)

    fun powerOff(): ByteArray = Frame.of(0x7E, 0x00, 0x04, 0x00, 0x00, 0x00, 0xFF, 0x00, 0xEF)

    fun color(red: Int, green: Int, blue: Int): ByteArray =
        Frame.of(0x7E, 0x00, 0x05, 0x03, red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255), 0x00, 0xEF)

    fun brightness(value: Int): ByteArray =
        Frame.of(0x7E, 0x00, 0x01, value.coerceIn(0, 100), 0x00, 0x00, 0x00, 0x00, 0xEF)

    fun mode(code: Int): ByteArray =
        Frame.of(0x7E, 0x00, 0x03, code.coerceIn(0, 255), 0x03, 0x00, 0x00, 0x00, 0xEF)

    fun speed(value: Int): ByteArray =
        Frame.of(0x7E, 0x00, 0x02, value.coerceIn(0, 100), 0x00, 0x00, 0x00, 0x00, 0xEF)
}
