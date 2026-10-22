package com.openlotus.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.openlotus.app.ble.BleDevice
import com.openlotus.app.ble.BleManager
import com.openlotus.app.color.Calibration
import com.openlotus.app.color.ColorCal
import com.openlotus.app.data.SettingsStore
import com.openlotus.app.log.AppLog
import com.openlotus.protocol.Commands
import com.openlotus.protocol.LightMode
import com.openlotus.protocol.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Calendar
import kotlin.collections.mutableSetOf
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "ui"

private fun defaultPresetList() = listOf(
    AndroidColor.rgb(255,0,0),
    AndroidColor.rgb(0,255,0),
    AndroidColor.rgb(0,0,255),
    AndroidColor.rgb(0,255,255),
    AndroidColor.rgb(255,255,0),
    AndroidColor.rgb(160,0,255),
    AndroidColor.rgb(255,255,255),
    AndroidColor.rgb(255,160,60),
)
private fun presetsToJson(list: List<Int>): String {
    val arr = org.json.JSONArray()
    list.forEach { arr.put(it) }
    return arr.toString()
}
private fun jsonToPresets(json: String): MutableList<Int> {
    if (json.isBlank()) return defaultPresetList().toMutableList()
    return try {
        val arr = org.json.JSONArray(json)
        val list = mutableListOf<Int>()
        for (i in 0 until arr.length()) list.add(arr.getInt(i))
        if (list.isEmpty()) defaultPresetList().toMutableList() else list
    } catch (e: Exception) {
        AppLog.e(TAG, "parse presets failed ${e.message}")
        defaultPresetList().toMutableList()
    }
}

@Composable
fun OpenLotusApp() {
    AppLog.t(TAG, "OpenLotusApp()")
    val context = LocalContext.current
    val ble = remember { BleManager(context) }
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(context) }
    val player = remember { SequencePlayer { frame -> ble.send(frame) } }
    var tab by rememberSaveable { mutableStateOf(0) }

    // One place loads the saved calibration and pushes it into ColorCal, so every
    // send and every preview, in any tab, sees the same correction.
    LaunchedEffect(Unit) {
        store.settingsFlow.collect { s ->
            val cal = Calibration(s.calRedGain, s.calGreenGain, s.calBlueGain, s.calGamma)
            if (cal != ColorCal.current) {
                AppLog.d(
                    TAG,
                    "calibration loaded r=${s.calRedGain} g=${s.calGreenGain} b=${s.calBlueGain} gamma=${s.calGamma}",
                )
                ColorCal.current = cal
            }
        }
    }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    var permissionsGranted by remember { mutableStateOf(hasAllPermissions(context, permissions)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        AppLog.d("permissions", "result $result")
        permissionsGranted = hasAllPermissions(context, permissions)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsGranted = hasAllPermissions(context, permissions)
    }
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            AppLog.d(TAG, "requesting permissions ${permissions.toList()}")
            launcher.launch(permissions)
        } else {
            AppLog.d(TAG, "permissions already held")
        }
    }

    val connectedDevice = ble.connectedDevice
    LaunchedEffect(connectedDevice) {
        if (connectedDevice != null) {
            AppLog.d(TAG, "remembering last device ${connectedDevice.address}")
            store.setLastDevice(connectedDevice.address, connectedDevice.name)
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect
        if (ble.connectedDevice != null) return@LaunchedEffect
        val saved = store.settingsFlow.first()
        if (saved.lastDeviceAddress.isNotBlank()) {
            AppLog.d(TAG, "auto connecting to saved ${saved.lastDeviceAddress}")
            ble.connect(BleDevice(saved.lastDeviceName, saved.lastDeviceAddress))
        }
    }

    DisposableEffect(Unit) {
        AppLog.d(TAG, "app composed")
        onDispose {
            AppLog.d(TAG, "app disposed, stopping everything")
            player.stop()
            ble.stopScan()
            ble.disconnect()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (!permissionsGranted) {
            PermissionBanner(
                onRequest = {
                    AppLog.d(TAG, "request permissions pressed")
                    launcher.launch(permissions)
                },
                onSettings = {
                    AppLog.d(TAG, "open app settings pressed")
                    openAppSettings(context)
                },
            )
        }
        ScrollableTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { AppLog.d(TAG, "tab devices"); tab = 0 },
                text = { Text("Devices", maxLines = 1) },
            )
            Tab(
                selected = tab == 1,
                onClick = { AppLog.d(TAG, "tab control"); tab = 1 },
                text = { Text("Control", maxLines = 1) },
            )
            Tab(
                selected = tab == 2,
                onClick = { AppLog.d(TAG, "tab timer"); tab = 2 },
                text = { Text("Timer", maxLines = 1) },
            )
            Tab(
                selected = tab == 3,
                onClick = { AppLog.d(TAG, "tab log"); tab = 3 },
                text = { Text("Log", maxLines = 1) },
            )
            Tab(
                selected = tab == 4,
                onClick = { AppLog.d(TAG, "tab calibrate"); tab = 4 },
                text = { Text("Calibrate", maxLines = 1) },
            )
        }
        when (tab) {
            0 -> DeviceScreen(ble)
            1 -> ControlScreen(ble, player, scope, store)
            2 -> TimerScreen(ble, scope, store)
            3 -> LogScreen()
            else -> CalibrateScreen(ble, scope, store)
        }
    }
}

