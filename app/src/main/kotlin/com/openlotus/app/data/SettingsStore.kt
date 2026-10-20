package com.openlotus.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openlotus.app.log.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "settings"
private const val DATA_STORE_NAME = "openlotus_prefs"

private val Context.dataStore by preferencesDataStore(DATA_STORE_NAME)

data class Settings(
    val brightness: Int = 60,
    val speed: Int = 50,
    val red: Int = 255,
    val green: Int = 80,
    val blue: Int = 0,
    val dwellMs: Int = 800,
    val powerOn: Boolean = false,
    val sequenceJson: String = "[]",
    val lastDeviceAddress: String = "",
    val lastDeviceName: String = "",
    val timerOnEnabled: Boolean = false,
    val timerOnHour: Int = 8,
    val timerOnMinute: Int = 0,
    val timerOnWeeks: Int = 0,
    val timerOffEnabled: Boolean = false,
    val timerOffHour: Int = 23,
    val timerOffMinute: Int = 0,
    val timerOffWeeks: Int = 0,
    val calRedGain: Float = 1f,
    val calGreenGain: Float = 1f,
    val calBlueGain: Float = 1f,
    val calGamma: Float = 1f,
    val presetColors: String = "",
)

object Keys {
    val brightness = intPreferencesKey("brightness")
    val speed = intPreferencesKey("speed")
    val red = intPreferencesKey("red")
    val green = intPreferencesKey("green")
    val blue = intPreferencesKey("blue")
    val dwellMs = intPreferencesKey("dwell_ms")
    val powerOn = booleanPreferencesKey("power_on")
    val sequenceJson = stringPreferencesKey("sequence_json")
    val lastDeviceAddress = stringPreferencesKey("last_device_address")
    val lastDeviceName = stringPreferencesKey("last_device_name")
    val timerOnEnabled = booleanPreferencesKey("timer_on_enabled")
    val timerOnHour = intPreferencesKey("timer_on_hour")
    val timerOnMinute = intPreferencesKey("timer_on_minute")
    val timerOnWeeks = intPreferencesKey("timer_on_weeks")
    val timerOffEnabled = booleanPreferencesKey("timer_off_enabled")
    val timerOffHour = intPreferencesKey("timer_off_hour")
    val timerOffMinute = intPreferencesKey("timer_off_minute")
    val timerOffWeeks = intPreferencesKey("timer_off_weeks")
    val calRedGain = floatPreferencesKey("cal_red_gain")
    val calGreenGain = floatPreferencesKey("cal_green_gain")
    val calBlueGain = floatPreferencesKey("cal_blue_gain")
    val calGamma = floatPreferencesKey("cal_gamma")
    val presetColors = stringPreferencesKey("preset_colors")
}

class SettingsStore(private val context: Context) {

    val settingsFlow: Flow<Settings> = context.dataStore.data
        .map { prefs -> mapToSettings(prefs) }
        .distinctUntilChanged()

    suspend fun setBrightness(value: Int) {
        val v = value.coerceIn(0, 100)
        AppLog.d(TAG, "setBrightness $v")
        context.dataStore.edit { it[Keys.brightness] = v }
    }

    suspend fun setSpeed(value: Int) {
        val v = value.coerceIn(0, 100)
        AppLog.d(TAG, "setSpeed $v")
        context.dataStore.edit { it[Keys.speed] = v }
    }

    suspend fun setColor(red: Int, green: Int, blue: Int) {
        val r = red.coerceIn(0, 255)
        val g = green.coerceIn(0, 255)
        val b = blue.coerceIn(0, 255)
        AppLog.d(TAG, "setColor $r,$g,$b")
        context.dataStore.edit {
            it[Keys.red] = r
            it[Keys.green] = g
            it[Keys.blue] = b
        }
    }

    suspend fun setDwell(value: Int) {
        val v = value.coerceIn(100, 3000)
        AppLog.d(TAG, "setDwell $v")
        context.dataStore.edit { it[Keys.dwellMs] = v }
    }

    suspend fun setPowerOn(value: Boolean) {
        AppLog.d(TAG, "setPowerOn $value")
        context.dataStore.edit { it[Keys.powerOn] = value }
    }

    suspend fun setSequenceJson(json: String) {
        AppLog.d(TAG, "setSequenceJson len=${json.length}")
        context.dataStore.edit { it[Keys.sequenceJson] = json }
    }

