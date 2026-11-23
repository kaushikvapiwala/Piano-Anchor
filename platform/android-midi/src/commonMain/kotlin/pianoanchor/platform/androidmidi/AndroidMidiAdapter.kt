package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.LinkResumed
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.midi.MidiParser
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val STATUS_MIN = 0x80
private const val BYTE_MASK = 0xFF

/**
 * Driven adapter for both MIDI ports and Bluetooth LE discovery over Android's MIDI service: USB devices, Bluetooth
 * devices the system has already opened and Bluetooth LE devices connected through [connect], which it reopens in
 * [scope] after a drop. Create it on Android with `AndroidMidiAdapter(context)`.
 * Contract: `MidiInputPortContractTest`, `MidiOutputPortContractTest`, `MidiDiscoveryPortContractTest`; details in
 * `docs/protocols/android-midi.md` and `docs/protocols/ble-midi.md`.
 */
public class AndroidMidiAdapter internal constructor(
    private val system: MidiSystem,
    scope: CoroutineScope,
) : MidiInputPort,
    MidiOutputPort,
    MidiDiscoveryPort {
    private val listed: Flow<List<ListedDevice>> = system.devices.map(::identify)
    private val discovered = ConcurrentHashMap<String, String>() // Bluetooth address by device id, from scans
    private val bluetooth = BluetoothConnections(system, listed, scope)
    private val dropGrace = DropGrace(system.devices, bluetooth.kept, scope)
    private val openInputs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val sendersLock = Mutex()
    private val senders = mutableMapOf<Int, SystemSender>() // by SystemDevice.systemId, guarded by sendersLock

    /** Also keeps a dropped Bluetooth device listed for `Tunables.BLE_DROP_GRACE_MS` while it is reopened. */
    override val inputDevices: Flow<List<MidiDevice>> =
        dropGrace.view
            .map { all -> all.filter { it.system.outputPorts > 0 }.map { it.device } }
            .distinctUntilChanged()

    override val outputDevices: Flow<List<MidiDevice>> =
        listed.map { all -> all.filter { it.system.inputPorts > 0 }.map { it.device } }

    /**
     * Also fails with [IllegalStateException] while another collector has [device] open. A kept Bluetooth device
     * that drops is waited for and reopened (see [reopened]); the flow then carries on after a `LinkResumed`.
     */
    override fun events(device: MidiDevice): Flow<MidiEvent> =
        flow {
            var target =
                listed.first().outputOf(device) ?: throw MidiDeviceUnavailableException(device.id)
            check(openInputs.add(device.id)) { "MIDI device '${device.id}' is already open" }
            try {
                val stamper = Stamper()
                var resumed = false
                while (true) {
                    val channel = Channel<MidiEvent>(Channel.UNLIMITED)
                    stamper.newLink()
                    val handle =
                        system.openInput(
                            target,
                            onBytes = { bytes, offset, count, timestamp ->
                                // A platform thread: a bad range fails this flow instead of crashing that thread.
                                try {
                                    stamper.feed(bytes, offset, count, timestamp).forEach { channel.trySend(it) }
                                } catch (e: IllegalArgumentException) {
                                    channel.close(e)
                                }
                            },
                            onClosed = { channel.close() },
                        ) ?: if (resumed) return@flow else throw MidiDeviceUnavailableException(device.id)
                    handle.use {
                        if (resumed) emit(LinkResumed(stamper.resumedAt(System.nanoTime())))
                        for (event in channel) emit(event)
                    }
                    if (device.id !in bluetooth.kept.value) return@flow
                    target = reopened(device, target) ?: return@flow
                    resumed = true
                }
            } finally {
                openInputs.remove(device.id)
            }
        }

    /** The system device that lists [device] again after [dead] closed, or null if not back within the grace. */
    private suspend fun reopened(
        device: MidiDevice,
        dead: SystemDevice,
    ): SystemDevice? {
        val probes = combine(listed, bluetooth.kept) { all, ids -> Probe(all.outputOf(device), device.id in ids) }
        // The grace starts when the system list drops the device, as the held entry in `inputDevices` does, so the
        // flow never ends while the device is still listed there.
        // A device that stays listed with its dead handle, or stops being kept, ends the flow too.
        val left = withTimeoutOrNull(Tunables.BLE_DROP_GRACE_MS) { probes.first { it.now != dead || !it.kept } }
        if (left == null || !left.kept) return null
        val notBack = setOf(null, dead)
        val back =
            withTimeoutOrNull(Tunables.BLE_DROP_GRACE_MS) {
                probes.first {
                    !it.kept ||
                        it.now !in notBack
                }
            }
        return back?.takeIf { it.kept }?.now
    }

    override suspend fun send(
        device: MidiDevice,
        bytes: ByteArray,
    ) {
        require(bytes.isNotEmpty()) { "MIDI message must not be empty" }
        require((bytes[0].toInt() and BYTE_MASK) >= STATUS_MIN) { "MIDI message must start with a status byte" }
        val current = listed.first()
        val target = current.find(device) { it.inputPorts > 0 }
        sendersLock.withLock {
            closeSendersOfGoneDevices(current)
            val (systemId, sender) = senderFor(device, target)
            try {
                sender.send(bytes, 0, bytes.size)
            } catch (e: IOException) {
                // The device went away before Android updated the list: drop the dead port so a retry reopens it.
                senders.remove(systemId)
                sender.close()
                throw MidiDeviceUnavailableException(device.id).also { it.initCause(e) }
            }
        }
    }

    /** The open sender for [target] (opened now if needed) with its system id; call with [sendersLock] held. */
    private suspend fun senderFor(
        device: MidiDevice,
        target: SystemDevice?,
    ): Pair<Int, SystemSender> {
        if (target == null) throw MidiDeviceUnavailableException(device.id)
        val sender =
            senders[target.systemId]
                ?: system.openOutput(target)?.also { senders[target.systemId] = it }
                ?: throw MidiDeviceUnavailableException(device.id)
        return target.systemId to sender
    }

    override fun scan(): Flow<List<MidiDevice>> =
        flow {
            requireBluetooth()
            val alreadyListed = listed.first().map { it.device.id }.toSet()
            val found = LinkedHashMap<String, MidiDevice>()
            emit(emptyList())
            withTimeoutOrNull(Tunables.BLE_SCAN_TIMEOUT_MS) {
                system.scanBluetooth().collect { advertisement ->
                    val id = bluetoothId(advertisement.address)
                    if (id in alreadyListed || id in found) return@collect
                    found[id] = MidiDevice(id, advertisement.name.present() ?: FALLBACK_DEVICE_NAME)
                    discovered[id] = advertisement.address
                    emit(found.values.toList())
                }
            }
        }

    override suspend fun connect(device: MidiDevice) {
        requireBluetooth()
        // A scanned device has its address on record; a `ble:` id from an earlier run carries it in the id.
        val address =
            discovered[device.id] ?: bluetoothAddressOf(device.id) ?: throw MidiDeviceUnavailableException(device.id)
        if (!bluetooth.connect(device.id, address)) throw MidiDeviceUnavailableException(device.id)
    }

    override suspend fun disconnect(device: MidiDevice): Unit = bluetooth.disconnect(device.id)

    private fun requireBluetooth() {
        system.bluetoothProblem()?.let { throw BluetoothUnavailableException(it) }
    }

    private fun List<ListedDevice>.outputOf(device: MidiDevice): SystemDevice? = find(device) { it.outputPorts > 0 }

    private fun List<ListedDevice>.find(
        device: MidiDevice,
        hasPort: (SystemDevice) -> Boolean,
    ): SystemDevice? = firstOrNull { it.device.id == device.id && hasPort(it.system) }?.system

    /** Senders stay open until their device leaves the list; checked on each send, the only time they are used. */
    private fun closeSendersOfGoneDevices(current: List<ListedDevice>) {
        val present = current.map { it.system.systemId }.toSet()
        val gone = senders.filterKeys { it !in present }
        gone.forEach { (systemId, sender) ->
            senders.remove(systemId)
            sender.close()
        }
    }
}

/** What [AndroidMidiAdapter] sees of one device while it waits for a reopen: its listing, and whether it is kept. */
private class Probe(
    val now: SystemDevice?,
    val kept: Boolean,
)

/**
 * Parses one open device's bytes and stamps them. Android calls back on binder threads, so feeding is locked.
 * Stamps never go backwards: `max(previous stamp, platform timestamp)`.
 */
private class Stamper {
    private val parser = MidiParser()
    private var lastStamp = Long.MIN_VALUE

    /** A new link: bytes of the dead one (running status, half a SysEx) must not shape what the new one sends. */
    @Synchronized
    fun newLink() = parser.reset()

    /** The stamp for a message the adapter makes up at [nowNanos]; never before the last one. */
    @Synchronized
    fun resumedAt(nowNanos: Long): Long {
        lastStamp = maxOf(lastStamp, nowNanos)
        return lastStamp
    }

    @Synchronized
    fun feed(
        bytes: ByteArray,
        offset: Int,
        count: Int,
        timestampNanos: Long,
    ): List<MidiEvent> {
        lastStamp = maxOf(lastStamp, timestampNanos)
        return parser.feed(bytes, lastStamp, offset, count)
    }
}
