package pianoanchor.feature.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.MidiDevice
import pianoanchor.feature.monitor.resources.Res
import pianoanchor.feature.monitor.resources.monitor_action_dismiss
import pianoanchor.feature.monitor.resources.monitor_action_enable_bluetooth
import pianoanchor.feature.monitor.resources.monitor_action_open_settings
import pianoanchor.feature.monitor.resources.monitor_connect
import pianoanchor.feature.monitor.resources.monitor_connect_device
import pianoanchor.feature.monitor.resources.monitor_devices
import pianoanchor.feature.monitor.resources.monitor_error_bluetooth_off
import pianoanchor.feature.monitor.resources.monitor_error_bluetooth_permission
import pianoanchor.feature.monitor.resources.monitor_error_device_unavailable
import pianoanchor.feature.monitor.resources.monitor_error_recording_limit
import pianoanchor.feature.monitor.resources.monitor_error_storage
import pianoanchor.feature.monitor.resources.monitor_export
import pianoanchor.feature.monitor.resources.monitor_instrument_model
import pianoanchor.feature.monitor.resources.monitor_log
import pianoanchor.feature.monitor.resources.monitor_log_empty
import pianoanchor.feature.monitor.resources.monitor_log_gap
import pianoanchor.feature.monitor.resources.monitor_log_time
import pianoanchor.feature.monitor.resources.monitor_no_devices
import pianoanchor.feature.monitor.resources.monitor_practice
import pianoanchor.feature.monitor.resources.monitor_record_start
import pianoanchor.feature.monitor.resources.monitor_record_stop
import pianoanchor.feature.monitor.resources.monitor_recorded_count
import pianoanchor.feature.monitor.resources.monitor_scan
import pianoanchor.feature.monitor.resources.monitor_scanned
import pianoanchor.feature.monitor.resources.monitor_scanning
import pianoanchor.feature.monitor.resources.monitor_select_device
import pianoanchor.feature.monitor.resources.monitor_status_connected
import pianoanchor.feature.monitor.resources.monitor_status_none
import pianoanchor.feature.monitor.resources.monitor_status_reconnecting
import pianoanchor.feature.monitor.resources.monitor_title

/** The note monitor: pick a piano, watch live notes, record and export a fixture. Stateless: see [MonitorViewModel]. */
@Composable
public fun MonitorScreen(
    state: MonitorState,
    actions: MonitorActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(Res.string.monitor_title), style = MaterialTheme.typography.headlineMedium) }
            state.error?.let { error -> item { ErrorCard(error, actions) } }
            devices(state, actions)
            item { StatusText(state) }
            state.instrument?.let { instrument -> item { InstrumentText(instrument) } }
            item {
                // Wait-mode practice on the selected piano, while it is connected.
                val device = state.selected
                Button(
                    onClick = { device?.let(actions.onPractice) },
                    enabled = device != null && state.connection == Connection.CONNECTED,
                ) { Text(stringResource(Res.string.monitor_practice)) }
            }
            item { RecordingControls(state, actions) }
            log(state.log)
        }
    }
}

private fun LazyListScope.devices(
    state: MonitorState,
    actions: MonitorActions,
) {
    item { SectionTitle(Res.string.monitor_devices) }
    if (state.devices.isEmpty()) item { Text(stringResource(Res.string.monitor_no_devices)) }
    items(state.devices, key = { "listed-${it.id}" }) { device ->
        DeviceRow(device, selected = device.id == state.selected?.id, onSelect = { actions.onSelect(device) })
    }
    item {
        if (state.scanning) {
            Text(stringResource(Res.string.monitor_scanning), style = MaterialTheme.typography.bodyMedium)
        } else {
            OutlinedButton(onClick = actions.onScan) { Text(stringResource(Res.string.monitor_scan)) }
        }
    }
    if (state.scanned.isNotEmpty()) item { SectionTitle(Res.string.monitor_scanned) }
    items(state.scanned, key = { "scanned-${it.id}" }) { device ->
        ScannedRow(device, onConnect = { actions.onConnect(device) })
    }
}

