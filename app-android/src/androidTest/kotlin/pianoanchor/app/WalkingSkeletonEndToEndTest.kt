package pianoanchor.app

import android.Manifest
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiDeviceStatus
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pianoanchor.core.model.MidiNote
import pianoanchor.engine.content.Passages
import pianoanchor.feature.monitor.resources.monitor_instrument_model
import pianoanchor.feature.monitor.resources.monitor_practice
import pianoanchor.feature.monitor.resources.monitor_select_device
import pianoanchor.feature.monitor.resources.monitor_status_connected
import pianoanchor.feature.practice.resources.practice_last_key_correct
import pianoanchor.feature.practice.resources.results_caption
import pianoanchor.feature.practice.resources.results_headline_clean
import pianoanchor.feature.practice.resources.results_try_again
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import pianoanchor.feature.monitor.resources.Res as MonitorRes
import pianoanchor.feature.practice.resources.Res as PracticeRes

/** How long a step may take on a cold emulator: listing the device, opening it, a screen change. */
private const val UI_TIMEOUT_MS = 20_000L
private const val NOTE_ON = 0x90
private const val NOTE_OFF = 0x80
private const val VELOCITY = 80
private const val RELEASE_VELOCITY = 0
private const val APP_READ_PORT = 0

/**
 * How long the first note of a run waits for the screen before it is played again. A note sent in the instant
 * between the app opening the port and connecting its receiver is dropped, never delivered late.
 */
private const val FIRST_NOTE_RETRY_MS = 2_000L

/**
 * The Phase 1 walking skeleton end to end, through the real Android MIDI stack, Koin wiring and SQLite: the monitor
 * finds and identifies [VirtualPianoService], practice follows C-D-E sent to its control port, and Results counts the
 * runs. Drives the app only through its UI; the orchestrator's `clearPackageData` starts it with an empty database.
 */
@RunWith(AndroidJUnit4::class)
class WalkingSkeletonEndToEndTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var piano: TestPiano

    @Before
    fun openPiano() {
        val manager = InstrumentationRegistry.getInstrumentation().context.getSystemService(MidiManager::class.java)
        checkNotNull(manager) { "No MIDI service" }
        // The MIDI service registers the test APK's device shortly after the install.
        compose.waitUntil(UI_TIMEOUT_MS) { findTestPiano(manager) != null }
        piano = TestPiano(manager, checkNotNull(findTestPiano(manager)))
    }

    @After
    fun closePiano() {
        if (::piano.isInitialized) piano.close()
    }

    @Test
    fun playsCdeTwiceAndCountsRuns() {
        val select = text(MonitorRes.string.monitor_select_device, piano.name)
        compose.waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodesWithContentDescription(select).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(select).performClick()
        awaitText(text(MonitorRes.string.monitor_status_connected, piano.name))
        awaitText(
            text(
                MonitorRes.string.monitor_instrument_model,
                TEST_PIANO_FAMILY.toString(),
                TEST_PIANO_MODEL.toString(),
                TEST_PIANO_VERSION.joinToString("."),
            ),
        )

        playCde { compose.onNodeWithText(text(MonitorRes.string.monitor_practice)).performClick() }
        assertCleanResults(run = 1)

        playCde { compose.onNodeWithText(text(PracticeRes.string.results_try_again)).performClick() }
        assertCleanResults(run = 2)
    }

    /** [openPractice], waits until practice reads the piano (so no note is lost), then plays C-D-E. */
    private fun playCde(openPractice: () -> Unit) {
        val opens = piano.appReadOpens
        openPractice()
        compose.waitUntil(UI_TIMEOUT_MS) { piano.appReadOpens > opens }
        val notes = Passages.CDE.steps.flatMap { it.notes }
        playFirst(notes.first())
        notes.drop(1).dropLast(1).forEach { note ->
            piano.play(note)
            awaitText(correct(note))
        }
        piano.play(notes.last()) // the last note finishes the run: Results follows
    }

    /** Plays [note] until the screen shows it correct: the app may connect its receiver just after opening the port. */
    private fun playFirst(note: MidiNote) {
        val shown = correct(note)
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS
        while (true) {
            piano.play(note)
            try {
                return compose.waitUntil(FIRST_NOTE_RETRY_MS) { hasText(shown) }
            } catch (timeout: ComposeTimeoutException) {
                if (SystemClock.uptimeMillis() > deadline) throw timeout
            }
        }
    }

    private fun correct(note: MidiNote) = text(PracticeRes.string.practice_last_key_correct, note.name)

    private fun assertCleanResults(run: Int) {
        awaitText(text(PracticeRes.string.results_headline_clean))
        compose
            .onNodeWithText(text(PracticeRes.string.results_caption, Passages.CDE.title, run.toString()))
            .assertIsDisplayed()
    }

    private fun awaitText(text: String) = compose.waitUntil(UI_TIMEOUT_MS) { hasText(text) }

    private fun hasText(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun text(
        resource: StringResource,
        vararg args: Any,
    ): String = runBlocking { getString(resource, *args) }
}

