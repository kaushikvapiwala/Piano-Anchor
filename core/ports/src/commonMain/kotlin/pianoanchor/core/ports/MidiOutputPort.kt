package pianoanchor.core.ports

import kotlinx.coroutines.flow.Flow
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException

/**
 * Driven port: MIDI messages sent to instruments (e.g. SysEx requests).
 * Contract: `pianoanchor.testing.MidiOutputPortContractTest`.
 */
public interface MidiOutputPort {
    /** Output devices currently available; same emission rules as [MidiInputPort.inputDevices]. */
    public val outputDevices: Flow<List<MidiDevice>>

    /**
     * Sends [bytes] to [device]: one or more complete MIDI messages, delivered in order. Completeness is the
     * caller's job; adapters only check the first byte and forward the bytes unchanged.
     * Suspends until the platform has accepted the bytes.
     * @throws IllegalArgumentException if [bytes] is empty or does not start with a status byte (0x80–0xFF).
     * @throws MidiDeviceUnavailableException if [device] is not listed.
     */
    public suspend fun send(
        device: MidiDevice,
        bytes: ByteArray,
    )
}
