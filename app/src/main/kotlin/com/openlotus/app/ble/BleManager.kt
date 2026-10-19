package com.openlotus.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

data class BleDevice(val name: String, val address: String)

/**
 * Wraps the framework BLE APIs. One connection at a time, which is all the
 * strips need. All calls are guarded because the permissions can be revoked
 * while the app is running.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val CHAR_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

        private val NAME_PREFIXES = listOf("ELK-", "ELK~", "ELK_", "CLK-", "XSL-", "LED LIGHT STRIP")

        fun isSupported(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            return NAME_PREFIXES.any { name.startsWith(it) }
        }
    }

    val devices = mutableStateListOf<BleDevice>()

    var connectedDevice by mutableStateOf<BleDevice?>(null)
        private set

    var status by mutableStateOf("Idle")
        private set

    private var gatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName ?: return
            if (!isSupported(name)) return
            if (devices.none { it.address == device.address }) {
                devices.add(BleDevice(name, device.address))
            }
        }
    }

    fun startScan() {
        val adapter = adapter()
        if (adapter == null) {
            status = "No Bluetooth adapter"
            return
        }
        if (!adapter.isEnabled) {
            status = "Bluetooth is off"
            return
        }
        devices.clear()
        try {
            val scanner = adapter.bluetoothLeScanner ?: run {
                status = "No BLE scanner"
                return
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
            status = "Scanning"
        } catch (e: SecurityException) {
            status = "Bluetooth permission missing"
        }
    }

    fun stopScan() {
        val adapter = adapter() ?: return
        try {
            adapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
        if (status == "Scanning") status = "Idle"
    }

    fun connect(device: BleDevice) {
        stopScan()
        disconnect()
        val adapter = adapter() ?: return
        try {
            val remote: BluetoothDevice = adapter.getRemoteDevice(device.address)
            status = "Connecting to ${device.name}"
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remote.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            status = "Bluetooth permission missing"
        } catch (e: IllegalArgumentException) {
            status = "Invalid device address"
        }
    }

    fun disconnect() {
        val current = gatt
        gatt = null
        writeCharacteristic = null
        connectedDevice = null
        if (current != null) {
            try {
                current.disconnect()
                current.close()
            } catch (_: SecurityException) {
            }
        }
        if (status != "Idle") status = "Disconnected"
    }

    @Suppress("DEPRECATION")
    fun send(frame: ByteArray) {
        val current = gatt ?: return
        val characteristic = writeCharacteristic ?: return
        try {
            val type = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                current.writeCharacteristic(characteristic, frame, type)
            } else {
                characteristic.writeType = type
                characteristic.value = frame
                current.writeCharacteristic(characteristic)
            }
        } catch (_: SecurityException) {
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = try {
                    BleDevice(gatt.device.name ?: "", gatt.device.address)
                } catch (_: SecurityException) {
                    BleDevice("", gatt.device.address)
                }
                this@BleManager.status = "Discovering services"
                try {
                    gatt.discoverServices()
                } catch (_: SecurityException) {
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                writeCharacteristic = null
                connectedDevice = null
                this@BleManager.status = "Disconnected"
                try {
                    gatt.close()
                } catch (_: SecurityException) {
                }
                if (this@BleManager.gatt === gatt) {
                    this@BleManager.gatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            val characteristic = service?.getCharacteristic(CHAR_UUID)
            if (characteristic == null) {
                this@BleManager.status = "Command characteristic not found"
                return
            }
            writeCharacteristic = characteristic
            try {
                gatt.requestMtu(63)
            } catch (_: SecurityException) {
            }
            this@BleManager.status = "Connected"
        }
    }
}
