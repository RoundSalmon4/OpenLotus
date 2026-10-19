package com.openlotus.protocol

/**
 * Optional log sink. The protocol module is plain JVM code, so it never touches
 * android.util.Log directly. The app sets [sink] at startup and gets a line for
 * every frame that is built. Tests leave it null and stay quiet.
 */
object ProtocolLog {
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun log(message: String) {
        sink?.invoke(message)
    }
}

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

    fun of(vararg bytes: Int): ByteArray {
        if (bytes.size != 9) {
            ProtocolLog.log("frame rejected: expected 9 bytes, got ${bytes.size}")
            throw IllegalArgumentException("frame must be 9 bytes")
        }
        if (bytes[0] != START || bytes[8] != END) {
            ProtocolLog.log("frame rejected: bad markers ${hex(ByteArray(9) { bytes[it].toByte() })}")
            throw IllegalArgumentException("bad frame markers")
        }
        return ByteArray(9) { bytes[it].toByte() }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    /** Logs the frame and hands it back, so builders stay one line. */
    internal fun logged(name: String, frame: ByteArray): ByteArray {
        ProtocolLog.log("$name -> ${hex(frame)}")
        return frame
    }
}

/**
 * The light target a brightness write applies to. Only [ALL] is wired into the
 * UI so far; the rest are here because the protocol defines them.
 */
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
        return Frame.logged(
            if (on) "power on" else "power off",
            Frame.of(0x7E, 0x04, 0x04, v, 0x00, v, 0xFF, 0x00, 0xEF),
        )
    }

    fun brightness(value: Int, mode: LightMode = LightMode.ALL): ByteArray {
        val v = value.coerceIn(0, 100)
        if (v != value) ProtocolLog.log("brightness clamped $value -> $v")
        return Frame.logged(
            "brightness $v mode ${mode.name}",
            Frame.of(0x7E, 0x04, 0x01, v, mode.code, 0xFF, 0xFF, 0x00, 0xEF),
        )
    }

    fun color(red: Int, green: Int, blue: Int): ByteArray {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        if (r != red || g != green || b != blue) {
            ProtocolLog.log("color clamped $red,$green,$blue -> $r,$g,$b")
        }
        return Frame.logged(
            "color $r,$g,$b",
            Frame.of(0x7E, 0x07, 0x05, 0x03, r, g, b, 0x10, 0xEF),
        )
    }

    // Built but not wired into the UI yet, needs a real strip test first.
    /** Same as [color] but tagged as music driven (flag 0x20). */
    fun musicColor(red: Int, green: Int, blue: Int): ByteArray {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        if (r != red || g != green || b != blue) {
            ProtocolLog.log("music color clamped $red,$green,$blue -> $r,$g,$b")
        }
        return Frame.logged(
            "music color $r,$g,$b",
            Frame.of(0x7E, 0x07, 0x05, 0x03, r, g, b, 0x20, 0xEF),
        )
    }

    // Built but not wired into the UI yet, needs a real strip test first.
    fun colorTemperature(warm: Int, cold: Int): ByteArray {
        val w = warm.coerceIn(0, 255)
        val c = cold.coerceIn(0, 255)
        if (w != warm || c != cold) {
            ProtocolLog.log("color temperature clamped $warm,$cold -> $w,$c")
        }
        return Frame.logged(
            "color temperature $w,$c",
            Frame.of(0x7E, 0x06, 0x05, 0x02, w, c, 0xFF, 0x08, 0xEF),
        )
    }

    // Built but not wired into the UI yet, needs a real strip test first.
    fun singleColor(value: Int): ByteArray {
        val v = value.coerceIn(0, 255)
        if (v != value) ProtocolLog.log("single color clamped $value -> $v")
        return Frame.logged(
            "single color $v",
            Frame.of(0x7E, 0x05, 0x05, 0x01, v, 0xFF, 0xFF, 0x08, 0xEF),
        )
    }

    fun mode(mode: Mode): ByteArray =
        Frame.logged(
            "mode ${mode.name}",
            Frame.of(0x7E, 0x05, 0x03, mode.code, 0x03, 0xFF, 0xFF, 0x00, 0xEF),
        )

    fun speed(value: Int): ByteArray {
        val v = value.coerceIn(0, 100)
        if (v != value) ProtocolLog.log("speed clamped $value -> $v")
        return Frame.logged(
            "speed $v",
            Frame.of(0x7E, 0x04, 0x02, v, 0xFF, 0xFF, 0xFF, 0x00, 0xEF),
        )
    }

    // Built but not wired into the UI yet, needs a real strip test first.
    fun pinSequence(red: Int, green: Int, blue: Int): ByteArray {
        val r = red.coerceIn(1, 3)
        val g = green.coerceIn(1, 3)
        val b = blue.coerceIn(1, 3)
        if (r != red || g != green || b != blue) {
            ProtocolLog.log("pin order clamped $red,$green,$blue -> $r,$g,$b")
        }
        return Frame.logged(
            "pin order $r,$g,$b",
            Frame.of(0x7E, 0x06, 0x81, r, g, b, 0xFF, 0x00, 0xEF),
        )
    }

    // Built but not wired into the UI yet, needs a real strip test first.
    fun micSensitivity(value: Int): ByteArray {
        val v = value.coerceIn(0, 100)
        if (v != value) ProtocolLog.log("mic sensitivity clamped $value -> $v")
        return Frame.logged(
            "mic sensitivity $v",
            Frame.of(0x7E, 0x04, 0x06, v, 0xFF, 0xFF, 0xFF, 0x00, 0xEF),
        )
    }

    // Built but not wired into the UI yet, needs a real strip test first.
    fun micEnabled(on: Boolean): ByteArray =
        Frame.logged(
            if (on) "mic on" else "mic off",
            Frame.of(0x7E, 0x04, 0x07, if (on) 1 else 0, 0xFF, 0xFF, 0xFF, 0x00, 0xEF),
        )

    // Weekday is 0 = Sunday through 6 = Saturday, matching the stock app's
    // Time.weekDay. The strip's timer uses this to pick the firing day.
    fun systemTime(hour: Int, minute: Int, second: Int, weekday: Int): ByteArray {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val s = second.coerceIn(0, 59)
        val w = weekday.coerceIn(0, 6)
        if (h != hour || m != minute || s != second || w != weekday) {
            ProtocolLog.log("clock clamped $hour:$minute:$second day $weekday -> $h:$m:$s day $w")
        }
        return Frame.logged(
            "clock $h:$m:$s day $w",
            Frame.of(0x7E, 0x07, 0x83, h, m, s, w, 0xFF, 0xEF),
        )
    }

    // Built but not wired into the UI yet, needs a real strip test first.
    fun schedule(hour: Int, minute: Int, second: Int, mode: Int, weekdays: Int): ByteArray {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val s = second.coerceIn(0, 59)
        val md = mode.coerceIn(0, 0xFF)
        val wd = weekdays.coerceIn(0, 0xFF)
        if (h != hour || m != minute || s != second || md != mode || wd != weekdays) {
            ProtocolLog.log("schedule clamped $hour:$minute:$second mode $mode days $weekdays -> $h:$m:$s mode $md days $wd")
        }
        return Frame.logged(
            "schedule $h:$m:$s mode $md days ${Integer.toBinaryString(wd)}",
            Frame.of(0x7E, 0x08, 0x82, h, m, s, md, wd, 0xEF),
        )
    }
}

