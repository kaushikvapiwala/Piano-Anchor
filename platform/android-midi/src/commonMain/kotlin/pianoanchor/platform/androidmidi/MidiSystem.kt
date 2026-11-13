package pianoanchor.platform.androidmidi

import kotlinx.coroutines.flow.Flow
import pianoanchor.core.model.BluetoothUnavailableException

/**
 * Thin seam over `android.media.midi`, so [AndroidMidiAdapter]'s logic runs on the JVM. Port names are from the
 * device's view, as in Android: a device's *output* port carries what it plays, its *input* port what it receives.
 */
internal interface MidiSystem {
    /** The current devices, then a new list on every add or remove. Never completes; reading it is cheap. */
    val devices: Flow<List<SystemDevice>>

    /**
     * Opens output port 0 of [device]. [onBytes] gets `(bytes, offset, count, timestampNanos)` on any thread;
     * [onClosed] is called once if the device goes away while open. Closing the handle closes port and device.
     * Returns null if the device can't be opened (e.g. it went away).
     */
    suspend fun openInput(
        device: SystemDevice,
        onBytes: (ByteArray, Int, Int, Long) -> Unit,
        onClosed: () -> Unit,
    ): AutoCloseable?

    /**
     * Opens input port 0 of [device] for sending. Closing the handle closes port and device.
     * Returns null if the device can't be opened (e.g. it went away).
     */
    suspend fun openOutput(device: SystemDevice): SystemSender?

    /** Null when Bluetooth LE scanning and connecting are possible (permissions granted, adapter on), else why not. */
    fun bluetoothProblem(): BluetoothUnavailableException.Reason?

    /**
     * Scans for devices advertising the BLE MIDI service while collected and emits every advertisement, repeats
     * included. Never completes; fails with [BluetoothUnavailableException] if the platform refuses the scan.
     */
    fun scanBluetooth(): Flow<BluetoothAdvertisement>

    /**
     * Asks Android's BLE MIDI service to connect the device at [address]; once connected it appears in [devices]
     * (transport [Transport.BLUETOOTH], same address). Closing the handle disconnects it. Returns null if it can't be
     * opened. When the link drops, Android removes the device from [devices]; the handle should still be closed.
     */
    suspend fun openBluetooth(address: String): AutoCloseable?
}

/** One advertisement of a BLE MIDI device: its Bluetooth [address] and the [name] it advertises, if any. */
internal data class BluetoothAdvertisement(
    val address: String,
    val name: String?,
)

/** An open input port of a device (Android naming): bytes sent here go to the instrument. */
internal interface SystemSender : AutoCloseable {
    /**
     * Hands `bytes[offset until offset + count]` to the platform.
     * @throws java.io.IOException if the port is closed or the device has gone.
     */
    fun send(
        bytes: ByteArray,
        offset: Int,
        count: Int,
    )
}

/** What Android reports about one MIDI device (`MidiDeviceInfo`); [systemId] changes on every reconnect. */
internal data class SystemDevice(
    val systemId: Int,
    val transport: Transport,
    val inputPorts: Int,
    val outputPorts: Int,
    val manufacturer: String? = null,
    val product: String? = null,
    val serial: String? = null,
    val name: String? = null,
    val bluetoothAddress: String? = null,
)

/** How a device is connected (`MidiDeviceInfo.getType()`). */
internal enum class Transport { USB, BLUETOOTH, OTHER }
