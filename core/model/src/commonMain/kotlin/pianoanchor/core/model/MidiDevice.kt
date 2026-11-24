package pianoanchor.core.model

/**
 * A MIDI instrument the platform can see. [id] is stable while the device stays connected and is unique among
 * listed devices; [name] is what the platform reports, for display only.
 * An adapter that implements both MIDI ports uses the same [id] for the same physical device.
 * [transport] is how it is connected, when the adapter knows.
 */
public data class MidiDevice(
    val id: String,
    val name: String,
    val transport: MidiTransport = MidiTransport.OTHER,
) {
    init {
        require(id.isNotBlank()) { "MIDI device id must not be blank" }
    }
}

/** Thrown when an operation names a [MidiDevice] that is not (or no longer) available. */
public class MidiDeviceUnavailableException(
    public val deviceId: String,
) : Exception("MIDI device '$deviceId' is not available")

/** How a [MidiDevice] is connected to the phone; the link delay differs by transport. */
public enum class MidiTransport { BLUETOOTH, USB, OTHER }
