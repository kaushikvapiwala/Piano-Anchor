package pianoanchor.feature.monitor

import pianoanchor.core.model.Instrument
import pianoanchor.core.model.MidiDevice

/**
 * What the note monitor shows. [devices] are listed inputs (USB and connected Bluetooth); [scanned] are Bluetooth
 * pianos found by the last scan that are not listed yet; [log] holds the newest `Tunables.MONITOR_LOG_LINES` lines,
 * newest first; [recordedCount] counts the messages in the current recording. [instrument] is the stored record of
 * the selected piano once it has been identified (or did not answer) after connecting.
 */
public data class MonitorState(
    val devices: List<MidiDevice> = emptyList(),
    val scanned: List<MidiDevice> = emptyList(),
    val scanning: Boolean = false,
    val selected: MidiDevice? = null,
    val connection: Connection = Connection.NONE,
    val log: List<LogLine> = emptyList(),
    val recording: Boolean = false,
    val recordedCount: Int = 0,
    val error: MonitorError? = null,
    val instrument: Instrument? = null,
)

/** The link to the selected device. */
public enum class Connection {
    /** No device is open. */
    NONE,

    /** Messages from the selected device arrive. */
    CONNECTED,

    /**
     * The selected device dropped; the monitor reopens it when it is listed again. If it is not open
     * `Tunables.MONITOR_RECONNECT_GIVE_UP_MS` after the drop, the monitor gives up: [NONE] with
     * [MonitorError.DEVICE_UNAVAILABLE].
     */
    RECONNECTING,
}

/** A problem the screen explains, with an action where one helps. */
public enum class MonitorError {
    /** Bluetooth permission is missing: the screen offers the app settings. */
    BLUETOOTH_PERMISSION,

    /** Bluetooth is off: the screen offers to turn it on. */
    BLUETOOTH_OFF,

    /** The chosen device is not (or no longer) available. */
    DEVICE_UNAVAILABLE,

    /** The recording reached `Tunables.MONITOR_RECORDING_MAX_MS` and stopped. */
    RECORDING_LIMIT,

    /** Storing the piano failed: it is shown but forgotten when the app closes. */
    STORAGE,
}

/**
 * One log line: [text] such as "C4 ↓ 100" (empty for a [gap]), [millis] since the device was selected. A [gap] line
 * marks where the device dropped and came back.
 */
public data class LogLine(
    val text: String,
    val millis: Long,
    val gap: Boolean = false,
)