/**
 * Older frames documented by the ELK-BLEDOM community. Nothing calls these yet,
 * there is no runtime dialect switch. Kept so one can be wired once hardware
 * testing decides which dialect a given strip wants.
 */
object LegacyCommands {
    fun powerOn(): ByteArray =
        Frame.logged("legacy power on", Frame.of(0x7E, 0x00, 0x04, 0xF0, 0x00, 0x01, 0xFF, 0x00, 0xEF))

    fun powerOff(): ByteArray =
        Frame.logged("legacy power off", Frame.of(0x7E, 0x00, 0x04, 0x00, 0x00, 0x00, 0xFF, 0x00, 0xEF))

    fun color(red: Int, green: Int, blue: Int): ByteArray {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        if (r != red || g != green || b != blue) {
            ProtocolLog.log("legacy color clamped $red,$green,$blue -> $r,$g,$b")
        }
        return Frame.logged(
            "legacy color $r,$g,$b",
            Frame.of(0x7E, 0x00, 0x05, 0x03, r, g, b, 0x00, 0xEF),
        )
    }

    fun brightness(value: Int): ByteArray {
        val v = value.coerceIn(0, 100)
        if (v != value) ProtocolLog.log("legacy brightness clamped $value -> $v")
        return Frame.logged(
            "legacy brightness $v",
            Frame.of(0x7E, 0x00, 0x01, v, 0x00, 0x00, 0x00, 0x00, 0xEF),
        )
    }

    fun mode(code: Int): ByteArray {
        val c = code.coerceIn(0, 255)
        if (c != code) ProtocolLog.log("legacy mode clamped $code -> $c")
        return Frame.logged(
            "legacy mode 0x%02X".format(c),
            Frame.of(0x7E, 0x00, 0x03, c, 0x03, 0x00, 0x00, 0x00, 0xEF),
        )
    }

    fun speed(value: Int): ByteArray {
        val v = value.coerceIn(0, 100)
        if (v != value) ProtocolLog.log("legacy speed clamped $value -> $v")
        return Frame.logged(
            "legacy speed $v",
            Frame.of(0x7E, 0x00, 0x02, v, 0x00, 0x00, 0x00, 0x00, 0xEF),
        )
    }
}
