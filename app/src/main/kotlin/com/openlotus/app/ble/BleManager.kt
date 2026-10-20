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
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.openlotus.app.log.AppLog
import com.openlotus.protocol.Frame
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BleDevice(val name: String, val address: String)

/**
 * Wraps the framework BLE APIs. One connection at a time, which is all the
 * strips need. All calls are guarded because the permissions can be revoked
 * while the app is running.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "ble"

        private val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val CHAR_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

        private val NAME_PREFIXES = listOf("ELK-", "ELK~", "ELK_", "CLK-", "XSL-", "LED LIGHT STRIP")

        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val FIRST_RECONNECT_DELAY_MS = 300L
        private const val RECONNECT_DELAY_MS = 2000L

        // The strip drops the link when it sees no traffic for a while, so a
        // harmless read every few seconds keeps the connection alive. Logged so
        // a capture can tell whether it actually helps.
        private const val KEEPALIVE_MS = 8000L

        // GATT allows one operation at a time, so writes are queued and sent one
        // at a time. The strip rejects a write with status 201 while busy.
        private const val MAX_QUEUED_WRITES = 16
        private const val WRITE_TIMEOUT_MS = 1200L
        private const val WRITE_RETRY_MS = 60L

        private fun isSupported(name: String?): Boolean {
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

    // Reconnect bookkeeping. lastDevice is the address we try to return to after
    // a drop. manualDisconnect suppresses reconnect when the user asked to stop.
    private var lastDevice: BleDevice? = null
    private var manualDisconnect = false
    private var reconnectAttempts = 0
    private var keepaliveRunnable: Runnable? = null
    private var lastWriteAt = 0L

    // GATT only allows one operation at a time. The strip rejects a write with
    // status 201 (busy) while another one is in flight, so sends are queued and
    // dribbled out one at a time. A small cap keeps a fast drag from piling up.
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false
    private var writeTimeout: Runnable? = null

    // GATT callbacks arrive on binder threads, so state changes are posted to
    // the main thread before they touch Compose state.
    private val main = Handler(Looper.getMainLooper())

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun adapter(): BluetoothAdapter? {
        AppLog.v(TAG, "adapter()")
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val result = manager?.adapter
        if (result == null) AppLog.e(TAG, "no bluetooth adapter")
        return result
    }

    private fun updateStatus(value: String) {
        post {
            AppLog.d(TAG, "status: $value")
            status = value
        }
    }

    // One advertisement line per device per scan. A busy area repeats the same
    // addresses many times a second, which would bury everything else.
    private val seenAddresses = ConcurrentHashMap.newKeySet<String>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName ?: return
            val supported = isSupported(name)
            if (seenAddresses.add(device.address)) {
                AppLog.v(TAG, "scan found $name ${device.address} rssi ${result.rssi} supported=$supported")
            }
            if (!supported) return
            post {
                if (devices.none { it.address == device.address }) {
                    AppLog.d(TAG, "found $name ${device.address}")
                    devices.add(BleDevice(name, device.address))
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            AppLog.e(TAG, "scan failed, error $errorCode")
            updateStatus("Scan failed ($errorCode)")
        }
    }

    fun startScan() {
        AppLog.d(TAG, "startScan()")
        val adapter = adapter()
        if (adapter == null) {
            updateStatus("No Bluetooth adapter")
            return
        }
        if (!adapter.isEnabled) {
            updateStatus("Bluetooth is off")
            return
        }
        devices.clear()
        seenAddresses.clear()
        try {
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                updateStatus("No BLE scanner")
                return
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
            updateStatus("Scanning")
        } catch (e: SecurityException) {
            AppLog.e(TAG, "startScan denied", e)
            updateStatus("Bluetooth permission missing")
        }
    }

    fun stopScan() {
        AppLog.d(TAG, "stopScan()")
        val adapter = adapter() ?: return
        try {
            adapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            AppLog.e(TAG, "stopScan denied", e)
        }
        if (status == "Scanning") updateStatus("Idle")
    }

    fun connect(device: BleDevice) {
        AppLog.d(TAG, "connect(${device.name} ${device.address})")
        stopScan()
        disconnect()
        manualDisconnect = false
        lastDevice = device
        reconnectAttempts = 0
        openGatt(device)
    }

    private fun openGatt(device: BleDevice) {
        val adapter = adapter() ?: return
        try {
            val remote: BluetoothDevice = adapter.getRemoteDevice(device.address)
            updateStatus("Connecting to ${device.name}")
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remote.connectGatt(context, false, gattCallback)
            }
            if (gatt == null) AppLog.e(TAG, "connectGatt returned null for ${device.address}")
        } catch (e: SecurityException) {
            AppLog.e(TAG, "connect denied", e)
            updateStatus("Bluetooth permission missing")
        } catch (e: IllegalArgumentException) {
            AppLog.e(TAG, "bad address ${device.address}", e)
            updateStatus("Invalid device address")
        }
    }

    fun disconnect() {
        AppLog.d(TAG, "disconnect()")
        manualDisconnect = true
        main.removeCallbacks(reconnectRunnable)
        stopKeepalive()
        val current = gatt
        gatt = null
        writeCharacteristic = null
        post { connectedDevice = null }
        if (current != null) {
            try {
                current.disconnect()
                current.close()
            } catch (e: SecurityException) {
                AppLog.e(TAG, "disconnect denied", e)
            }
        }
        if (status != "Idle") updateStatus("Disconnected")
    }

    private val reconnectRunnable = Runnable { attemptReconnect() }

    private fun scheduleReconnect() {
        val device = lastDevice
        if (manualDisconnect || device == null) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            AppLog.d(TAG, "giving up on ${device.address}")
            updateStatus("Disconnected, gave up")
            return
        }
        reconnectAttempts++
        val delay = if (reconnectAttempts == 1) FIRST_RECONNECT_DELAY_MS else RECONNECT_DELAY_MS
        AppLog.d(TAG, "reconnect $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${delay}ms")
        updateStatus("Reconnecting ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")
        main.removeCallbacks(reconnectRunnable)
        main.postDelayed(reconnectRunnable, delay)
    }

    private fun attemptReconnect() {
        if (manualDisconnect) return
        val device = lastDevice ?: return
        AppLog.d(TAG, "reconnecting to ${device.address}")
        openGatt(device)
    }

    // A benign GATT read keeps the link from idling out. fff3 advertises READ,
    // so this works without touching the lighting state.
    private fun startKeepalive() {
        stopKeepalive()
        val runnable = object : Runnable {
            override fun run() {
                val current = gatt
                val characteristic = writeCharacteristic
                if (current == null || characteristic == null) return
                if (writeInFlight || writeQueue.isNotEmpty()) {
                    main.postDelayed(this, KEEPALIVE_MS)
                    return
                }
                if (System.currentTimeMillis() - lastWriteAt < 1500L) {
                    main.postDelayed(this, KEEPALIVE_MS)
                    return
                }
                try {
                    val sent = current.readCharacteristic(characteristic)
                    AppLog.v(TAG, "keepalive read $sent")
                } catch (e: SecurityException) {
                    AppLog.e(TAG, "keepalive denied", e)
                } catch (e: Exception) {
                    AppLog.v(TAG, "keepalive skipped: ${e.javaClass.simpleName}")
                }
                main.postDelayed(this, KEEPALIVE_MS)
            }
        }
        keepaliveRunnable = runnable
        main.postDelayed(runnable, KEEPALIVE_MS)
        AppLog.d(TAG, "keepalive every ${KEEPALIVE_MS}ms")
    }

    private fun stopKeepalive() {
        keepaliveRunnable?.let { main.removeCallbacks(it) }
        keepaliveRunnable = null
    }

    @Suppress("DEPRECATION")
    fun send(frame: ByteArray) {
        AppLog.d(TAG, "send ${Frame.hex(frame)}")
        post { enqueueWrite(frame) }
    }

    private fun enqueueWrite(frame: ByteArray) {
        if (gatt == null || writeCharacteristic == null) {
            AppLog.e(TAG, "send dropped, not connected")
            return
        }
        if (writeQueue.size >= MAX_QUEUED_WRITES) {
            AppLog.v(TAG, "write queue full, dropping oldest")
            writeQueue.removeFirst()
        }
        writeQueue.addLast(frame)
        pumpWrites()
    }

    @Suppress("DEPRECATION")
    private fun pumpWrites() {
        if (writeInFlight) return
        val current = gatt
        val characteristic = writeCharacteristic
        if (current == null || characteristic == null) {
            writeQueue.clear()
            AppLog.e(TAG, "send dropped, not connected")
            return
        }
        val frame = writeQueue.removeFirstOrNull() ?: return
        lastWriteAt = System.currentTimeMillis()
        writeInFlight = true
        val type = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val watchdog = Runnable {
            if (writeInFlight) {
                AppLog.d(TAG, "write timed out, moving on")
                writeInFlight = false
                pumpWrites()
            }
        }
        writeTimeout = watchdog
        main.postDelayed(watchdog, WRITE_TIMEOUT_MS)
        try {
            AppLog.v(TAG, "write type $type, properties ${characteristic.properties}, queued ${writeQueue.size}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = current.writeCharacteristic(characteristic, frame, type)
                if (result != 0) {
                    AppLog.e(TAG, "write rejected, status $result")
                    onWriteFinished()
                    if (result == 201) {
                        writeQueue.addFirst(frame)
                        main.postDelayed({ pumpWrites() }, WRITE_RETRY_MS)
                    } else {
                        pumpWrites()
                    }
                }
            } else {
                characteristic.writeType = type
                characteristic.value = frame
                val accepted = current.writeCharacteristic(characteristic)
                if (!accepted) {
                    AppLog.e(TAG, "write rejected")
                    onWriteFinished()
                    pumpWrites()
                }
            }
        } catch (e: SecurityException) {
            AppLog.e(TAG, "send denied", e)
            onWriteFinished()
        }
    }

    private fun onWriteFinished() {
        writeTimeout?.let { main.removeCallbacks(it) }
        writeTimeout = null
        writeInFlight = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            AppLog.d(TAG, "connection state status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val device = try {
                    BleDevice(gatt.device.name ?: "", gatt.device.address)
                } catch (e: SecurityException) {
                    AppLog.e(TAG, "cannot read device name", e)
                    BleDevice("", gatt.device.address)
                }
                post { connectedDevice = device }
                reconnectAttempts = 0
                updateStatus("Discovering services")
                try {
                    if (!gatt.discoverServices()) {
                        AppLog.e(TAG, "discoverServices returned false")
                        updateStatus("Service discovery failed to start")
                    }
                } catch (e: SecurityException) {
                    AppLog.e(TAG, "discoverServices denied", e)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                AppLog.d(TAG, "disconnected from ${gatt.device.address}")
                writeCharacteristic = null
                writeQueue.clear()
                onWriteFinished()
                stopKeepalive()
                post { connectedDevice = null }
                try {
                    gatt.close()
                } catch (e: SecurityException) {
                    AppLog.e(TAG, "close denied", e)
                }
                if (this@BleManager.gatt === gatt) {
                    this@BleManager.gatt = null
                }
                if (manualDisconnect) {
                    updateStatus("Disconnected")
                } else {
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            AppLog.d(TAG, "services discovered status=$status")
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                AppLog.e(TAG, "service $SERVICE_UUID not found")
            }
            val characteristic = service?.getCharacteristic(CHAR_UUID)
            if (characteristic == null) {
                AppLog.e(TAG, "characteristic $CHAR_UUID not found")
                updateStatus("Command characteristic not found")
                return
            }
            writeCharacteristic = characteristic
            AppLog.d(TAG, "using characteristic $CHAR_UUID properties ${characteristic.properties}")
            try {
                val accepted = gatt.requestMtu(63)
                AppLog.v(TAG, "requestMtu accepted $accepted")
                if (!accepted) AppLog.e(TAG, "requestMtu rejected")
            } catch (e: SecurityException) {
                AppLog.e(TAG, "requestMtu denied", e)
            }
            try {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                AppLog.d(TAG, "asked for high connection priority")
            } catch (e: SecurityException) {
                AppLog.e(TAG, "requestConnectionPriority denied", e)
            }
            startKeepalive()
            updateStatus("Connected")
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            AppLog.d(TAG, "mtu $mtu status $status")
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            AppLog.v(TAG, "write complete status $status, queued ${writeQueue.size}")
            post {
                onWriteFinished()
                pumpWrites()
            }
        }
    }
}
