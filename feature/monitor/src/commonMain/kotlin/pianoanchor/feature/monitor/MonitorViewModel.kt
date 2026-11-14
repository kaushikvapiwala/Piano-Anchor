package pianoanchor.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
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
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.Tunables
import kotlin.time.Clock
import kotlin.time.Instant

private const val SYSEX_FRAME_BYTES = 2

/**
 * The note monitor: shows live messages from the selected piano, reopens it after a drop and records a session as a
 * JSONL fixture (`fixtures/SCHEMA.md`). On every connect it asks the piano who it is (MIDI Identity Request,
 * `docs/protocols/midi-identity.md`) and stores it in [instruments]; if storing fails it still shows the piano, with
 * [MonitorError.STORAGE], and MIDI keeps flowing. [clock] times messages; [wallClock] gives the recording start time
 * and when a piano was seen.
 */
public class MonitorViewModel(
    private val input: MidiInputPort,
    output: MidiOutputPort,
    private val discovery: MidiDiscoveryPort,
    instruments: InstrumentRepositoryPort,
    private val clock: ClockPort,
    private val wallClock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MonitorState())
    public val state: StateFlow<MonitorState> = mutableState.asStateFlow()

    private var session: Job? = null
    private var released: MidiDevice? = null
    private var scan: Job? = null
    private var selectedAtNanos = 0L
    private val recorder = FixtureRecorder()
    private val identifier = InstrumentIdentifier(output, instruments, wallClock)

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
        released = null
        selectedAtNanos = clock.nowNanos()
        mutableState.update {
            it.copy(
                selected = device,
                connection = Connection.NONE,
                log = emptyList(),
                recording = false,
                error = null,
                instrument = null,
            )
        }
        session = viewModelScope.launch { monitor(device) }
    }

    /**
     * `false` closes the selected device, keeping it selected, so another screen (practice) can open it: an input
     * has one reader at a time, so this returns once the device is closed. `true` reopens a device closed this way,
     * with a fresh log; otherwise it does nothing.
     */
    public suspend fun setDeviceOpen(open: Boolean) {
        if (open) {
            released?.let(::select)
            return
        }
        val device = state.value.selected ?: return
        session?.cancelAndJoin()
        session = null
        released = device
        mutableState.update { it.copy(connection = Connection.NONE) }
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
            recordedAt = Instant.fromEpochSeconds(wallClock.now().epochSeconds).toString(),
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
                val gap = LogLine("", clock.nowNanos().millisAfter(selectedAtNanos), gap = true)
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

    /** Shows [device]'s messages until it drops; identifies it once its messages flow. */
    private suspend fun collect(device: MidiDevice): Unit =
        coroutineScope {
            var identifying: Job? = null
            try {
                input
                    .events(device)
                    .onStart {
                        mutableState.update { it.copy(connection = Connection.CONNECTED) }
                        identifying =
                            this@coroutineScope.launch {
                                val identified = identifier.identify(device)
                                mutableState.update {
                                    it.copy(
                                        instrument = identified.instrument,
                                        error = if (identified.saved) it.error else MonitorError.STORAGE,
                                    )
                                }
                            }
                    }.collect(::onEvent)
            } finally {
                identifying?.cancel() // dropped before the reply or the timeout: the reconnect asks again
            }
        }

    private fun onEvent(event: MidiEvent) {
        mutableState.update {
            it.withLine(
                LogLine(event.describe(), event.timestampNanos.millisAfter(selectedAtNanos)),
            )
        }
        identifier.offer(event)
        if (!state.value.recording) return
        if (recorder.add(event)) {
            mutableState.update { it.copy(recordedCount = recorder.count) }
        } else {
            mutableState.update { it.copy(recording = false, error = MonitorError.RECORDING_LIMIT) }
        }
    }
}

/** Adds [line] at the top of the log, keeping the newest `Tunables.MONITOR_LOG_LINES`. */
private fun MonitorState.withLine(line: LogLine) = copy(log = (listOf(line) + log).take(Tunables.MONITOR_LOG_LINES))

/** Milliseconds from [start] to this monotonic time, both in nanoseconds. */
private fun Long.millisAfter(start: Long) = (this - start) / NANOS_PER_MILLI

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