@Composable
private fun DeviceScreen(ble: BleManager) {
    AppLog.t(TAG, "DeviceScreen()")
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Status: ${ble.status}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AppLog.d(TAG, "scan pressed"); ble.startScan() }) { Text("Scan") }
            OutlinedButton(onClick = { AppLog.d(TAG, "stop scan pressed"); ble.stopScan() }) { Text("Stop") }
            OutlinedButton(
                onClick = { AppLog.d(TAG, "disconnect pressed"); ble.disconnect() },
                enabled = ble.connectedDevice != null,
            ) { Text("Disconnect") }
        }
        Spacer(Modifier.height(12.dp))
        if (ble.devices.isEmpty()) {
            Text(
                "No strips found yet. Power the strip on and keep the phone close.",
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ble.devices, key = { it.address }) { device ->
                    DeviceRow(device, ble.connectedDevice?.address == device.address) {
                        AppLog.d(TAG, "connect pressed for ${device.address}")
                        ble.connect(device)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice, connected: Boolean, onClick: () -> Unit) {
    AppLog.t(TAG, "DeviceRow(${device.address}, connected=$connected)")
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            if (connected) {
                Text("connected", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private val sequenceSaver = listSaver<MutableList<SequenceStep>, Int>(
    save = { list -> list.flatMap { listOf(it.color, it.dwellMs.toInt()) } },
    restore = { flat ->
        flat.chunked(2)
            .filter { it.size == 2 }
            .map { SequenceStep(it[0], it[1].toLong()) }
            .toMutableStateList()
    },
)


private fun sendCal(ble: BleManager, r:Int,g:Int,b:Int){ val (cr,cg,cb)=ColorCal.apply(r,g,b); ble.send(Commands.color(cr,cg,cb)) }
@Composable
private fun ControlScreen(
    ble: BleManager,
    player: SequencePlayer,
    scope: CoroutineScope,
    store: SettingsStore,
) {
    AppLog.t(TAG, "ControlScreen()")
    val connected = ble.connectedDevice != null
    var brightness by rememberSaveable { mutableStateOf(60) }
    var speed by rememberSaveable { mutableStateOf(50) }
    var red by rememberSaveable { mutableStateOf(255) }
    var green by rememberSaveable { mutableStateOf(80) }
    var blue by rememberSaveable { mutableStateOf(0) }
    var defaultDwell by rememberSaveable { mutableStateOf(800) }
    var selected by rememberSaveable { mutableStateOf(-1) }
    val sequence = rememberSaveable(saver = sequenceSaver) { mutableStateListOf<SequenceStep>() }
    var powerOn by rememberSaveable { mutableStateOf(false) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    var showWheel by rememberSaveable { mutableStateOf(false) }
    var wheelForStep by rememberSaveable { mutableStateOf(-1) }
    var showPresetEditor by rememberSaveable { mutableStateOf(false) }
    val presetColors = remember { mutableStateListOf<Int>() }
    var lastWheelSend by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        if (seeded) return@LaunchedEffect
        val loaded = store.settingsFlow.first()
        AppLog.d(TAG, "seeding controls from saved settings")
        brightness = loaded.brightness
        speed = loaded.speed
        red = loaded.red
        green = loaded.green
        blue = loaded.blue
        defaultDwell = loaded.dwellMs
        powerOn = loaded.powerOn
        selected = -1
        sequence.clear()
        sequence.addAll(jsonToSequence(loaded.sequenceJson).map { SequenceStep(it.first, it.second.toLong()) })
        presetColors.clear()
        presetColors.addAll(jsonToPresets(loaded.presetColors))
        seeded = true
    }

    val persistSequence: () -> Unit = {
        scope.launch {
            store.setSequenceJson(sequenceToJson(sequence.map { it.color to it.dwellMs.toInt() }))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!connected) {
            Text("Connect to a strip first.", style = MaterialTheme.typography.bodyMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Power", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = powerOn,
                onCheckedChange = { on ->
                    AppLog.d(TAG, "power ${if (on) "on" else "off"}")
                    powerOn = on
                    ble.send(Commands.power(on))
                    scope.launch { store.setPowerOn(on) }
                },
                enabled = connected,
            )
        }

        LabeledSlider(
            label = "Brightness $brightness",
            value = brightness.toFloat(),
            range = 0f..100f,
            enabled = connected,
            onChange = { brightness = it.toInt() },
            onFinished = {
                AppLog.d(TAG, "brightness $brightness")
                ble.send(Commands.brightness(brightness, LightMode.ALL))
                scope.launch { store.setBrightness(brightness) }
            },
        )

        LabeledSlider(
            label = "Effect speed $speed",
            value = speed.toFloat(),
            range = 0f..100f,
            enabled = connected,
            onChange = { speed = it.toInt() },
            onFinished = {
                AppLog.d(TAG, "speed $speed")
                ble.send(Commands.speed(speed))
                scope.launch { store.setSpeed(speed) }
            },
        )

        HorizontalDivider()
        Text("Color", style = MaterialTheme.typography.titleSmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color(AndroidColor.rgb(red, green, blue)))
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .clickable {
                    AppLog.d(TAG, "open color wheel")
                    showWheel = true
                }
        )
        Text(
            "Tap the swatch for a color wheel, or use the sliders.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (showWheel) {
            val editingStep = wheelForStep in sequence.indices
            ColorWheelDialog(
                initialRed = if (editingStep) AndroidColor.red(sequence[wheelForStep].color) else red,
                initialGreen = if (editingStep) AndroidColor.green(sequence[wheelForStep].color) else green,
                initialBlue = if (editingStep) AndroidColor.blue(sequence[wheelForStep].color) else blue,
                onDismiss = { showWheel = false; wheelForStep = -1 },
                onConfirm = { r, g, b ->
                    if (editingStep) {
                        val argb = AndroidColor.rgb(r, g, b)
                        AppLog.d(TAG, "sequence recolor $wheelForStep ${hex(argb)}")
                        sequence[wheelForStep] = sequence[wheelForStep].copy(color = argb)
                        persistSequence()
                        if (player.playing) {
                            AppLog.d(TAG, "restart sequence from step ${player.currentIndex}")
                            player.start(scope, sequence.toList(), player.currentIndex)
                        }
                    } else {
                        AppLog.d(TAG, "wheel color $r,$g,$b")
                        red = r
                        green = g
                        blue = b
                        player.stop()
                        sendCal(ble, r, g, b)
                        scope.launch { store.setColor(r, g, b) }
                    }
                    showWheel = false
                    wheelForStep = -1
                },
                onLive = { r, g, b ->
                    AppLog.t(TAG, "wheel live $r,$g,$b")
                    if (editingStep) {
                        sequence[wheelForStep] = sequence[wheelForStep].copy(color = AndroidColor.rgb(r, g, b))
                        persistSequence()
                    } else {
                        red = r
                        green = g
                        blue = b
                    }
                    if (connected) {
                        val now = System.currentTimeMillis()
                        if (now - lastWheelSend > 80L) {
                            lastWheelSend = now
                            player.stop()
                            sendCal(ble, r, g, b)
                        }
                    }
                },
            )
        }
        if (showPresetEditor) {
            PresetEditorDialog(
                presets = presetColors,
                onDismiss = { showPresetEditor = false },
                onChange = {
                    scope.launch { store.setPresetColors(presetsToJson(presetColors)) }
                },
                currentRgb = { AndroidColor.rgb(red, green, blue) },
            )
        }
        LabeledSlider("Red $red", red.toFloat(), 0f..255f, connected, { red = it.toInt() }, {
            AppLog.d(TAG, "color $red,$green,$blue")
            player.stop()
            sendCal(ble,red,green,blue)
            scope.launch { store.setColor(red,green,blue) }
        })
        LabeledSlider("Green $green", green.toFloat(), 0f..255f, connected, { green = it.toInt() }, {
            AppLog.d(TAG, "color $red,$green,$blue")
            player.stop()
            sendCal(ble,red,green,blue)
            scope.launch { store.setColor(red,green,blue) }
        })
        LabeledSlider("Blue $blue", blue.toFloat(), 0f..255f, connected, { blue = it.toInt() }, {
            AppLog.d(TAG, "color $red,$green,$blue")
            player.stop()
            sendCal(ble,red,green,blue)
            scope.launch { store.setColor(red,green,blue) }
        })

        HorizontalDivider()
        Text("Modes", style = MaterialTheme.typography.titleSmall)
        Mode.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { mode ->
                    OutlinedButton(
                        onClick = {
                            AppLog.d(TAG, "mode ${mode.name} (0x%02X)".format(mode.code))
                            player.stop()
                            ble.send(Commands.mode(mode))
                        },
                        enabled = connected,
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Text(modeLabel(mode), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        HorizontalDivider()
        Text("Color sequence", style = MaterialTheme.typography.titleSmall)
        Text(
            "Tap presets to add them, then tap a swatch to give it its own hold time.",
            style = MaterialTheme.typography.bodySmall,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(presetColors.size) { i ->
                val argb = presetColors[i]
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable {
                            AppLog.d(TAG, "sequence add preset ${hex(argb)}")
                            sequence.add(SequenceStep(argb, defaultDwell.toLong()))
                            selected = sequence.lastIndex
                            persistSequence()
                        },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                AppLog.d(TAG, "open preset editor")
                showPresetEditor = true
            }) {
                Text("Edit presets")
            }
            OutlinedButton(onClick = {
                val argb = AndroidColor.rgb(red, green, blue)
                AppLog.d(TAG, "add preset ${hex(argb)}")
                presetColors.add(argb)
                scope.launch { store.setPresetColors(presetsToJson(presetColors)) }
            }) {
                Text("Add current as preset")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val argb = AndroidColor.rgb(red, green, blue)
                AppLog.d(TAG, "sequence add current ${hex(argb)}")
                sequence.add(SequenceStep(argb, defaultDwell.toLong()))
                selected = sequence.lastIndex
                persistSequence()
            }) {
                Text("Add current color")
            }
            OutlinedButton(onClick = {
                AppLog.d(TAG, "sequence add off")
                sequence.add(SequenceStep(0x000000, defaultDwell.toLong()))
                selected = sequence.lastIndex
                persistSequence()
            }) {
                Text("Add off")
            }
        }
        val nowPlaying = if (player.playing) sequence.getOrNull(player.currentIndex)?.color else null
        Text(
            if (nowPlaying != null) {
                "Now playing ${hex(nowPlaying)} at step ${player.currentIndex + 1} of ${sequence.size}"
            } else {
                "Not playing"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (sequence.isEmpty()) {
            Text("No colors yet", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(sequence.size) { index ->
                    val step = sequence[index]
                    val active = player.playing && player.currentIndex == index
                    val isSelected = selected == index
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(step.color))
                            .border(
                                width = when {
                                    active -> 3.dp
                                    isSelected -> 2.dp
                                    else -> 1.dp
                                },
                                color = when {
                                    active -> MaterialTheme.colorScheme.primary
                                    isSelected -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape,
                            )
                            .clickable {
                                AppLog.d(TAG, "sequence select $index ${hex(step.color)}")
                                selected = index
                            },
                    )
                }
            }
        }
        Text(
            if (selected in sequence.indices) {
                "Selected step ${selected + 1}: ${hex(sequence[selected].color)} held ${sequence[selected].dwellMs}ms"
            } else {
                "No step selected"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (selected in sequence.indices) {
                        val argb = AndroidColor.rgb(red, green, blue)
                        AppLog.d(TAG, "sequence recolor $selected ${hex(argb)}")
                        sequence[selected] = sequence[selected].copy(color = argb)
                        persistSequence()
                    }
                },
                enabled = selected in sequence.indices,
            ) {
                Text("Recolor selected with current RGB")
            }
            OutlinedButton(
                onClick = { if (selected in sequence.indices) { wheelForStep = selected; showWheel = true } },
                enabled = selected in sequence.indices,
            ) {
                Text("Pick color for selected")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (selected in sequence.indices) {
                        AppLog.d(TAG, "sequence remove selected $selected")
                        sequence.removeAt(selected)
                        selected = -1
                        persistSequence()
                    }
                },
                enabled = selected in sequence.indices,
            ) {
                Text("Remove selected")
            }
            OutlinedButton(onClick = {
                AppLog.d(TAG, "sequence clear")
                sequence.clear()
                selected = -1
                player.stop()
                persistSequence()
            }) {
                Text("Clear")
            }
        }
        LabeledSlider(
            label = if (selected in sequence.indices) {
                "Hold selected color ${sequence[selected].dwellMs}ms"
            } else {
                "Hold for new colors ${defaultDwell}ms"
            },
            value = (if (selected in sequence.indices) sequence[selected].dwellMs else defaultDwell.toLong()).toFloat(),
            range = 100f..3000f,
            enabled = true,
            onChange = { value ->
                val v = value.toInt()
                if (selected in sequence.indices) {
                    sequence[selected] = sequence[selected].copy(dwellMs = v.toLong())
                } else {
                    defaultDwell = v
                    for (i in sequence.indices) {
                        sequence[i] = sequence[i].copy(dwellMs = v.toLong())
                    }
                }
            },
            onFinished = {
                if (selected in sequence.indices) {
                    AppLog.d(TAG, "step $selected dwell ${sequence[selected].dwellMs}")
                } else {
                    AppLog.d(TAG, "default dwell $defaultDwell")
                    scope.launch { store.setDwell(defaultDwell) }
                }
                persistSequence()
                if (player.playing) {
                    AppLog.d(TAG, "restart sequence from step ${player.currentIndex}")
                    player.start(scope, sequence.toList(), player.currentIndex)
                }
            },
        )
        Button(
            onClick = {
                AppLog.d(TAG, "sequence button, playing=${player.playing}, steps=${sequence.size}")
                player.toggle(scope, sequence.toList(), 0)
            },
            enabled = connected && sequence.isNotEmpty(),
        ) {
            Text(if (player.playing) "Stop sequence" else "Start sequence")
        }

        HorizontalDivider()
        OutlinedButton(
            onClick = {
                val now = Calendar.getInstance()
                val weekday = now.get(Calendar.DAY_OF_WEEK) - 1
                AppLog.d(TAG, "sync clock pressed, weekday $weekday")
                ble.send(
                    Commands.systemTime(
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        now.get(Calendar.SECOND),
                        weekday,
                    )
                )
            },
            enabled = connected,
        ) {
            Text("Sync clock")
        }
    }
}

@Composable
private fun LogScreen() {
    AppLog.t(TAG, "LogScreen()")
    val listState = rememberLazyListState()
    val count = AppLog.lines.size
    LaunchedEffect(count) {
        if (count > 0) {
            listState.scrollToItem(count - 1)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                AppLog.d(TAG, "log cleared")
                AppLog.clear()
            }) { Text("Clear") }
            Text("$count lines kept, logcat has everything", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(AppLog.lines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    steps: Int = 0,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = {
                AppLog.t(TAG, "slider $label -> $it")
                onChange(it)
            },
            onValueChangeFinished = {
                AppLog.t(TAG, "slider $label finished")
                onFinished()
            },
            valueRange = range,
            steps = steps,
            enabled = enabled,
        )
    }
}

@Composable
private fun PresetEditorDialog(
    presets: MutableList<Int>,
    onDismiss: () -> Unit,
    onChange: () -> Unit,
    currentRgb: () -> Int,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Edit presets", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Presets are the quick swatches under the sequence. They only change the list here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (presets.isEmpty()) {
                    Text("No presets yet", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(presets.size) { i ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(presets[i]))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                )
                                Text(hex(presets[i]), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = {
                                    val argb = currentRgb()
                                    AppLog.d(TAG, "preset $i replace ${hex(argb)}")
                                    presets[i] = argb
                                    onChange()
                                }) {
                                    Text("Set current")
                                }
                                OutlinedButton(onClick = {
                                    AppLog.d(TAG, "preset $i remove")
                                    presets.removeAt(i)
                                    onChange()
                                }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val argb = currentRgb()
                        AppLog.d(TAG, "preset add ${hex(argb)}")
                        presets.add(argb)
                        onChange()
                    }) {
                        Text("Add current color")
                    }
                    Button(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun ColorWheelDialog(
    initialRed: Int,
    initialGreen: Int,
    initialBlue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
    onLive: ((Int,Int,Int)->Unit)? = null,
) {
    val initial = remember {
        FloatArray(3).also { AndroidColor.colorToHSV(AndroidColor.rgb(initialRed, initialGreen, initialBlue), it) }
    }
    var hue by remember { mutableStateOf(initial[0]) }
    var saturation by remember { mutableStateOf(initial[1]) }
    var value by remember { mutableStateOf(initial[2]) }
    val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Pick a color", style = MaterialTheme.typography.titleSmall)
                HsvWheel(hue = hue, saturation = saturation) { h, s ->
                    hue = h
                    saturation = s
                    val nr = AndroidColor.red(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                    val ng = AndroidColor.green(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                    val nb = AndroidColor.blue(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                    onLive?.invoke(nr,ng,nb)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(Color(rgb)),
                )
                LabeledSlider(
                    label = "Value ${(value * 100).roundToInt()}%",
                    value = value,
                    range = 0f..1f,
                    enabled = true,
                    onChange = { 
                        value = it
                        val nr = AndroidColor.red(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                        val ng = AndroidColor.green(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                        val nb = AndroidColor.blue(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                        onLive?.invoke(nr,ng,nb)
                    },
                    onFinished = {},
                )
                Text(hex(rgb), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            onConfirm(
                                AndroidColor.red(rgb),
                                AndroidColor.green(rgb),
                                AndroidColor.blue(rgb),
                            )
                        },
                    ) { Text("Use color") }
                }
            }
        }
    }
}

@Composable
private fun HsvWheel(hue: Float, saturation: Float, onChange: (Float, Float) -> Unit) {
    val hueColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
    )
    Canvas(
        modifier = Modifier
            .size(220.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.width / 2f
                    fun report(position: Offset) {
                        val dx = position.x - center.x
                        val dy = position.y - center.y
                        var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (degrees < 0f) degrees += 360f
                        val distance = sqrt(dx * dx + dy * dy)
                        onChange(degrees % 360f, (distance / maxRadius).coerceIn(0f, 1f))
                    }
                    val down = awaitFirstDown()
                    report(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        report(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        drawCircle(
            brush = Brush.sweepGradient(hueColors, center),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        val angle = Math.toRadians(hue.toDouble())
        val thumbRadius = (saturation * radius).toDouble()
        val thumb = Offset(
            center.x + (cos(angle) * thumbRadius).toFloat(),
            center.y + (sin(angle) * thumbRadius).toFloat(),
        )
        drawCircle(color = Color.Black, radius = 9f, center = thumb)
        drawCircle(color = Color.White, radius = 6f, center = thumb)
    }
}

@Composable
private fun CalibrateScreen(ble: BleManager, scope: CoroutineScope, store: SettingsStore) {
    AppLog.t(TAG, "CalibrateScreen()")
    val connected = ble.connectedDevice != null
    var redGain by rememberSaveable { mutableStateOf(1f) }
    var greenGain by rememberSaveable { mutableStateOf(1f) }
    var blueGain by rememberSaveable { mutableStateOf(1f) }
    var gamma by rememberSaveable { mutableStateOf(1f) }
    var stage by rememberSaveable { mutableStateOf(0) }
    var patchIndex by rememberSaveable { mutableStateOf(0) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var seeded by rememberSaveable { mutableStateOf(false) }
    // Track which patches the user has confirmed as matching
    var confirmedPatches by rememberSaveable { mutableStateOf(mutableSetOf<Int>()) }

    LaunchedEffect(Unit) {
        if (seeded) return@LaunchedEffect
        val loaded = store.settingsFlow.first()
        redGain = loaded.calRedGain
        greenGain = loaded.calGreenGain
        blueGain = loaded.calBlueGain
        gamma = loaded.calGamma
        seeded = true
    }

    // Patch definitions: name -> (target RGB, hint for adjustment)
    val patches: List<Pair<String, Triple<Triple<Int, Int, Int>, String>>> = listOf(
        "White" to Triple(Triple(255, 255, 255), "Adjust white balance in Step 1"),
        "Gray" to Triple(Triple(128, 128, 128), "Adjust gamma in Step 2"),
        "Red" to Triple(Triple(255, 0, 0), "If too orange: lower green. If too pink: lower blue."),
        "Green" to Triple(Triple(0, 255, 0), "If too yellow: lower red. If too cyan: lower blue."),
        "Blue" to Triple(Triple(0, 0, 255), "If too cyan: lower green. If too purple: lower red."),
        "Yellow" to Triple(Triple(255, 255, 0), "If too green: lower green. If too orange: lower red."),
        "Cyan" to Triple(Triple(0, 255, 255), "If too green: lower green. If too blue: lower blue."),
        "Magenta" to Triple(Triple(255, 0, 255), "If too red: lower red. If too blue: lower blue."),
        "Orange" to Triple(Triple(255, 128, 0), "If too yellow: lower green. If too red: lower red."),
    )

    // Current target color based on stage
    val target: Triple<Int, Int, Int> = when (stage) {
        0 -> Triple(255, 255, 255)
        1 -> Triple(64, 64, 64)
        2 -> patches[patchIndex.coerceIn(0, patches.lastIndex)].second.first
        else -> Triple(255, 255, 255)
    }

    // Current patch hint (for step 3)
    val currentPatchHint: String = when (stage) {
        2 -> patches[patchIndex.coerceIn(0, patches.lastIndex)].second.second
        else -> ""
    }

    // Current patch name (for step 3)
    val currentPatchName: String = when (stage) {
        2 -> patches[patchIndex.coerceIn(0, patches.lastIndex)].first
        else -> ""
    }

    // Whether current patch is confirmed
    val isCurrentPatchConfirmed: Boolean = confirmedPatches.contains(patchIndex)

    // All patches confirmed (for enabling save)
    val allPatchesConfirmed = confirmedPatches.size == patches.size

    fun applyCal() {
        ColorCal.current = Calibration(redGain, greenGain, blueGain, gamma)
    }

    fun sendTarget() {
        applyCal()
        val (r, g, b) = target
        if (connected) {
            AppLog.d(TAG, "cal target ${hex(AndroidColor.rgb(r, g, b))} -> out ${hex(ColorCal.packed(r, g, b))}")
            ble.send(ColorCal.frame(r, g, b))
        }
    }

    fun nudge(scaleR: Float, scaleG: Float, scaleB: Float) {
        redGain = (redGain * scaleR).coerceIn(0.05f, 2f)
        greenGain = (greenGain * scaleG).coerceIn(0.05f, 2f)
        blueGain = (blueGain * scaleB).coerceIn(0.05f, 2f)
        sendTarget()
    }

    fun resetStep(step: Int) {
        when (step) {
            0 -> { redGain = 1f; greenGain = 1f; blueGain = 1f }
            1 -> { gamma = 1f }
            2 -> { confirmedPatches.remove(patchIndex) }
        }
        sendTarget()
    }

    LaunchedEffect(stage, patchIndex, connected) {
        if (connected) sendTarget()
    }

    // Stepper labels
    val stepLabels = listOf("White Balance", "Gamma / Dim", "Color Check")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!connected) {
            Text("Connect to a strip first.", style = MaterialTheme.typography.bodyMedium)
        }

        // Progress stepper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            stepLabels.forEachIndexed { index, label ->
                val isCurrent = stage == index
                val isDone = stage > index || (stage == 3 && index < 3)
                val color = if (isDone) MaterialTheme.colorScheme.primary
                else if (isCurrent) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outline
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isDone || isCurrent) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(color = color),
                        textAlign = TextAlign.Center,
                    )
                }
                if (index < stepLabels.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (stage > index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            .padding(top = 10.dp),
                    )
                }
            }
        }

        HorizontalDivider()

        Text("Calibrate the strip", style = MaterialTheme.typography.titleSmall)
        Text(
            "This strip's red, green and blue are not even, so the same numbers can " +
                "look wrong. You tune it once by eye here, and then every color the app " +
                "sends, plus the sequence, is corrected to match what you picked.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            "Keep looking at the strip. Each step shows sliders so you can adjust " +
                "by eye instead of tapping blindly.",
            style = MaterialTheme.typography.bodySmall,
        )

        // Reference swatch (target color on phone screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color(AndroidColor.rgb(target.first, target.second, target.third)))
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Match the light to this",
                color = if (target.first + target.second + target.third > 380) Color.Black else Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // Corrected preview swatch (what the strip should show after calibration)
        val (cr, cg, cb) = ColorCal.apply(target.first, target.second, target.third)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color(AndroidColor.rgb(cr, cg, cb)))
                .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Corrected preview: ${hex(AndroidColor.rgb(cr, cg, cb))}",
                color = if (cr + cg + cb > 380) Color.Black else Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Text(
            "Sending ${hex(AndroidColor.rgb(target.first, target.second, target.third))} " +
                "as ${hex(ColorCal.packed(target.first, target.second, target.third))} after correction",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        when (stage) {
            0 -> { // Step 1: White Balance
                Text("Step 1 of 3: White Balance", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The strip is showing white. Adjust each channel until the light looks like a clean white " +
                        "with no color tint. The sliders show the current gain for each channel.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // Red gain slider
                LabeledSlider(
                    label = "Red gain ${"%.2f".format(redGain)} (lower if pink/red tint)",
                    value = redGain,
                    range = 0.05f..2f,
                    enabled = connected,
                    onChange = { redGain = it },
                    onFinished = { sendTarget() },
                )
                // Green gain slider
                LabeledSlider(
                    label = "Green gain ${"%.2f".format(greenGain)} (lower if green/yellow tint)",
                    value = greenGain,
                    range = 0.05f..2f,
                    enabled = connected,
                    onChange = { greenGain = it },
                    onFinished = { sendTarget() },
                )
                // Blue gain slider
                LabeledSlider(
                    label = "Blue gain ${"%.2f".format(blueGain)} (lower if blue/cold tint)",
                    value = blueGain,
                    range = 0.05f..2f,
                    enabled = connected,
                    onChange = { blueGain = it },
                    onFinished = { sendTarget() },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        AppLog.d(TAG, "cal reset step 1")
                        redGain = 1f; greenGain = 1f; blueGain = 1f
                        sendTarget()
                    }) { Text("Reset this step") }
                    Button(
                        onClick = { AppLog.d(TAG, "cal to step 2"); stage = 1 },
                        enabled = connected,
                    ) { Text("White looks neutral →") }
                }
            }

            1 -> { // Step 2: Gamma / Dim Level
                Text("Step 2 of 3: Gamma / Dim Level", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The strip is showing a dim gray. Adjust gamma until the brightness matches the reference patch. " +
                        "Lower gamma = darker at low levels. Higher gamma = brighter at low levels.",
                    style = MaterialTheme.typography.bodySmall,
                )

                LabeledSlider(
                    label = "Gamma ${"%.2f".format(gamma)} (shapes dim end, 1.00 is neutral)",
                    value = gamma,
                    range = 0.5f..2f,
                    enabled = connected,
                    onChange = { gamma = it },
                    onFinished = { sendTarget() },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        AppLog.d(TAG, "cal reset step 2")
                        gamma = 1f
                        sendTarget()
                    }) { Text("Reset this step") }
                    OutlinedButton(onClick = { AppLog.d(TAG, "cal back to step 1"); stage = 0 }) {
                        Text("← Back")
                    }
                    Button(
                        onClick = { AppLog.d(TAG, "cal to step 3"); patchIndex = 0; stage = 2 },
                        enabled = connected,
                    ) { Text("Gray matches →") }
                }
            }

            2 -> { // Step 3: Color Verification
                Text("Step 3 of 3: Color Check", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The strip cycles through reference colors. For each one, confirm it matches the patch " +
                        "or adjust the relevant channel. Tap 'This matches' to mark it done.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Text(
                    "Patch ${patchIndex + 1} of ${patches.size}: $currentPatchName",
                    style = MaterialTheme.typography.titleSmall,
                )

                // Patch hint
                if (currentPatchHint.isNotBlank()) {
                    Text(
                        "Hint: $currentPatchHint",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary),
                    )
                }

                // Navigation
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { patchIndex = (patchIndex - 1 + patches.size) % patches.size },
                        enabled = patchIndex > 0,
                    ) { Text("← Previous") }
                    if (patchIndex < patches.lastIndex) {
                        OutlinedButton(
                            onClick = { patchIndex += 1 },
                        ) { Text("Next →") }
                    }
                }

                // Confirm / Adjust buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            AppLog.d(TAG, "cal patch $patchIndex confirmed")
                            confirmedPatches.add(patchIndex)
                            if (patchIndex < patches.lastIndex) patchIndex += 1
                        },
                        enabled = connected,
                        colors = if (isCurrentPatchConfirmed)
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else ButtonDefaults.buttonColors(),
                    ) {
                        Text(if (isCurrentPatchConfirmed) "✓ This matches" else "This matches")
                    }
                    OutlinedButton(
                        onClick = { AppLog.d(TAG, "cal back to white"); stage = 0 },
                    ) { Text("← Back to white") }
                }

                // Show adjustment slider for the current patch based on hint
                // Parse hint to determine which slider to show
                val showRedSlider = currentPatchHint.contains("lower red") || currentPatchHint.contains("too red") || currentPatchHint.contains("too pink")
                val showGreenSlider = currentPatchHint.contains("lower green") || currentPatchHint.contains("too green") || currentPatchHint.contains("too yellow") || currentPatchHint.contains("too cyan")
                val showBlueSlider = currentPatchHint.contains("lower blue") || currentPatchHint.contains("too blue") || currentPatchHint.contains("too purple") || currentPatchHint.contains("too cyan")

                if (showRedSlider) {
                    LabeledSlider(
                        label = "Red gain ${"%.2f".format(redGain)} (lower if too red/pink)",
                        value = redGain,
                        range = 0.05f..2f,
                        enabled = connected,
                        onChange = { redGain = it },
                        onFinished = { sendTarget() },
                    )
                }
                if (showGreenSlider) {
                    LabeledSlider(
                        label = "Green gain ${"%.2f".format(greenGain)} (lower if too green/yellow/cyan)",
                        value = greenGain,
                        range = 0.05f..2f,
                        enabled = connected,
                        onChange = { greenGain = it },
                        onFinished = { sendTarget() },
                    )
                }
                if (showBlueSlider) {
                    LabeledSlider(
                        label = "Blue gain ${"%.2f".format(blueGain)} (lower if too blue/cyan/purple)",
                        value = blueGain,
                        range = 0.05f..2f,
                        enabled = connected,
                        onChange = { blueGain = it },
                        onFinished = { sendTarget() },
                    )
                }

                // Save button (enabled when all patches confirmed or user forces it)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { AppLog.d(TAG, "cal reset step 3"); confirmedPatches.clear() }) {
                        Text("Reset all confirmations")
                    }
                    Button(
                        onClick = {
                            applyCal()
                            AppLog.d(TAG, "save calibration r=$redGain g=$greenGain b=$blueGain gamma=$gamma")
                            scope.launch { store.setCalibration(redGain, greenGain, blueGain, gamma) }
                            stage = 3
                        },
                        enabled = connected && (allPatchesConfirmed || confirmedPatches.isNotEmpty()),
                    ) {
                        Text(if (allPatchesConfirmed) "All confirmed – Save" else "Save calibration (${confirmedPatches.size}/${patches.size} confirmed)")
                    }
                }
            }

            else -> { // stage == 3: Saved
                Text("Saved", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Done. The correction is applied to colors you pick and to the " +
                        "sequence. Note that the built in modes and brightness are the " +
                        "strip's own effects, so they are not corrected.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    AppLog.d(TAG, "cal run again")
                    stage = 0
                    patchIndex = 0
                    confirmedPatches.clear()
                }) {
                    Text("Run calibration again")
                }
            }
        }

        // Advanced manual sliders (collapsible, always available)
        OutlinedButton(onClick = {
            AppLog.d(TAG, "cal advanced toggle $showAdvanced")
            showAdvanced = !showAdvanced
        }) {
            Text(if (showAdvanced) "Hide manual sliders" else "Show manual sliders")
        }
        if (showAdvanced) {
            Text(
                "Manual control. Red, green and blue set how strong each channel is: " +
                    "lower a channel if the light has too much of it. Gamma shapes the " +
                    "dim end only, leave it at 1.00 unless the white is right but low " +
                    "levels look off.",
                style = MaterialTheme.typography.bodySmall,
            )
            LabeledSlider(
                label = "Red level ${"%.2f".format(redGain)} (lower if pink or red tinted)",
                value = redGain,
                range = 0.05f..2f,
                enabled = true,
                onChange = { redGain = it },
                onFinished = { sendTarget() },
            )
            LabeledSlider(
                label = "Green level ${"%.2f".format(greenGain)} (lower if green or yellow tinted, the usual fix)",
                value = greenGain,
                range = 0.05f..2f,
                enabled = true,
                onChange = { greenGain = it },
                onFinished = { sendTarget() },
            )
            LabeledSlider(
                label = "Blue level ${"%.2f".format(blueGain)} (lower if blue or cold tinted)",
                value = blueGain,
                range = 0.05f..2f,
                enabled = true,
                onChange = { blueGain = it },
                onFinished = { sendTarget() },
            )
            LabeledSlider(
                label = "Gamma ${"%.2f".format(gamma)} (shapes the dim end, 1.00 is neutral)",
                value = gamma,
                range = 0.5f..2f,
                enabled = true,
                onChange = { gamma = it },
                onFinished = { sendTarget() },
            )
        }

        Text(
            "Current: red ${"%.2f".format(redGain)} green ${"%.2f".format(greenGain)} " +
                "blue ${"%.2f".format(blueGain)} gamma ${"%.2f".format(gamma)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
@Composable
private fun TimerScreen(ble: BleManager, scope: CoroutineScope, store: SettingsStore) {
    AppLog.t(TAG, "TimerScreen()")
    val connected = ble.connectedDevice != null
    var onEnabled by rememberSaveable { mutableStateOf(false) }
    var onHour by rememberSaveable { mutableStateOf(8) }
    var onMinute by rememberSaveable { mutableStateOf(0) }
    var onWeeks by rememberSaveable { mutableStateOf(0) }
    var offEnabled by rememberSaveable { mutableStateOf(false) }
    var offHour by rememberSaveable { mutableStateOf(23) }
    var offMinute by rememberSaveable { mutableStateOf(0) }
    var offWeeks by rememberSaveable { mutableStateOf(0) }
    var seeded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (seeded) return@LaunchedEffect
        val loaded = store.settingsFlow.first()
        onEnabled = loaded.timerOnEnabled
        onHour = loaded.timerOnHour
        onMinute = loaded.timerOnMinute
        onWeeks = loaded.timerOnWeeks
        offEnabled = loaded.timerOffEnabled
        offHour = loaded.timerOffHour
        offMinute = loaded.timerOffMinute
        offWeeks = loaded.timerOffWeeks
        seeded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!connected) {
            Text("Connect to a strip first.", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Timer", style = MaterialTheme.typography.titleSmall)
        Text(
            "The strip runs these two times itself, so they still fire when the phone is away. " +
                "Pick your days, then send once. Sending also sets the strip's clock.",
            style = MaterialTheme.typography.bodySmall,
        )
        TimerEditor(
            title = "Turn on at",
            enabled = onEnabled,
            onEnabledChange = { onEnabled = it },
            hour = onHour,
            onHourChange = { onHour = it },
            minute = onMinute,
            onMinuteChange = { onMinute = it },
            weeks = onWeeks,
            onWeeksChange = { onWeeks = it },
        )
        HorizontalDivider()
        TimerEditor(
            title = "Turn off at",
            enabled = offEnabled,
            onEnabledChange = { offEnabled = it },
            hour = offHour,
            onHourChange = { offHour = it },
            minute = offMinute,
            onMinuteChange = { offMinute = it },
            weeks = offWeeks,
            onWeeksChange = { offWeeks = it },
        )
        Button(
            onClick = {
                val now = Calendar.getInstance()
                val weekday = now.get(Calendar.DAY_OF_WEEK) - 1
                AppLog.d(
                    TAG,
                    "send timers on=$onEnabled $onHour:$onMinute w=$onWeeks " +
                        "off=$offEnabled $offHour:$offMinute w=$offWeeks",
                )
                ble.send(
                    Commands.systemTime(
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        now.get(Calendar.SECOND),
                        weekday,
                    )
                )
                ble.send(Commands.schedule(onHour, onMinute, 0, 0, weeksWithEnable(onEnabled, onWeeks)))
                ble.send(Commands.schedule(offHour, offMinute, 0, 1, weeksWithEnable(offEnabled, offWeeks)))
                scope.launch {
                    store.setTimer(on = true, enabled = onEnabled, hour = onHour, minute = onMinute, weeks = onWeeks)
                    store.setTimer(on = false, enabled = offEnabled, hour = offHour, minute = offMinute, weeks = offWeeks)
                }
            },
            enabled = connected,
        ) {
            Text("Send to strip")
        }
    }
}

@Composable
private fun TimerEditor(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hour: Int,
    onHourChange: (Int) -> Unit,
    minute: Int,
    onMinuteChange: (Int) -> Unit,
    weeks: Int,
    onWeeksChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        LabeledSlider(
            label = "Hour $hour",
            value = hour.toFloat(),
            range = 0f..23f,
            enabled = enabled,
            onChange = { onHourChange(it.toInt()) },
            onFinished = {},
            steps = 22,
        )
        LabeledSlider(
            label = "Minute $minute",
            value = minute.toFloat(),
            range = 0f..59f,
            enabled = enabled,
            onChange = { onMinuteChange(it.toInt()) },
            onFinished = {},
            steps = 58,
        )
        WeekdayPicker(weeks = weeks, enabled = enabled, onChange = onWeeksChange)
    }
}

