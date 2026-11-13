package pianoanchor.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.midi.MidiParser

/** MIDI "no velocity sensing" default velocity; test-support only. */
const val DEFAULT_VELOCITY = 64

private const val NOTE_OFF_STATUS = 0x80
private const val NOTE_ON_STATUS = 0x90
private const val STATUS_MIN = 0x80
private val CHANNEL_RANGE = 0..15
private val DATA_RANGE = 0..127

/**
 * In-memory piano for tests; implements both MIDI ports for one wired [device] (listed from the start) and
 * discovery of the devices in [advertised], which are listed once connected.
 * Incoming bytes go through the real `MidiParser` and are stamped with [clock].
 */
class FakePiano(
    val clock: FakeClock = FakeClock(),
    val device: MidiDevice = MidiDevice(id = "fake-piano", name = "Fake Piano"),
) : MidiInputPort,
    MidiOutputPort,
    MidiDiscoveryPort {
    private val devices = MutableStateFlow(listOf(device))
    private val wiredInputOnly = MutableStateFlow(false)
    private val advertisedDevices = MutableStateFlow(emptyList<MidiDevice>())
    private val parser = MidiParser()
    private val collectors = mutableListOf<Collector>()
    private val sentCopies = mutableListOf<ByteArray>()
    private val reconnects = mutableMapOf<String, Job>()
    private val scanned = mutableSetOf<String>()
    private val connected = mutableSetOf<String>()

    /** Devices a [scan] finds (repeats allowed, as with real advertisements) and [connect] can open. */
    var advertised: List<MidiDevice>
        get() = advertisedDevices.value
        set(value) {
            advertisedDevices.value = value
        }

    /** When set, [scan] and [connect] fail with [BluetoothUnavailableException] for this reason. */
    var bluetoothProblem: BluetoothUnavailableException.Reason? = null

    /** Every byte array passed to [send], in order (copies). */
    val sent: List<ByteArray> get() = sentCopies.map { it.copyOf() }

    /** When true, the wired device is listed as an input only: [outputDevices] leaves it out and [send] to it fails. */
    var inputOnly: Boolean
        get() = wiredInputOnly.value
        set(value) {
            wiredInputOnly.value = value
        }

    /** When true, [send] fails with [MidiDeviceUnavailableException] after checking its bytes (gone mid-send). */
    var failSends: Boolean = false

    /** Optional scripted reply: called for each [send]; non-null result is played back as incoming bytes. */
    var replyTo: (ByteArray) -> ByteArray? = { null }

    override val inputDevices: Flow<List<MidiDevice>> get() = devices

    override val outputDevices: Flow<List<MidiDevice>>
        get() = combine(devices, wiredInputOnly) { all, inputOnly -> if (inputOnly) all - device else all }

    override fun events(device: MidiDevice): Flow<MidiEvent> =
        flow {
            requireListed(device)
            val collector = Collector(device.id, Channel(Channel.UNLIMITED))
            collectors.add(collector)
            try {
                for (event in collector.channel) emit(event)
            } finally {
                collectors.remove(collector)
            }
        }

    override suspend fun send(
        device: MidiDevice,
        bytes: ByteArray,
    ) {
        require(bytes.isNotEmpty()) { "MIDI message must not be empty" }
        require((bytes[0].toInt() and BYTE_MASK) >= STATUS_MIN) { "MIDI message must start with a status byte" }
        requireListed(device)
        if (failSends || (inputOnly && device.id == this.device.id)) throw MidiDeviceUnavailableException(device.id)
        val copy = bytes.copyOf()
        sentCopies.add(copy)
        replyTo(copy.copyOf())?.let(::play)
    }

    override fun scan(): Flow<List<MidiDevice>> =
        flow {
            requireBluetooth()
            val alreadyListed = devices.value.map { it.id }.toSet()
            val found = LinkedHashMap<String, MidiDevice>()
            emit(emptyList())
            withTimeoutOrNull(Tunables.BLE_SCAN_TIMEOUT_MS) {
                advertisedDevices.collect { all ->
                    val new = all.filter { it.id !in alreadyListed && found.put(it.id, it) == null }
                    scanned += new.map { it.id }
                    if (new.isNotEmpty()) emit(found.values.toList())
                }
            }
        }

    /** Lists [device] if a [scan] found it; the fake connects at once. */
    override suspend fun connect(device: MidiDevice) {
        requireBluetooth()
        if (device.id !in scanned) throw MidiDeviceUnavailableException(device.id)
        reconnects.remove(device.id)?.cancel()
        connected += device.id
        list(device)
    }

    /** Cancels a pending reconnect of [device] and, if it was connected, removes it from both device lists. */
    override suspend fun disconnect(device: MidiDevice) {
        reconnects.remove(device.id)?.cancel()
        if (connected.remove(device.id)) unlist(device)
    }

    /**
     * The link of connected [device] drops: it leaves both device lists and its open `events` flows complete.
     * Like a real adapter, the fake lists it again in [scope] after `Tunables.BLE_RECONNECT_INITIAL_MS`.
     */
    fun drop(
        device: MidiDevice,
        scope: CoroutineScope,
    ) {
        unlist(device)
        reconnects[device.id] =
            scope.launch {
                delay(Tunables.BLE_RECONNECT_INITIAL_MS)
                reconnects.remove(device.id)
                list(device)
            }
    }

    /** Plays raw bytes as if the wired piano sent them now, to the open `events` flows of [device]. */
    fun play(bytes: ByteArray) {
        if (device !in devices.value) return
        parser.feed(bytes, clock.nowNanos()).forEach { event ->
            collectors.filter { it.deviceId == device.id }.forEach { it.channel.trySend(event) }
        }
    }

    /** Shortcut for a Note On (status 0x90 + [channel]). */
    fun press(
        note: MidiNote,
        velocity: Int = DEFAULT_VELOCITY,
        channel: Int = 0,
    ) = playChannelMessage(NOTE_ON_STATUS, note, velocity, channel)

    /** Shortcut for a Note Off (status 0x80 + [channel], release velocity [velocity]). */
    fun release(
        note: MidiNote,
        velocity: Int = DEFAULT_VELOCITY,
        channel: Int = 0,
    ) = playChannelMessage(NOTE_OFF_STATUS, note, velocity, channel)

    /** Removes the wired device from both device lists and completes its open `events` flows. */
    fun unplug() = unlist(device)

    /** Lists the wired device again (starts plugged in). Parser state is reset. */
    fun plugIn() {
        parser.reset()
        list(device)
    }

    private fun list(listed: MidiDevice) = devices.update { all -> if (listed in all) all else all + listed }

    private fun unlist(gone: MidiDevice) {
        devices.update { all -> all - gone }
        collectors.filter { it.deviceId == gone.id }.forEach { it.channel.close() }
    }

    private fun requireBluetooth() {
        bluetoothProblem?.let { throw BluetoothUnavailableException(it) }
    }

    private fun playChannelMessage(
        status: Int,
        note: MidiNote,
        velocity: Int,
        channel: Int,
    ) {
        require(channel in CHANNEL_RANGE) { "channel must be in $CHANNEL_RANGE, was $channel" }
        require(velocity in DATA_RANGE) { "velocity must be in $DATA_RANGE, was $velocity" }
        play(byteArrayOf((status + channel).toByte(), note.number.toByte(), velocity.toByte()))
    }

    private fun requireListed(requested: MidiDevice) {
        if (devices.value.none { it.id == requested.id }) throw MidiDeviceUnavailableException(requested.id)
    }

    private class Collector(
        val deviceId: String,
        val channel: Channel<MidiEvent>,
    )

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
