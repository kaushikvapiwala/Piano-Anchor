package pianoanchor.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.SysEx
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables

private const val SYSEX_FRAME_BYTES = 2

/**
 * The note monitor: shows live messages from the selected piano, reopens it after a drop and records a session as a
 * JSONL fixture (`fixtures/SCHEMA.md`). [recordedAt] gives the wall-clock time (ISO-8601 UTC) when a recording starts.
 */
public class MonitorViewModel(
    private val input: MidiInputPort,
    private val discovery: MidiDiscoveryPort,
    private val clock: ClockPort,
    private val recordedAt: () -> String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MonitorState())
    public val state: StateFlow<MonitorState> = mutableState.asStateFlow()

    private var session: Job? = null
    private var scan: Job? = null
    private var selectedAtNanos = 0L
    private val recorder = FixtureRecorder()

    init {
        viewModelScope.launch {
            input.inputDevices.collect { devices ->
                mutableState.update { it.copy(devices = devices) }
            }
        }
    }

    /** Shows [device]'s messages from now on, with a fresh log; stops (but keeps) a running recording. */
    public fun select(device: MidiDevice) {
        session?.cancel()
        selectedAtNanos = clock.nowNanos()
        mutableState.update {
            it.copy(selected = device, connection = Connection.NONE, log = emptyList(), recording = false, error = null)
        }
        session = viewModelScope.launch { monitor(device) }
    }

    /** Scans for Bluetooth pianos into [MonitorState.scanned]; ignored while a scan runs. */
    public fun scanBluetooth() {
        if (scan?.isActive == true) return
        scan =
            viewModelScope.launch {
                mutableState.update {
                    it.copy(
                        scanning = true,
                        scanned = emptyList(),
                        error =
                            it.error.takeUnless { e ->
                                e in
                                    BLUETOOTH_ERRORS
                            },
                    )
                }
                try {
                    discovery.scan().collect { found -> mutableState.update { it.copy(scanned = found) } }
                } catch (e: BluetoothUnavailableException) {
                    mutableState.update { it.copy(error = e.toMonitorError()) }
                } finally {
                    mutableState.update { it.copy(scanning = false) }
                }
            }
    }

    /** Connects a [device] found by [scanBluetooth], then selects it. */
    public fun connect(device: MidiDevice) {
        viewModelScope.launch {
            val error =
                try {
                    discovery.connect(device)
                    null
                } catch (e: BluetoothUnavailableException) {
                    e.toMonitorError()
                } catch (_: MidiDeviceUnavailableException) {
                    MonitorError.DEVICE_UNAVAILABLE
                }
            if (error != null) return@launch mutableState.update { it.copy(error = error) }
            mutableState.update { it.copy(scanned = it.scanned - device) }
            select(device)
        }
    }

    /** Starts a new recording (discarding the previous one); times count from its first message. */
    public fun startRecording() {
        recorder.start(
            deviceName =
                state.value.selected
                    ?.name
                    .orEmpty(),
            recordedAt = recordedAt(),
        )
        mutableState.update { it.copy(recording = true, recordedCount = 0, error = null) }
    }

    public fun stopRecording() {
        mutableState.update { it.copy(recording = false) }
    }

    /** The recording as fixture JSONL, or null when nothing was recorded. */
    public fun exportJsonl(): String? = recorder.jsonl()

    public fun dismissError() {
        mutableState.update { it.copy(error = null) }
    }

    private suspend fun monitor(device: MidiDevice): Unit =
        coroutineScope {
            try {
                collect(device)
            } catch (_: MidiDeviceUnavailableException) {
                return@coroutineScope mutableState.update { it.unavailable() }
            }
            var giveUp: Job? = null
            while (true) {
                // events() completed: the device dropped. Mark the gap, then reopen it as soon as it is listed.
                giveUp?.cancel()
                val gap = LogLine("", millisSince(clock.nowNanos()), gap = true)
                mutableState.update { it.copy(connection = Connection.RECONNECTING).withLine(gap) }
                giveUp =
                    launch {
                        delay(Tunables.MONITOR_RECONNECT_GIVE_UP_MS)
                        // Not open by now (an open still pending counts once it fails): the adapter has stopped
                        // reconnecting. Once the device is open, this waits until the next drop cancels it.
                        state.first { it.connection == Connection.RECONNECTING }
                        mutableState.update { it.unavailable() }
                        this@coroutineScope.cancel()
                    }
                do {
                    input.inputDevices.first { device.isListedIn(it) }
                    val reopened =
                        try {
                            collect(device) // returns when it drops again
                            true
                        } catch (_: MidiDeviceUnavailableException) {
                            // Listed but not openable (a stale list): wait for the list to change, then try again.
                            mutableState.update { it.copy(connection = Connection.RECONNECTING) }
                            input.inputDevices.drop(1).first()
                            false
                        }
                } while (!reopened)
            }
        }

    private suspend fun collect(device: MidiDevice) {
        input
            .events(device)
            .onStart { mutableState.update { it.copy(connection = Connection.CONNECTED) } }
            .collect(::onEvent)
    }

    private fun onEvent(event: MidiEvent) {
        mutableState.update { it.withLine(LogLine(event.describe(), millisSince(event.timestampNanos))) }
        if (!state.value.recording) return
        if (recorder.add(event)) {
            mutableState.update { it.copy(recordedCount = recorder.count) }
        } else {
            mutableState.update { it.copy(recording = false, error = MonitorError.RECORDING_LIMIT) }
        }
    }

    private fun millisSince(nanos: Long) = (nanos - selectedAtNanos) / NANOS_PER_MILLI
}

/** Adds [line] at the top of the log, keeping the newest `Tunables.MONITOR_LOG_LINES`. */
private fun MonitorState.withLine(line: LogLine) = copy(log = (listOf(line) + log).take(Tunables.MONITOR_LOG_LINES))

private val BLUETOOTH_ERRORS = setOf(MonitorError.BLUETOOTH_PERMISSION, MonitorError.BLUETOOTH_OFF)

private fun MidiDevice.isListedIn(devices: List<MidiDevice>) = devices.any { it.id == id }

private fun MonitorState.unavailable() = copy(connection = Connection.NONE, error = MonitorError.DEVICE_UNAVAILABLE)

private fun BluetoothUnavailableException.toMonitorError() =
    when (reason) {
        BluetoothUnavailableException.Reason.PERMISSION_DENIED -> MonitorError.BLUETOOTH_PERMISSION
        BluetoothUnavailableException.Reason.BLUETOOTH_OFF -> MonitorError.BLUETOOTH_OFF
    }

/** The log text of [this], e.g. "C4 ↓ 100", "C4 ↑ 64", "CC64 127", "SysEx 6 bytes" (whole message length). */
internal fun MidiEvent.describe(): String =
    when (this) {
        is NoteOn -> "${note.name} ↓ $velocity"
        is NoteOff -> "${note.name} ↑ $velocity"
        is ControlChange -> "CC$controller $value"
        is SysEx -> "SysEx ${payload.size + SYSEX_FRAME_BYTES} bytes"
    }