private fun LazyListScope.log(log: List<LogLine>) {
    item { SectionTitle(Res.string.monitor_log) }
    if (log.isEmpty()) item { Text(stringResource(Res.string.monitor_log_empty)) }
    items(log) { LogRow(it) }
}

@Composable
private fun SectionTitle(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun DeviceRow(
    device: MidiDevice,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val description = stringResource(Res.string.monitor_select_device, device.name)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
                .semantics { contentDescription = description }
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(device.name, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ScannedRow(
    device: MidiDevice,
    onConnect: () -> Unit,
) {
    val description = stringResource(Res.string.monitor_connect_device, device.name)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(device.name, modifier = Modifier.weight(1f))
        TextButton(onClick = onConnect, modifier = Modifier.semantics { contentDescription = description }) {
            Text(stringResource(Res.string.monitor_connect))
        }
    }
}

@Composable
private fun StatusText(state: MonitorState) {
    val name = state.selected?.name
    val text =
        when {
            name == null || state.connection == Connection.NONE -> stringResource(Res.string.monitor_status_none)
            state.connection == Connection.CONNECTED -> stringResource(Res.string.monitor_status_connected, name)
            else -> stringResource(Res.string.monitor_status_reconnecting, name)
        }
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
}

/** The identified piano: its name, plus "model <family>/<model> · v<version>" when it answered. */
@Composable
private fun InstrumentText(instrument: Instrument) {
    Column {
        Text(instrument.name, style = MaterialTheme.typography.titleSmall)
        instrument.identity?.let { identity ->
            Text(
                stringResource(
                    Res.string.monitor_instrument_model,
                    identity.family.toString(),
                    identity.model.toString(),
                    identity.version.joinToString("."),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RecordingControls(
    state: MonitorState,
    actions: MonitorActions,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.recording) {
            Button(onClick = actions.onStopRecording) { Text(stringResource(Res.string.monitor_record_stop)) }
        } else {
            Button(onClick = actions.onStartRecording, enabled = state.connection == Connection.CONNECTED) {
                Text(stringResource(Res.string.monitor_record_start))
            }
        }
        OutlinedButton(onClick = actions.onExport, enabled = !state.recording && state.recordedCount > 0) {
            Text(stringResource(Res.string.monitor_export))
        }
    }
    Text(
        stringResource(Res.string.monitor_recorded_count, state.recordedCount.toString()),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LogRow(line: LogLine) {
    if (line.gap) {
        Text(
            stringResource(Res.string.monitor_log_gap),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.secondary,
        )
        return
    }
    Row {
        Text(
            stringResource(Res.string.monitor_log_time, line.millis.toString()),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(112.dp),
        )
        Text(line.text, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ErrorCard(
    error: MonitorError,
    actions: MonitorActions,
) {
    val (message, action) =
        when (error) {
            MonitorError.BLUETOOTH_PERMISSION -> {
                Res.string.monitor_error_bluetooth_permission to
                    (Res.string.monitor_action_open_settings to actions.onOpenSettings)
            }

            MonitorError.BLUETOOTH_OFF -> {
                Res.string.monitor_error_bluetooth_off to
                    (Res.string.monitor_action_enable_bluetooth to actions.onEnableBluetooth)
            }

            MonitorError.DEVICE_UNAVAILABLE -> {
                Res.string.monitor_error_device_unavailable to null
            }

            MonitorError.RECORDING_LIMIT -> {
                Res.string.monitor_error_recording_limit to null
            }

            MonitorError.STORAGE -> {
                Res.string.monitor_error_storage to null
            }
        }
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(message), modifier = Modifier.padding(16.dp))
        Row(modifier = Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            action?.let { (label, onClick) ->
                TextButton(onClick = onClick, colors = buttonColors) { Text(stringResource(label)) }
            }
            TextButton(onClick = actions.onDismissError, colors = buttonColors) {
                Text(stringResource(Res.string.monitor_action_dismiss))
            }
        }
    }
}
