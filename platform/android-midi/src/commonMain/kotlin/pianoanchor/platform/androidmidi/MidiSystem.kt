package pianoanchor.platform.androidmidi

import kotlinx.coroutines.flow.Flow

/**
 * Thin seam over `android.media.midi`, so [AndroidMidiAdapter]'s logic runs on the JVM. Port names are from the
 * device's view, as in Android: a device's *output* port carries what it plays, its *input* port what it receives.
 */
internal interface MidiSystem {
    /** The current devices, then a new list on every add or remove. Never completes. */
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
}

/** An open input port of a device (Android naming): bytes sent here go to the instrument. */
internal interface SystemSender : AutoCloseable {
    /** Hands `bytes[offset until offset + count]` to the platform. */
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
