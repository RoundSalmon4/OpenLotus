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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "ui"

private val presets = listOf(
    "Red" to AndroidColor.rgb(255, 0, 0),
    "Green" to AndroidColor.rgb(0, 255, 0),
    "Blue" to AndroidColor.rgb(0, 0, 255),
    "Cyan" to AndroidColor.rgb(0, 255, 255),
    "Yellow" to AndroidColor.rgb(255, 255, 0),
    "Purple" to AndroidColor.rgb(160, 0, 255),
    "White" to AndroidColor.rgb(255, 255, 255),
    "Warm" to AndroidColor.rgb(255, 160, 60),
)

@Composable
fun OpenLotusApp() {
    AppLog.t(TAG, "OpenLotusApp()")
    val context = LocalContext.current
    val ble = remember { BleManager(context) }
    val scope = rememberCoroutineScope()
    val store = remember { SettingsStore(context) }
    val player = remember { SequencePlayer { frame -> ble.send(frame) } }
    var tab by rememberSaveable { mutableStateOf(0) }

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
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { AppLog.d(TAG, "tab devices"); tab = 0 },
                text = { Text("Devices") },
            )
            Tab(
                selected = tab == 1,
                onClick = { AppLog.d(TAG, "tab control"); tab = 1 },
                text = { Text("Control") },
            )
            Tab(
                selected = tab == 2,
                onClick = { AppLog.d(TAG, "tab timer"); tab = 2 },
                text = { Text("Timer") },
            )
            Tab(
                selected = tab == 3,
                onClick = { AppLog.d(TAG, "tab log"); tab = 3 },
                text = { Text("Log") },
            )
        }
        when (tab) {
            0 -> DeviceScreen(ble)
            1 -> ControlScreen(ble, player, scope, store)
            2 -> TimerScreen(ble, scope, store)
            else -> LogScreen()
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


private fun sendCal(ble: BleManager, r:Int,g:Int,b:Int){ val (cr,cg,cb)=ColorCal.frame(r,g,b); ble.send(Commands.color(cr,cg,cb)) }
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

    LaunchedEffect(Unit) {
        if (seeded) return@LaunchedEffect
        val loaded = store.settingsFlow.first()
        AppLog.d(TAG, "seeding controls from saved settings")
        ColorCal.set(Calibration(loaded.calRedGain, loaded.calGreenGain, loaded.calBlueGain, loaded.calGamma))
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
            ColorWheelDialog(
                initialRed = red,
                initialGreen = green,
                initialBlue = blue,
                onDismiss = { showWheel = false },
                onConfirm = { r, g, b ->
                    AppLog.d(TAG, "wheel color $r,$g,$b")
                    red = r
                    green = g
                    blue = b
                    player.stop()
                    sendCal(ble,r,g,b)
                    scope.launch { store.setColor(r,g,b) }
                    showWheel = false
                },
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (name, argb) ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable {
                            AppLog.d(TAG, "sequence add $name")
                            sequence.add(SequenceStep(argb, defaultDwell.toLong()))
                            selected = sequence.lastIndex
                            persistSequence()
                        },
                )
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sequence.forEachIndexed { index, step ->
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
private fun ColorWheelDialog(
    initialRed: Int,
    initialGreen: Int,
    initialBlue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
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
                    onChange = { value = it },
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

