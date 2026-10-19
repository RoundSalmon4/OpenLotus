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

    private fun mapToSettings(prefs: Preferences): Settings =
        Settings(
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
        )
}
