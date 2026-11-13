package pianoanchor.platform.androidmidi

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.midi.MidiParser
import java.util.concurrent.ConcurrentHashMap

private const val STATUS_MIN = 0x80
private const val BYTE_MASK = 0xFF

/**
 * Driven adapter for both MIDI ports over Android's MIDI service: USB devices and Bluetooth devices the system has
 * already opened. Create it on Android with `AndroidMidiAdapter(context)`.
 * Contract: `MidiInputPortContractTest`, `MidiOutputPortContractTest`; details in `docs/protocols/android-midi.md`.
 */
public class AndroidMidiAdapter internal constructor(
    private val system: MidiSystem,
) : MidiInputPort,
    MidiOutputPort {
    private val listed: Flow<List<ListedDevice>> = system.devices.map(::identify)
    private val openInputs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val sendersLock = Mutex()
    private val senders = mutableMapOf<Int, SystemSender>() // by SystemDevice.systemId, guarded by sendersLock

    override val inputDevices: Flow<List<MidiDevice>> =
        listed.map { all -> all.filter { it.system.outputPorts > 0 }.map { it.device } }

    override val outputDevices: Flow<List<MidiDevice>> =
        listed.map { all -> all.filter { it.system.inputPorts > 0 }.map { it.device } }

    /** Also fails with [IllegalStateException] while another collector has [device] open. */
    override fun events(device: MidiDevice): Flow<MidiEvent> =
        flow {
            val target =
                listed.first().find(device) { it.outputPorts > 0 } ?: throw MidiDeviceUnavailableException(device.id)
            check(openInputs.add(device.id)) { "MIDI device '${device.id}' is already open" }
            try {
                val channel = Channel<MidiEvent>(Channel.UNLIMITED)
                val stamper = Stamper()
                val handle =
                    system.openInput(
                        target,
                        onBytes = { bytes, offset, count, timestamp ->
                            stamper.feed(bytes, offset, count, timestamp).forEach { channel.trySend(it) }
                        },
                        onClosed = { channel.close() },
                    ) ?: throw MidiDeviceUnavailableException(device.id)
                handle.use { for (event in channel) emit(event) }
            } finally {
                openInputs.remove(device.id)
            }
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
            if (target == null) throw MidiDeviceUnavailableException(device.id)
            val sender =
                senders[target.systemId]
                    ?: system.openOutput(target)?.also { senders[target.systemId] = it }
                    ?: throw MidiDeviceUnavailableException(device.id)
            sender.send(bytes, 0, bytes.size)
        }
    }

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

/**
 * Parses one open device's bytes and stamps them. Android calls back on binder threads, so feeding is locked.
 * Stamps never go backwards: `max(previous stamp, platform timestamp)`.
 */
private class Stamper {
    private val parser = MidiParser()
    private var lastStamp = Long.MIN_VALUE

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
