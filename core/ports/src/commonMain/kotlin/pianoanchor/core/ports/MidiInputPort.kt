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
     * then a new list after every change. Never completes. A Bluetooth device that drops stays listed for up to
     * the adapter's drop grace (`Tunables.BLE_DROP_GRACE_MS`) while it is reopened.
     */
    public val inputDevices: Flow<List<MidiDevice>>

    /**
     * Opens [device] while collected and emits its messages in arrival order, each stamped in the
     * `ClockPort` clock domain with non-decreasing timestamps. Messages that arrive while nobody collects
     * are dropped. A dropped Bluetooth device that is reopened within the grace period keeps the flow open and
     * emits [pianoanchor.core.model.LinkResumed]; the flow completes normally when the device stays gone.
     * Cancelling the collector closes the device.
     * Fails with [MidiDeviceUnavailableException] if [device] is not listed when collection starts.
     */
    public fun events(device: MidiDevice): Flow<MidiEvent>
}
