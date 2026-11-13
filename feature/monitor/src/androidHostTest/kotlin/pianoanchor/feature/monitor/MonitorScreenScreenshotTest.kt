package pianoanchor.feature.monitor

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.MidiDevice
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the note monitor in light and dark. `./gradlew :feature:monitor:recordRoborazziAndroidHostTest`
 * rewrites them under `feature/monitor/screenshots/`; fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class MonitorScreenScreenshotTest {
    private val usbPiano = MidiDevice(id = "usb-1", name = "Digital Piano (USB)")
    private val blePiano = MidiDevice(id = "ble-1", name = "FP-30X")
    private val log =
        listOf(
            LogLine("E4 ↑ 64", 1_450),
            LogLine("E4 ↓ 88", 1_210),
            LogLine("CC64 127", 1_005),
            LogLine("D4 ↑ 64", 980),
            LogLine("D4 ↓ 92", 720),
            LogLine("C4 ↑ 64", 480),
            LogLine("C4 ↓ 100", 240),
        )

    private fun capture(
        name: String,
        state: MonitorState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/MonitorScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { MonitorScreen(state, MonitorActions()) }
            }
        }
    }

    @Test
    fun empty() = capture("empty", MonitorState())

    @Test
    fun devicesAndScanResults() =
        capture(
            "devices",
            MonitorState(devices = listOf(usbPiano), scanned = listOf(blePiano), scanning = false),
        )

    @Test
    fun connectedWithLog() =
        capture(
            "connected",
            MonitorState(
                devices = listOf(usbPiano, blePiano),
                selected = blePiano,
                connection = Connection.CONNECTED,
                log = log,
                recording = true,
                recordedCount = 7,
            ),
        )

    @Test
    fun reconnecting() =
        capture(
            "reconnecting",
            MonitorState(
                devices = listOf(usbPiano),
                selected = blePiano,
                connection = Connection.RECONNECTING,
                log = listOf(LogLine("", 3_000, gap = true)) + log,
                recordedCount = 7,
            ),
        )

    @Test
    fun permissionError() =
        capture(
            "permission_error",
            MonitorState(devices = listOf(usbPiano), error = MonitorError.BLUETOOTH_PERMISSION),
        )
}

/** Android API level the screenshots render with (the newest Robolectric supports). */
private const val SDK = 36
