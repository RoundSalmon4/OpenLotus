package com.openlotus.app.color

import com.openlotus.protocol.Commands
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Per-channel gains and a gamma curve applied to every color the app sends, so
 * the strip matches what the color wheel shows. The strip's LEDs are not
 * balanced (its green runs hot, for example), so the wheel needs a correction
 * that the user tunes once in the calibration wizard.
 *
 * A default calibration is the identity, so nothing changes until it is tuned.
 */
data class Calibration(
    val redGain: Float = 1f,
    val greenGain: Float = 1f,
    val blueGain: Float = 1f,
    val gamma: Float = 1f,
) {
    val isIdentity: Boolean
        get() = redGain == 1f && greenGain == 1f && blueGain == 1f && gamma == 1f

    fun apply(red: Int, green: Int, blue: Int): Triple<Int, Int, Int> {
        if (isIdentity) return Triple(red, green, blue)
        return Triple(
            channel(red, redGain),
            channel(green, greenGain),
            channel(blue, blueGain),
        )
    }

    private fun channel(value: Int, gain: Float): Int {
        val linear = (value.coerceIn(0, 255) / 255f).pow(gamma) * gain
        return (linear.coerceIn(0f, 1f) * 255f).roundToInt()
    }
}

/**
 * The live calibration. Loaded from settings at startup and updated when the
 * wizard saves, so the UI, the sequence player and the previews all read the
 * same correction without threading it through every call.
 */
object ColorCal {
    @Volatile
    var current: Calibration = Calibration()

    fun apply(red: Int, green: Int, blue: Int): Triple<Int, Int, Int> =
        current.apply(red, green, blue)

    fun packed(red: Int, green: Int, blue: Int): Int {
        val (r, g, b) = apply(red, green, blue)
        return (r shl 16) or (g shl 8) or b
    }

    fun packed(argb: Int): Int =
        packed((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    fun frame(red: Int, green: Int, blue: Int): ByteArray {
        val (r, g, b) = apply(red, green, blue)
        return Commands.color(r, g, b)
    }
}