@Composable
private fun WeekdayPicker(weeks: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        weekdayLabels.forEachIndexed { index, label ->
            val selected = (weeks and (1 shl index)) != 0
            OutlinedButton(
                onClick = {
                    val next = if (selected) weeks and (1 shl index).inv() else weeks or (1 shl index)
                    AppLog.d(TAG, "timer weekday $index -> ${Integer.toBinaryString(next)}")
                    onChange(next)
                },
                enabled = enabled,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun weeksWithEnable(enabled: Boolean, weeks: Int): Int =
    (weeks and 0x7F) or (if (enabled) 0x80 else 0)

@Composable
private fun PermissionBanner(onRequest: () -> Unit, onSettings: () -> Unit) {
    AppLog.t(TAG, "PermissionBanner()")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Text(
            "Bluetooth permission is needed to find and control the strip.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequest) { Text("Grant") }
            OutlinedButton(onClick = onSettings) { Text("App settings") }
        }
    }
}

private fun hasAllPermissions(context: android.content.Context, permissions: Array<String>): Boolean {
    val granted = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    AppLog.v(TAG, "hasAllPermissions = $granted")
    return granted
}

private fun openAppSettings(context: android.content.Context) {
    AppLog.d(TAG, "opening app settings")
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun modeLabel(mode: Mode): String =
    mode.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

