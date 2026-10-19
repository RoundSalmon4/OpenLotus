package com.openlotus.app.ui

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openlotus.app.ble.BleDevice
import com.openlotus.app.ble.BleManager
import com.openlotus.protocol.Commands
import com.openlotus.protocol.LightMode
import com.openlotus.protocol.Mode
import java.util.Calendar

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
    val context = LocalContext.current
    val ble = remember { BleManager(context) }
    val scope = rememberCoroutineScope()
    val player = remember { SequencePlayer(ble) }
    var tab by rememberSaveable { mutableStateOf(0) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) { launcher.launch(permissions) }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            ble.stopScan()
            ble.disconnect()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Devices") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Control") })
        }
        when (tab) {
            0 -> DeviceScreen(ble)
            else -> ControlScreen(ble, player, scope)
        }
    }
}

@Composable
private fun DeviceScreen(ble: BleManager) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Status: ${ble.status}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ble.startScan() }) { Text("Scan") }
            OutlinedButton(onClick = { ble.stopScan() }) { Text("Stop") }
            OutlinedButton(
                onClick = { ble.disconnect() },
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
                        ble.connect(device)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice, connected: Boolean, onClick: () -> Unit) {
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

@Composable
private fun ControlScreen(ble: BleManager, player: SequencePlayer, scope: kotlinx.coroutines.CoroutineScope) {
    val connected = ble.connectedDevice != null
    var brightness by rememberSaveable { mutableStateOf(60) }
    var speed by rememberSaveable { mutableStateOf(50) }
    var red by rememberSaveable { mutableStateOf(255) }
    var green by rememberSaveable { mutableStateOf(80) }
    var blue by rememberSaveable { mutableStateOf(0) }
    var sequence by remember { mutableStateListOf<Int>() }
    var dwell by rememberSaveable { mutableStateOf(800) }

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { ble.send(Commands.power(true)) },
                enabled = connected,
            ) { Text("On") }
            OutlinedButton(
                onClick = { ble.send(Commands.power(false)) },
                enabled = connected,
            ) { Text("Off") }
        }

        LabeledSlider(
            label = "Brightness $brightness",
            value = brightness.toFloat(),
            range = 0f..100f,
            enabled = connected,
            onChange = { brightness = it.toInt() },
            onFinished = {
                ble.send(Commands.brightness(brightness, LightMode.ALL))
            },
        )

        LabeledSlider(
            label = "Effect speed $speed",
            value = speed.toFloat(),
            range = 0f..100f,
            enabled = connected,
            onChange = { speed = it.toInt() },
            onFinished = { ble.send(Commands.speed(speed)) },
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
        )
        LabeledSlider("Red $red", red.toFloat(), 0f..255f, connected, { red = it.toInt() }, {
            ble.send(Commands.color(red, green, blue))
        })
        LabeledSlider("Green $green", green.toFloat(), 0f..255f, connected, { green = it.toInt() }, {
            ble.send(Commands.color(red, green, blue))
        })
        LabeledSlider("Blue $blue", blue.toFloat(), 0f..255f, connected, { blue = it.toInt() }, {
            ble.send(Commands.color(red, green, blue))
        })

        HorizontalDivider()
        Text("Modes", style = MaterialTheme.typography.titleSmall)
        Mode.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { mode ->
                    OutlinedButton(
                        onClick = { ble.send(Commands.mode(mode)) },
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
            "Pick a few presets and the app alternates them on a timer.",
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
                        .clickable { sequence.add(argb) },
                )
            }
        }
        Text(
            sequence.mapIndexed { index, argb -> "$index:${hex(argb)}" }.joinToString("  ").ifEmpty { "No colors yet" },
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (sequence.isNotEmpty()) sequence.removeAt(sequence.lastIndex) }) {
                Text("Remove last")
            }
            OutlinedButton(onClick = { sequence.clear(); player.stop() }) {
                Text("Clear")
            }
        }
        LabeledSlider(
            label = "Hold each color ${dwell}ms",
            value = dwell.toFloat(),
            range = 100f..3000f,
            enabled = connected,
            onChange = { dwell = it.toInt() },
            onFinished = {
                if (player.playing) {
                    player.start(scope, sequence.toList(), dwell.toLong())
                }
            },
        )
        Button(
            onClick = { player.toggle(scope, sequence.toList(), dwell.toLong()) },
            enabled = connected && sequence.isNotEmpty(),
        ) {
            Text(if (player.playing) "Stop sequence" else "Start sequence")
        }

        HorizontalDivider()
        OutlinedButton(
            onClick = {
                val now = Calendar.getInstance()
                ble.send(
                    Commands.systemTime(
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        now.get(Calendar.SECOND),
                        now.get(Calendar.DAY_OF_WEEK),
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
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            enabled = enabled,
        )
    }
}

private fun modeLabel(mode: Mode): String =
    mode.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

