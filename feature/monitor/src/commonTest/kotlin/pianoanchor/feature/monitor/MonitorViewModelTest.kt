package pianoanchor.feature.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.SysEx
import pianoanchor.engine.Tunables
import pianoanchor.engine.fixture.FixtureCodec
import pianoanchor.engine.midi.MidiParser
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakePiano
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(1_000_000_000)
    private val piano = FakePiano(clock)
    private val bluetoothPiano = MidiDevice(id = "ble-piano", name = "BLE Piano")
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val ms = 1_000_000L

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.monitor(): MonitorViewModel =
        MonitorViewModel(piano, piano, clock) { "2026-11-12T10:00:00Z" }.also { runCurrent() }

    private fun TestScope.selected(): MonitorViewModel =
        monitor().also { it.select(piano.device) }.also { runCurrent() }

    private fun TestScope.play(action: () -> Unit) {
        action()
        runCurrent()
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun listsTheAvailableDevices() =
        runTest(dispatcher) {
            assertEquals(listOf(piano.device), monitor().state.value.devices)
        }

    @Test
    fun selectingADeviceShowsLiveNotes() =
        runTest(dispatcher) {
            val vm = selected()
            assertEquals(piano.device, vm.state.value.selected)
            assertEquals(Connection.CONNECTED, vm.state.value.connection)

            clock.advanceNanos(5 * ms)
            play { piano.press(c4, velocity = 100) }
            clock.advanceNanos(120 * ms)
            play { piano.release(c4, velocity = 30) }
            play { piano.play(bytes(0xB0, 0x40, 0x7F)) }
            play { piano.play(bytes(0xF0, 0x41, 0x10, 0xF7)) }

            val expected =
                listOf(
                    LogLine("SysEx 4 bytes", 125),
                    LogLine("CC64 127", 125),
                    LogLine("C4 ↑ 30", 125),
                    LogLine("C4 ↓ 100", 5),
                )
            assertEquals(expected, vm.state.value.log)
        }

    @Test
    fun logKeepsOnlyTheNewestLines() =
        runTest(dispatcher) {
            val vm = selected()
            repeat(Tunables.MONITOR_LOG_LINES + 5) { i -> play { piano.press(MidiNote(i), velocity = 1 + i) } }

            val log = vm.state.value.log
            assertEquals(Tunables.MONITOR_LOG_LINES, log.size)
            assertEquals(
                "${MidiNote(Tunables.MONITOR_LOG_LINES + 4).name} ↓ ${Tunables.MONITOR_LOG_LINES + 5}",
                log.first().text,
            )
            assertEquals("${MidiNote(5).name} ↓ 6", log.last().text)
        }

    @Test
    fun selectingAnotherDeviceStartsAFreshLog() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.press(c4) }
            clock.advanceNanos(10 * ms)
            vm.select(piano.device)
            runCurrent()
            clock.advanceNanos(2 * ms)
            play { piano.press(d4, velocity = 50) }

            assertEquals(listOf(LogLine("D4 ↓ 50", 2)), vm.state.value.log)
        }

    @Test
    fun selectingAnUnavailableDeviceShowsAnError() =
        runTest(dispatcher) {
            val vm = monitor()
            vm.select(MidiDevice(id = "gone", name = "Gone"))
            runCurrent()

            assertEquals(MonitorError.DEVICE_UNAVAILABLE, vm.state.value.error)
            assertEquals(Connection.NONE, vm.state.value.connection)
        }

    @Test
    fun droppedDeviceShowsReconnectingThenResumes() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.unplug() }
            assertEquals(Connection.RECONNECTING, vm.state.value.connection)

            clock.advanceNanos(3_000 * ms)
            play { piano.plugIn() }
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
            assertEquals(
                LogLine("", 3_000, gap = true),
                vm.state.value.log
                    .first(),
            )

            play { piano.press(e4, velocity = 70) }
            assertEquals(
                LogLine("E4 ↓ 70", 3_000),
                vm.state.value.log
                    .first(),
            )
        }

    @Test
    fun recordingSurvivesAReconnect() =
        runTest(dispatcher) {
            val vm = selected()
            vm.startRecording()
            play { piano.press(c4) }
            play { piano.unplug() }
            clock.advanceNanos(2_000 * ms)
            play { piano.plugIn() }
            play { piano.press(d4) }
            vm.stopRecording()

            assertTrue(vm.state.value.recordedCount == 2)
            val messages = FixtureCodec.decode(vm.exportJsonl()!!).messages
            assertEquals(listOf(0L, 2_000 * ms), messages.map { it.t })
        }

    @Test
    fun exportProducesAFixtureThatDecodesToTheRecordedEvents() =
        runTest(dispatcher) {
            val vm = selected()
            vm.startRecording()
            assertTrue(vm.state.value.recording)
            for (note in listOf(c4, d4, e4)) {
                play { piano.press(note, velocity = 80) }
                clock.advanceNanos(200 * ms)
                play { piano.release(note, velocity = 40) }
                clock.advanceNanos(100 * ms)
            }
            play { piano.play(bytes(0x90, 0x3C, 0x00)) } // velocity-0 Note On arrives as a Note Off
            vm.stopRecording()
            assertFalse(vm.state.value.recording)
            assertEquals(7, vm.state.value.recordedCount)

            val fixture = FixtureCodec.decode(vm.exportJsonl()!!)
            assertEquals("Fake Piano", fixture.header.device)
            assertEquals("2026-11-12T10:00:00Z", fixture.header.recordedAt)
            val replayed = fixture.messages.flatMap { MidiParser().feed(it.bytes, it.t) }
            val expected =
                listOf(c4, d4, e4).flatMapIndexed { i, note ->
                    listOf(NoteOn(i * 300 * ms, 0, note, 80), NoteOff((i * 300 + 200) * ms, 0, note, 40))
                } + NoteOff(900 * ms, 0, c4, 64)
            assertEquals(expected, replayed)
        }

    @Test
    fun startingAgainDiscardsThePreviousRecording() =
        runTest(dispatcher) {
            val vm = selected()
            vm.startRecording()
            play { piano.press(c4) }
            vm.stopRecording()
            play { piano.press(d4) }
            clock.advanceNanos(50 * ms)
            vm.startRecording()
            play { piano.play(bytes(0xB0, 0x40, 0x00)) }

            val fixture = FixtureCodec.decode(vm.exportJsonl()!!)
            assertEquals(1, vm.state.value.recordedCount)
            assertEquals(
                listOf(ControlChange(0, 0, 64, 0)),
                fixture.messages.flatMap { MidiParser().feed(it.bytes, it.t) },
            )
        }

    @Test
    fun recordingStopsAtTheLimit() =
        runTest(dispatcher) {
            val vm = selected()
            vm.startRecording()
            play { piano.press(c4) }
            clock.advanceNanos(Tunables.MONITOR_RECORDING_MAX_MS * ms)
            play { piano.release(c4) }
            assertTrue(vm.state.value.recording)

            clock.advanceNanos(1)
            play { piano.press(d4) }
            assertFalse(vm.state.value.recording)
            assertEquals(MonitorError.RECORDING_LIMIT, vm.state.value.error)
            assertEquals(2, vm.state.value.recordedCount)

            vm.dismissError()
            assertNull(vm.state.value.error)
        }

    @Test
    fun selectingADeviceStopsTheRecordingButKeepsIt() =
        runTest(dispatcher) {
            val vm = selected()
            vm.startRecording()
            play { piano.press(c4) }
            vm.select(piano.device)
            runCurrent()
            play { piano.press(d4) }

            assertFalse(vm.state.value.recording)
            assertEquals(1, FixtureCodec.decode(vm.exportJsonl()!!).messages.size)
        }

    @Test
    fun scanListsAdvertisedDevices() =
        runTest(dispatcher) {
            piano.advertised = listOf(bluetoothPiano, piano.device)
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            assertTrue(vm.state.value.scanning)
            assertEquals(listOf(bluetoothPiano), vm.state.value.scanned)

            vm.scanBluetooth() // ignored while a scan runs
            advanceTimeBy(Tunables.BLE_SCAN_TIMEOUT_MS + 1)
            assertFalse(vm.state.value.scanning)
            assertEquals(listOf(bluetoothPiano), vm.state.value.scanned)
        }

    @Test
    fun connectingAScannedDeviceSelectsIt() =
        runTest(dispatcher) {
            piano.advertised = listOf(bluetoothPiano)
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            vm.connect(bluetoothPiano)
            runCurrent()

            assertEquals(bluetoothPiano, vm.state.value.selected)
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
            assertEquals(listOf(piano.device, bluetoothPiano), vm.state.value.devices)
            assertEquals(emptyList(), vm.state.value.scanned)
        }

    @Test
    fun connectingAnUnknownDeviceShowsAnError() =
        runTest(dispatcher) {
            val vm = monitor()
            vm.connect(bluetoothPiano)
            runCurrent()

            assertEquals(MonitorError.DEVICE_UNAVAILABLE, vm.state.value.error)
            assertNull(vm.state.value.selected)
        }

    @Test
    fun scanWithoutPermissionShowsPermissionError() =
        runTest(dispatcher) {
            piano.bluetoothProblem = BluetoothUnavailableException.Reason.PERMISSION_DENIED
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()

            assertEquals(MonitorError.BLUETOOTH_PERMISSION, vm.state.value.error)
            assertFalse(vm.state.value.scanning)
        }

    @Test
    fun scanWithBluetoothOffShowsBluetoothOffError() =
        runTest(dispatcher) {
            piano.bluetoothProblem = BluetoothUnavailableException.Reason.BLUETOOTH_OFF
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()

            assertEquals(MonitorError.BLUETOOTH_OFF, vm.state.value.error)
        }

    @Test
    fun connectWithBluetoothOffShowsBluetoothOffError() =
        runTest(dispatcher) {
            piano.advertised = listOf(bluetoothPiano)
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            piano.bluetoothProblem = BluetoothUnavailableException.Reason.BLUETOOTH_OFF
            vm.connect(bluetoothPiano)
            runCurrent()

            assertEquals(MonitorError.BLUETOOTH_OFF, vm.state.value.error)
        }

    @Test
    fun exportWithNothingRecordedIsNull() =
        runTest(dispatcher) {
            val vm = selected()
            assertNull(vm.exportJsonl())
            vm.startRecording()
            vm.stopRecording()
            assertNull(vm.exportJsonl())
        }

    @Test
    fun sysExIsLoggedWithItsFullLength() =
        runTest(dispatcher) {
            assertEquals("SysEx 2 bytes", SysEx(0, emptyList()).describe())
        }
}
