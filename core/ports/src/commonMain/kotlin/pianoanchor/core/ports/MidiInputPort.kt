package pianoanchor.core.ports

import kotlinx.coroutines.flow.Flow
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent

/**
 * Driven port: MIDI messages arriving from instruments.
 * Contract: `pianoanchor.testing.MidiInputPortContractTest`.
 */
public interface MidiInputPort {
    /**
     * Input devices currently available. Emits the current list as soon as it is collected,
     * then a new list after every change. Never completes.
     */
    public val inputDevices: Flow<List<MidiDevice>>

    /**
     * Opens [device] while collected and emits its messages in arrival order, each stamped in the
     * `ClockPort` clock domain with non-decreasing timestamps. Messages that arrive while nobody collects
     * are dropped. Completes normally when the device disconnects; cancelling the collector closes the device.
     * Fails with [MidiDeviceUnavailableException] if [device] is not listed when collection starts.
     */
    public fun events(device: MidiDevice): Flow<MidiEvent>
}