/**
 * The test's side of [VirtualPianoService]: plays notes into its control port and counts how often the app opens
 * the output port it reads ([appReadOpens]), from the device status the MIDI service reports.
 */
private class TestPiano(
    private val manager: MidiManager,
    private val info: MidiDeviceInfo,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val readOpens = AtomicInteger()
    private var appReaders = 0
    private val statusCallback =
        object : MidiManager.DeviceCallback() {
            override fun onDeviceStatusChanged(status: MidiDeviceStatus) {
                if (status.deviceInfo.id != info.id) return
                val readers = status.getOutputPortOpenCount(APP_READ_PORT)
                if (readers > appReaders) readOpens.incrementAndGet()
                appReaders = readers
            }
        }
    private val device: MidiDevice
    private val control: MidiInputPort

    /** The name Android lists the piano under. */
    val name: String = TEST_PIANO_NAME

    /** How many times the app has opened the port it reads notes from since this was created. */
    val appReadOpens: Int get() = readOpens.get()

    init {
        @Suppress("DEPRECATION") // the executor overload needs API 33; minSdk is 31
        manager.registerDeviceCallback(statusCallback, handler)
        try {
            val opened = openDevice()
            control =
                opened.openInputPort(TEST_PIANO_CONTROL_PORT) ?: run {
                    opened.close()
                    error("Control port is busy")
                }
            device = opened
        } catch (e: IllegalStateException) {
            manager.unregisterDeviceCallback(statusCallback)
            throw e
        }
    }

    /** A key press and release of [note]. */
    fun play(note: MidiNote) {
        send(NOTE_ON, note, VELOCITY)
        send(NOTE_OFF, note, RELEASE_VELOCITY)
    }

    override fun close() {
        manager.unregisterDeviceCallback(statusCallback)
        control.close()
        device.close()
    }

    private fun send(
        status: Int,
        note: MidiNote,
        velocity: Int,
    ) {
        val message = byteArrayOf(status.toByte(), note.number.toByte(), velocity.toByte())
        control.send(message, 0, message.size)
    }

    private fun openDevice(): MidiDevice {
        val opened = CountDownLatch(1)
        var result: MidiDevice? = null
        manager.openDevice(info, {
            result = it
            opened.countDown()
        }, handler)
        check(opened.await(UI_TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "The test piano did not open" }
        return checkNotNull(result) { "The test piano could not be opened" }
    }
}

/** The [VirtualPianoService] device once the MIDI service lists it. */
private fun findTestPiano(manager: MidiManager): MidiDeviceInfo? {
    @Suppress("DEPRECATION") // getDevicesForTransport needs API 33; minSdk is 31
    val devices = manager.devices
    return devices.firstOrNull {
        it.type == MidiDeviceInfo.TYPE_VIRTUAL &&
            it.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == TEST_PIANO_NAME
    }
}
