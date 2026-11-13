package pianoanchor.feature.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.SysEx
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.fixture.FixtureCodec
import pianoanchor.engine.identity.IdentityCodec
import pianoanchor.engine.midi.MidiParser
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeInstrumentRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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

    private val instruments = FakeInstrumentRepository()
    private val wallClock = FakeWallClock(Instant.parse("2026-11-12T10:00:00Z"))

    private fun viewModel(input: MidiInputPort = piano) =
        MonitorViewModel(input, piano, piano, instruments, clock, wallClock)

    private fun TestScope.monitor(): MonitorViewModel = viewModel().also { runCurrent() }

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
    fun closingTheDeviceKeepsItSelectedAndReopeningResumesIt() =
        runTest(dispatcher) {
            val vm = selected()

            vm.setDeviceOpen(false)
            runCurrent()
            assertEquals(piano.device, vm.state.value.selected)
            assertEquals(Connection.NONE, vm.state.value.connection)
            play { piano.press(c4) }
            assertTrue(
                vm.state.value.log
                    .isEmpty(),
            ) // closed: another reader may open it now

            vm.setDeviceOpen(true)
            runCurrent()
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
            play { piano.press(d4) }
            assertEquals(
                listOf("D4 ↓ 64"),
                vm.state.value.log
                    .map { it.text },
            )
        }

    @Test
    fun closingWithoutASelectionAndReopeningWhatWasNotClosedDoNothing() =
        runTest(dispatcher) {
            val vm = monitor()

            vm.setDeviceOpen(false)
            vm.setDeviceOpen(true)
            runCurrent()
            assertNull(vm.state.value.selected)

            vm.select(piano.device)
            runCurrent()
            vm.setDeviceOpen(true)
            runCurrent()
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
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
            clock.advanceNanos(400 * ms)
            play { piano.unplug() }
            assertEquals(Connection.RECONNECTING, vm.state.value.connection)
            assertEquals(
                LogLine("", 400, gap = true),
                vm.state.value.log
                    .first(),
            )

            clock.advanceNanos(3_000 * ms)
            play { piano.plugIn() }
            assertEquals(Connection.CONNECTED, vm.state.value.connection)

            play { piano.press(e4, velocity = 70) }
            assertEquals(listOf(LogLine("E4 ↓ 70", 3_400), LogLine("", 400, gap = true)), vm.state.value.log)
        }

    @Test
    fun deviceBackBeforeTheMonitorNoticesResumes() =
        runTest(dispatcher) {
            val vm = selected()
            play {
                piano.unplug()
                piano.plugIn()
            }
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
            play { piano.press(c4, velocity = 10) }
            assertEquals(
                "C4 ↓ 10",
                vm.state.value.log
                    .first()
                    .text,
            )
        }

    @Test
    fun listedButUnopenableDeviceWaitsForTheNextListChange() =
        runTest(dispatcher) {
            val stale = StaleInput(piano)
            val vm = viewModel(stale)
            runCurrent()
            vm.select(piano.device)
            runCurrent()
            stale.failOpens = 1
            play { piano.unplug() }
            play { piano.plugIn() }
            assertEquals(Connection.RECONNECTING, vm.state.value.connection)
            assertEquals(
                1,
                vm.state.value.log
                    .count { it.gap },
            )

            play { piano.unplug() }
            play { piano.plugIn() }
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
            assertEquals(
                1,
                vm.state.value.log
                    .count { it.gap },
            )
        }

    private val giveUpMs = Tunables.MONITOR_RECONNECT_GIVE_UP_MS

    @Test
    fun droppedDeviceThatStaysGoneGivesUpWithDeviceUnavailable() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.unplug() }
            advanceTimeBy(giveUpMs - 1)
            runCurrent()
            assertEquals(Connection.RECONNECTING, vm.state.value.connection)
            assertNull(vm.state.value.error)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(Connection.NONE, vm.state.value.connection)
            assertEquals(MonitorError.DEVICE_UNAVAILABLE, vm.state.value.error)
            assertEquals(piano.device, vm.state.value.selected)
            assertTrue(
                vm.state.value.log
                    .single()
                    .gap,
            )
        }

    @Test
    fun afterGivingUpTheDeviceComingBackIsIgnoredUntilSelectedAgain() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.unplug() }
            advanceTimeBy(giveUpMs)
            runCurrent()
            play { piano.plugIn() }
            assertEquals(Connection.NONE, vm.state.value.connection)

            vm.select(piano.device)
            runCurrent()
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
            play { piano.press(c4, velocity = 20) }
            assertEquals(
                "C4 ↓ 20",
                vm.state.value.log
                    .single()
                    .text,
            )
        }

    @Test
    fun deviceBackBeforeGiveUpStaysConnectedPastTheWindow() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.unplug() }
            advanceTimeBy(giveUpMs - 1_000)
            play { piano.plugIn() }
            assertEquals(Connection.CONNECTED, vm.state.value.connection)

            advanceTimeBy(giveUpMs)
            runCurrent()
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
            assertNull(vm.state.value.error)
        }

    @Test
    fun eachDropStartsAFreshGiveUpWindow() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.unplug() }
            advanceTimeBy(giveUpMs - 1_000)
            play { piano.plugIn() }
            play { piano.unplug() }
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(Connection.RECONNECTING, vm.state.value.connection)

            advanceTimeBy(giveUpMs - 2_000)
            runCurrent()
            assertEquals(Connection.NONE, vm.state.value.connection)
            assertEquals(MonitorError.DEVICE_UNAVAILABLE, vm.state.value.error)
        }

    @Test
    fun staleListRetriesShareTheGiveUpWindow() =
        runTest(dispatcher) {
            val stale = StaleInput(piano)
            val vm = viewModel(stale)
            runCurrent()
            vm.select(piano.device)
            runCurrent()
            stale.failOpens = Int.MAX_VALUE
            play { piano.unplug() }
            repeat(3) {
                advanceTimeBy(giveUpMs / 4)
                play { piano.plugIn() } // listed but not openable
                play { piano.unplug() }
            }
            assertEquals(Connection.RECONNECTING, vm.state.value.connection)

            advanceTimeBy(giveUpMs / 4)
            runCurrent()
            assertEquals(Connection.NONE, vm.state.value.connection)
            assertEquals(MonitorError.DEVICE_UNAVAILABLE, vm.state.value.error)
        }

    @Test
    fun staleListThenADeviceThatOpensResumesAndNeverGivesUp() =
        runTest(dispatcher) {
            val stale = StaleInput(piano)
            val vm = viewModel(stale)
            runCurrent()
            vm.select(piano.device)
            runCurrent()
            stale.failOpens = 1
            play { piano.unplug() }
            play { piano.plugIn() } // listed but not openable
            play { piano.unplug() }
            play { piano.plugIn() }
            assertEquals(Connection.CONNECTED, vm.state.value.connection)

            advanceTimeBy(giveUpMs + 1)
            runCurrent()
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
            assertNull(vm.state.value.error)
        }

    /** Input whose next [failOpens] `events` calls fail as if the device listing were stale. */
    private class StaleInput(
        private val delegate: FakePiano,
    ) : MidiInputPort by delegate {
        var failOpens = 0

        override fun events(device: MidiDevice): Flow<MidiEvent> =
            if (failOpens > 0) {
                failOpens--
                flow { throw MidiDeviceUnavailableException(device.id) }
            } else {
                delegate.events(device)
            }
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
    fun scanKeepsAnUnrelatedError() =
        runTest(dispatcher) {
            val vm = monitor()
            vm.select(MidiDevice(id = "gone", name = "Gone"))
            runCurrent()
            vm.scanBluetooth()
            runCurrent()
            assertEquals(MonitorError.DEVICE_UNAVAILABLE, vm.state.value.error)
        }

    @Test
    fun scanClearsAnEarlierBluetoothError() =
        runTest(dispatcher) {
            piano.bluetoothProblem = BluetoothUnavailableException.Reason.BLUETOOTH_OFF
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            piano.bluetoothProblem = null
            vm.scanBluetooth()
            runCurrent()
            assertNull(vm.state.value.error)
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
    fun headerTimeIsWhenTheRecordingStarted() =
        runTest(dispatcher) {
            wallClock.advance(750.milliseconds) // the header keeps whole seconds
            val vm = selected()
            vm.startRecording()
            wallClock.advance(4.hours)
            play { piano.press(c4) }
            vm.stopRecording()
            assertEquals("2026-11-12T10:00:00Z", FixtureCodec.decode(vm.exportJsonl()!!).header.recordedAt)
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

    // Identity on connect (docs/protocols/midi-identity.md)

    private val identityReply = bytes(0xF0, 0x7E, 0x10, 0x06, 0x02, 0x41, 0x19, 0x00, 0x02, 0x00, 1, 0, 3, 0, 0xF7)
    private val identity = InstrumentIdentity(listOf(0x41), family = 0x19, model = 2, version = listOf(1, 0, 3, 0))
    private val connectedAt = Instant.parse("2026-11-12T10:00:00Z")

    private fun answersIdentity() {
        piano.replyTo = { request -> identityReply.takeIf { request.contentEquals(IdentityCodec.REQUEST) } }
    }

    private fun unidentified(at: Instant = connectedAt) = Instrument("fake-piano", "Fake Piano", null, at, at)

    @Test
    fun replyWithinTheTimeoutStoresTheIdentity() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()

            val expected = Instrument("fake-piano", "Fake Piano", identity, connectedAt, connectedAt)
            assertEquals(expected, vm.state.value.instrument)
            assertEquals(expected, instruments.get("fake-piano"))
            assertContentEquals(IdentityCodec.REQUEST, piano.sent.single())
        }

    @Test
    fun replyIsStillLogged() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()
            assertEquals(listOf(LogLine("SysEx 15 bytes", 0)), vm.state.value.log)
        }

    @Test
    fun noReplyStoresNoIdentityAfterTheTimeout() =
        runTest(dispatcher) {
            val vm = selected()
            advanceTimeBy(Tunables.IDENTITY_REPLY_TIMEOUT_MS - 1)
            runCurrent()
            assertNull(vm.state.value.instrument)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(unidentified(), vm.state.value.instrument)
            assertEquals(unidentified(), instruments.get("fake-piano"))
        }

    @Test
    fun otherSysExIsNotTakenAsTheReply() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.play(bytes(0xF0, 0x41, 0x10, 0xF7)) }
            play { piano.press(c4) }
            assertNull(vm.state.value.instrument)
            advanceTimeBy(Tunables.IDENTITY_REPLY_TIMEOUT_MS)
            runCurrent()
            assertEquals(unidentified(), vm.state.value.instrument)
        }

    @Test
    fun noOutputDeviceStoresNoIdentityWithoutSending() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = selected()
            assertEquals(unidentified(), vm.state.value.instrument)
            assertTrue(piano.sent.isEmpty())
            assertNull(vm.state.value.error)
        }

    @Test
    fun failedSendStoresNoIdentityAndShowsNoError() =
        runTest(dispatcher) {
            piano.failSends = true
            val vm = selected()
            assertEquals(unidentified(), vm.state.value.instrument)
            assertNull(vm.state.value.error)
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
        }

    @Test
    fun reconnectAsksAgainAndAdvancesLastSeen() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()
            wallClock.advance(30.seconds)
            play { piano.unplug() }
            play { piano.plugIn() }

            assertEquals(2, piano.sent.size)
            val later = connectedAt + 30.seconds
            assertEquals(
                Instrument("fake-piano", "Fake Piano", identity, connectedAt, later),
                vm.state.value.instrument,
            )
        }

    @Test
    fun dropBeforeTheReplyCancelsTheWait() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.unplug() }
            advanceTimeBy(Tunables.IDENTITY_REPLY_TIMEOUT_MS)
            runCurrent()
            assertNull(vm.state.value.instrument)
            assertNull(instruments.get("fake-piano"))
        }

    @Test
    fun selectingAgainClearsTheShownInstrument() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()
            piano.replyTo = { null }
            vm.select(piano.device)
            runCurrent()
            assertNull(vm.state.value.instrument)
        }

    @Test
    fun sysExIsLoggedWithItsFullLength() =
        runTest(dispatcher) {
            assertEquals("SysEx 2 bytes", SysEx(0, emptyList()).describe())
        }
}
