package pianoanchor.core.ports

import kotlinx.coroutines.flow.Flow
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException

/**
 * Driven port: finding and connecting MIDI devices that are not listed until connected (e.g. Bluetooth LE).
 * Contract: `pianoanchor.testing.MidiDiscoveryPortContractTest`.
 */
public interface MidiDiscoveryPort {
    /**
     * Scans while collected: emits the devices found so far (starting with an empty list), de-duplicated by id,
     * and completes after `Tunables.BLE_SCAN_TIMEOUT_MS`. Devices already listed when the scan starts are not
     * included. Fails with [BluetoothUnavailableException] if scanning isn't possible.
     */
    public fun scan(): Flow<List<MidiDevice>>

    /**
     * Connects [device], found by a [scan] of this adapter; returns once it is listed in
     * [MidiInputPort.inputDevices] and [MidiOutputPort.outputDevices] under the same id (within
     * `Tunables.BLE_LISTED_TIMEOUT_MS` of opening). While the app runs, the adapter reopens it after a drop
     * (backoff from `Tunables.BLE_RECONNECT_INITIAL_MS` to `BLE_RECONNECT_MAX_MS`, giving up after
     * `BLE_RECONNECT_GIVE_UP_MS`); during a drop the device is not listed. Connecting a device that is already
     * connected and listed does nothing.
     * @throws MidiDeviceUnavailableException if no scan of this adapter found [device], or it can't be opened.
     * @throws BluetoothUnavailableException if Bluetooth LE can't be used.
     */
    public suspend fun connect(device: MidiDevice)

    /** Stops reconnecting [device] and closes it if open. No-op if unknown. */
    public suspend fun disconnect(device: MidiDevice)
}