    suspend fun setLastDevice(address: String, name: String) {
        AppLog.d(TAG, "setLastDevice $address $name")
        context.dataStore.edit {
            it[Keys.lastDeviceAddress] = address
            it[Keys.lastDeviceName] = name
        }
    }

    suspend fun setPresetColors(json: String) {
        AppLog.d(TAG, "setPresetColors len=${json.length}")
        context.dataStore.edit { it[Keys.presetColors] = json }
    }

    suspend fun setCalibration(redGain: Float, greenGain: Float, blueGain: Float, gamma: Float) {
        AppLog.d(TAG, "setCalibration r=$redGain g=$greenGain b=$blueGain gamma=$gamma")
        context.dataStore.edit {
            it[Keys.calRedGain] = redGain
            it[Keys.calGreenGain] = greenGain
            it[Keys.calBlueGain] = blueGain
            it[Keys.calGamma] = gamma
        }
    }

    suspend fun setTimer(on: Boolean, enabled: Boolean, hour: Int, minute: Int, weeks: Int) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val w = weeks and 0xFF
        AppLog.d(TAG, "setTimer ${if (on) "on" else "off"} enabled=$enabled $h:$m weeks=${Integer.toBinaryString(w)}")
        context.dataStore.edit {
            if (on) {
                it[Keys.timerOnEnabled] = enabled
                it[Keys.timerOnHour] = h
                it[Keys.timerOnMinute] = m
                it[Keys.timerOnWeeks] = w
            } else {
                it[Keys.timerOffEnabled] = enabled
                it[Keys.timerOffHour] = h
                it[Keys.timerOffMinute] = m
                it[Keys.timerOffWeeks] = w
            }
        }
    }

    private fun mapToSettings(prefs: Preferences): Settings {
        val settings = Settings(
            brightness = prefs[Keys.brightness] ?: 60,
            speed = prefs[Keys.speed] ?: 50,
            red = prefs[Keys.red] ?: 255,
            green = prefs[Keys.green] ?: 80,
            blue = prefs[Keys.blue] ?: 0,
            dwellMs = prefs[Keys.dwellMs] ?: 800,
            powerOn = prefs[Keys.powerOn] ?: false,
            sequenceJson = prefs[Keys.sequenceJson] ?: "[]",
            lastDeviceAddress = prefs[Keys.lastDeviceAddress] ?: "",
            lastDeviceName = prefs[Keys.lastDeviceName] ?: "",
            timerOnEnabled = prefs[Keys.timerOnEnabled] ?: false,
            timerOnHour = prefs[Keys.timerOnHour] ?: 8,
            timerOnMinute = prefs[Keys.timerOnMinute] ?: 0,
            timerOnWeeks = prefs[Keys.timerOnWeeks] ?: 0,
            timerOffEnabled = prefs[Keys.timerOffEnabled] ?: false,
            timerOffHour = prefs[Keys.timerOffHour] ?: 23,
            timerOffMinute = prefs[Keys.timerOffMinute] ?: 0,
            timerOffWeeks = prefs[Keys.timerOffWeeks] ?: 0,
            calRedGain = prefs[Keys.calRedGain] ?: 1f,
            calGreenGain = prefs[Keys.calGreenGain] ?: 1f,
            calBlueGain = prefs[Keys.calBlueGain] ?: 1f,
            calGamma = prefs[Keys.calGamma] ?: 1f,
            presetColors = prefs[Keys.presetColors] ?: "",
        )
        AppLog.v(
            TAG,
            "loaded brightness=${settings.brightness} speed=${settings.speed} " +
                "rgb=${settings.red},${settings.green},${settings.blue} " +
                "dwell=${settings.dwellMs} power=${settings.powerOn} " +
                "sequence=${settings.sequenceJson.length}b last=${settings.lastDeviceAddress} " +
                "timerOn=${settings.timerOnEnabled}@${settings.timerOnHour}:${settings.timerOnMinute}w${settings.timerOnWeeks} " +
                "timerOff=${settings.timerOffEnabled}@${settings.timerOffHour}:${settings.timerOffMinute}w${settings.timerOffWeeks} " +
                "cal=${settings.calRedGain},${settings.calGreenGain},${settings.calBlueGain},${settings.calGamma} " +
                "presets=${settings.presetColors.length}",
        )
        return settings
    }
}
