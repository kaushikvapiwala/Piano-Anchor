package pianoanchor.testing

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.midi.MidiParser

/** MIDI "no velocity sensing" default velocity; test-support only. */
const val DEFAULT_VELOCITY = 64

private const val NOTE_OFF_STATUS = 0x80
private const val NOTE_ON_STATUS = 0x90
private const val STATUS_MIN = 0x80
private val CHANNEL_RANGE = 0..15
private val DATA_RANGE = 0..127

/**
 * In-memory piano for tests; implements both MIDI ports for one device.
 * Incoming bytes go through the real `MidiParser` and are stamped with [clock].
 */
class FakePiano(
    val clock: FakeClock = FakeClock(),
    val device: MidiDevice = MidiDevice(id = "fake-piano", name = "Fake Piano"),
) : MidiInputPort,
    MidiOutputPort {
    private val devices = MutableStateFlow(listOf(device))
    private val parser = MidiParser()
    private val collectors = mutableListOf<Channel<MidiEvent>>()
    private val sentCopies = mutableListOf<ByteArray>()

    /** Every byte array passed to [send], in order (copies). */
    val sent: List<ByteArray> get() = sentCopies.map { it.copyOf() }

    /** Optional scripted reply: called for each [send]; non-null result is played back as incoming bytes. */
    var replyTo: (ByteArray) -> ByteArray? = { null }

    override val inputDevices: Flow<List<MidiDevice>> get() = devices

    override val outputDevices: Flow<List<MidiDevice>> get() = devices

    override fun events(device: MidiDevice): Flow<MidiEvent> =
        flow {
            requireListed(device)
            val channel = Channel<MidiEvent>(Channel.UNLIMITED)
            collectors.add(channel)
            try {
                for (event in channel) emit(event)
            } finally {
                collectors.remove(channel)
            }
        }

    override suspend fun send(
        device: MidiDevice,
        bytes: ByteArray,
    ) {
        require(bytes.isNotEmpty()) { "MIDI message must not be empty" }
        require((bytes[0].toInt() and BYTE_MASK) >= STATUS_MIN) { "MIDI message must start with a status byte" }
        requireListed(device)
        val copy = bytes.copyOf()
        sentCopies.add(copy)
        replyTo(copy.copyOf())?.let(::play)
    }

    /** Plays raw bytes as if the piano sent them now. */
    fun play(bytes: ByteArray) {
        if (devices.value.isEmpty()) return
        parser.feed(bytes, clock.nowNanos()).forEach { event -> collectors.toList().forEach { it.trySend(event) } }
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

    /** Removes the device from both device lists and completes open `events` flows. */
    fun unplug() {
        devices.value = emptyList()
        collectors.toList().forEach { it.close() }
    }

    /** Lists the device again (starts plugged in). Parser state is reset. */
    fun plugIn() {
        parser.reset()
        devices.value = listOf(device)
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

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
