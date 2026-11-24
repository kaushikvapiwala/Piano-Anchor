package pianoanchor.feature.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.PLACEMENT_ATTEMPT_ID
import pianoanchor.engine.midi.MidiEncoder
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val C4 = MidiNote(60)
private val D4 = MidiNote(62)
private val E4 = MidiNote(64)
private val F4 = MidiNote(65)
private val G4 = MidiNote(67)
private const val SKIP_AFTER = 3

/** An input that counts the flows collected from it right now. */
private class CountingInput(
    private val delegate: MidiInputPort,
) : MidiInputPort by delegate {
    var open = 0
        private set

    /** The most flows that were collected at the same time. */
    var maxOpen = 0
        private set

    override fun events(device: MidiDevice): Flow<MidiEvent> =
        delegate
            .events(device)
            .onStart {
                open++
                maxOpen = maxOf(maxOpen, open)
            }.onCompletion { open-- }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val input = CountingInput(piano)
    private val learnerRepository = FakeLearnerRepository()
    private val settings = FakeSettingsRepository()
    private val clock = FakeWallClock(Instant.parse("2026-11-18T10:00:00Z"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): OnboardingViewModel =
        OnboardingViewModel(
            input,
            piano,
            piano,
            LearnerModel(Curriculum.graph, learnerRepository),
            settings,
            clock,
            TimeZone.UTC,
        ).also { runCurrent() }

    private fun TestScope.advanceToMs(ms: Long) {
        advanceTimeBy(ms - testScheduler.currentTime)
        runCurrent()
    }

    private fun TestScope.play(vararg notes: MidiNote) {
        notes.forEach {
            piano.press(it)
            runCurrent()
        }
    }

    /** Presses a key to connect, then waits out the playback so the echo listens. */
    private fun TestScope.connectAndListen(): OnboardingViewModel =
        viewModel().also {
            val start = testScheduler.currentTime
            play(C4)
            advanceToMs(start + PLAYBACK_MS)
        }

    private fun TestScope.passEcho(vm: OnboardingViewModel) {
        play(C4, E4, G4)
        assertEquals(EchoPhase.PASSED, vm.state.value.echo)
    }

    private fun TestScope.missThreeTimes() {
        repeat(SKIP_AFTER) { play(D4, D4) }
    }

    private fun TestScope.toStartPoint(vm: OnboardingViewModel) {
        passEcho(vm)
        vm.continueToStartPoint()
        runCurrent()
    }

    private fun noteBytes(
        note: MidiNote,
        on: Boolean,
    ): ByteArray =
        MidiEncoder.encode(
            if (on) NoteOn(0, 0, note, Tunables.PLAYBACK_VELOCITY) else NoteOff(0, 0, note, 0),
        )

    @Test
    fun `first note connects that piano`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertNull(vm.state.value.device)
            assertEquals(OnboardingStep.CONNECT, vm.state.value.step)

            play(F4)

            assertEquals(piano.device, vm.state.value.device)
            assertEquals(OnboardingStep.ECHO, vm.state.value.step)
        }

    @Test
    fun `a listed piano shows as connected while waiting for its first note`() =
        runTest(dispatcher) {
            val vm = viewModel()

            assertEquals(piano.device, vm.state.value.listed)
            assertNull(vm.state.value.device)

            piano.unplug()
            runCurrent()

            // The input port rides out brief Bluetooth drops itself, so the line follows the list without a timer.
            assertNull(vm.state.value.listed)
            piano.plugIn()
            runCurrent()
            assertEquals(piano.device, vm.state.value.listed)
        }

    @Test
    fun `a resumed link while waiting for the first note changes nothing`() =
        runTest(dispatcher) {
            val vm = viewModel()

            piano.dropLink()
            runCurrent()

            assertEquals(piano.device, vm.state.value.listed)
            assertNull(vm.state.value.device)
        }

    @Test
    fun `the piano list is offered after the hint delay without a note`() =
        runTest(dispatcher) {
            val vm = viewModel()

            advanceToMs(Tunables.ONBOARDING_CONNECT_HINT_MS - 1)
            assertFalse(vm.state.value.pianoListHint)
            advanceToMs(Tunables.ONBOARDING_CONNECT_HINT_MS)

            assertTrue(vm.state.value.pianoListHint)
            assertEquals(1, input.open)
            vm.close()
            assertEquals(0, input.open)
        }

    @Test
    fun `sends C E G with the gaps`() =
        runTest(dispatcher) {
            viewModel()
            play(C4)
            val expected =
                listOf(
                    0L to noteBytes(C4, on = true),
                    400L to noteBytes(C4, on = false),
                    500L to noteBytes(E4, on = true),
                    900L to noteBytes(E4, on = false),
                    1000L to noteBytes(G4, on = true),
                    1400L to noteBytes(G4, on = false),
                )

            expected.forEachIndexed { i, (at, bytes) ->
                advanceToMs(at)
                assertEquals(i + 1, piano.sent.size, "messages at $at ms")
                assertContentEquals(bytes, piano.sent.last())
                advanceToMs(at + 1)
            }
            assertEquals(6, piano.sent.size)
        }

    @Test
    fun `the echo listens once the playback is over`() =
        runTest(dispatcher) {
            val vm = viewModel()
            play(C4)
            assertEquals(EchoPhase.PLAYING, vm.state.value.echo)

            advanceToMs(PLAYBACK_MS)

            assertEquals(EchoPhase.LISTENING, vm.state.value.echo)
            assertFalse(vm.state.value.keysHighlighted)
        }

    @Test
    fun `echo passes and moves on`() =
        runTest(dispatcher) {
            val vm = connectAndListen()

            // Any octave, one stray note forgiven.
            play(D4, MidiNote(48), MidiNote(76), MidiNote(55))

            assertEquals(EchoPhase.PASSED, vm.state.value.echo)
            assertEquals(0, vm.state.value.misses)
            vm.continueToStartPoint()
            runCurrent()
            assertEquals(OnboardingStep.START_POINT, vm.state.value.step)
            assertEquals(0, input.open)
        }

    @Test
    fun `miss offers replay`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            val sentBefore = piano.sent.size

            play(C4, E4, F4)

            assertEquals(EchoPhase.MISSED, vm.state.value.echo)
            assertEquals(1, vm.state.value.misses)
            assertFalse(vm.state.value.canSkipEcho)

            vm.replay()
            runCurrent()
            assertEquals(EchoPhase.PLAYING, vm.state.value.echo)
            advanceToMs(testScheduler.currentTime + PLAYBACK_MS)
            assertEquals(sentBefore + 6, piano.sent.size)
            assertEquals(EchoPhase.LISTENING, vm.state.value.echo)
            assertEquals(1, input.open)
            passEcho(vm)
        }

    @Test
    fun `playing again after a miss starts a fresh attempt`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            play(D4, D4)
            assertEquals(EchoPhase.MISSED, vm.state.value.echo)

            play(C4)
            assertEquals(EchoPhase.LISTENING, vm.state.value.echo)
            play(E4, G4)

            assertEquals(EchoPhase.PASSED, vm.state.value.echo)
            assertEquals(1, vm.state.value.misses)
        }

    @Test
    fun `skip after three misses`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            vm.continueToStartPoint()
            runCurrent()
            assertEquals(OnboardingStep.ECHO, vm.state.value.step)

            missThreeTimes()

            assertTrue(vm.state.value.canSkipEcho)
            vm.continueToStartPoint()
            runCurrent()
            assertEquals(OnboardingStep.START_POINT, vm.state.value.step)
        }

    @Test
    fun `no output port skips the playback`() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = viewModel()

            play(C4)

            assertTrue(piano.sent.isEmpty())
            assertEquals(EchoPhase.LISTENING, vm.state.value.echo)
            assertTrue(vm.state.value.keysHighlighted)
            play(C4, E4, G4)
            assertEquals(EchoPhase.PASSED, vm.state.value.echo)
        }

    @Test
    fun `a piano that stops accepting the notes shows the keys instead`() =
        runTest(dispatcher) {
            piano.failSends = true
            val vm = viewModel()

            play(C4)

            assertTrue(vm.state.value.keysHighlighted)
            assertEquals(EchoPhase.LISTENING, vm.state.value.echo)
        }

    @Test
    fun `a replay in the middle of a note releases the key`() =
        runTest(dispatcher) {
            val vm = viewModel()
            play(C4)
            advanceToMs(PLAYBACK_MS / 2)
            val before = piano.sent.size

            vm.replay()
            runCurrent()

            assertContentEquals(noteBytes(E4, on = false), piano.sent[before])
        }

    @Test
    fun `a dropped piano sends the learner back to connecting`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            play(D4, D4)

            piano.unplug()
            runCurrent()

            assertEquals(OnboardingState(), vm.state.value)
            piano.plugIn()
            runCurrent()
            play(C4)
            assertEquals(piano.device, vm.state.value.device)
        }

    @Test
    fun `a resumed link during the echo does not disturb the attempt`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            play(C4)
            val before = vm.state.value

            piano.dropLink()
            runCurrent()

            assertEquals(before, vm.state.value)
            play(E4, G4)
            assertEquals(EchoPhase.PASSED, vm.state.value.echo)
        }

    @Test
    fun `starting at unit 2 places units 0 and 1`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)

            vm.start(StartPoint.UNIT_2)
            runCurrent()

            val placed = learnerRepository.states()
            val expected =
                Curriculum.units
                    .take(2)
                    .flatMap { it.skills }
                    .toSet()
            assertEquals(expected, placed.keys)
            assertTrue(placed.values.all { it.learned })
            assertEquals(LocalDate(2026, 11, 18), placed.values.first().introducedDay)
            assertEquals(PLACEMENT_ATTEMPT_ID, learnerRepository.reviews(expected.first()).single().attemptId)
        }

    @Test
    fun `starting at the beginning places nothing`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)

            vm.start(StartPoint.BEGINNING)
            runCurrent()

            assertTrue(learnerRepository.states().isEmpty())
            assertTrue(vm.state.value.finished)
        }

    @Test
    fun `finishing sets onboarded`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            assertFalse(settings.settings.first().onboarded)

            vm.start(StartPoint.UNIT_1)
            runCurrent()

            assertTrue(settings.settings.first().onboarded)
            assertTrue(vm.state.value.finished)
            assertEquals(
                Curriculum.units
                    .first()
                    .skills
                    .toSet(),
                learnerRepository.states().keys,
            )
        }

    @Test
    fun `a store that fails keeps the start point open for another try`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            settings.failing = true

            vm.start(StartPoint.UNIT_1)
            runCurrent()

            assertTrue(vm.state.value.storageFailed)
            assertFalse(vm.state.value.finished)
            settings.failing = false
            vm.start(StartPoint.UNIT_1)
            runCurrent()
            assertTrue(vm.state.value.finished)
            assertFalse(vm.state.value.storageFailed)
            assertTrue(settings.settings.first().onboarded)
        }

    @Test
    fun `closes input before the start point`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            missThreeTimes()
            assertEquals(1, input.open)

            vm.continueToStartPoint()
            runCurrent()

            assertEquals(OnboardingStep.START_POINT, vm.state.value.step)
            assertEquals(0, input.open)
        }

    @Test
    fun `steps out of order are ignored`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.replay()
            vm.continueToStartPoint()
            vm.start(StartPoint.UNIT_2)
            runCurrent()
            assertEquals(OnboardingState(listed = piano.device), vm.state.value)

            play(C4)
            advanceToMs(PLAYBACK_MS)
            vm.continueToStartPoint() // not passed, not three misses
            vm.start(StartPoint.UNIT_2)
            runCurrent()

            assertEquals(OnboardingStep.ECHO, vm.state.value.step)
            assertTrue(learnerRepository.states().isEmpty())
        }

    @Test
    fun `a second start while saving is ignored`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)

            vm.start(StartPoint.UNIT_2)
            vm.start(StartPoint.BEGINNING)
            runCurrent()

            assertEquals(
                Curriculum.units
                    .take(2)
                    .flatMap { it.skills }
                    .toSet(),
                learnerRepository.states().keys,
            )
        }

    @Test
    fun `scanning connects the Bluetooth pianos it finds and lets go of the ones not chosen`() =
        runTest(dispatcher) {
            val ble = MidiDevice("ble:AA", "Bluetooth Piano")
            piano.advertised = listOf(ble)
            val vm = viewModel()

            vm.scanBluetooth()
            vm.scanBluetooth() // already scanning
            runCurrent()
            assertTrue(ble in piano.inputDevices.first())

            play(C4)
            advanceToMs(Tunables.BLE_SCAN_TIMEOUT_MS)

            assertEquals(piano.device, vm.state.value.device)
            assertFalse(ble in piano.inputDevices.first())
        }

    @Test
    fun `a Bluetooth piano connected for the search and picked is remembered`() =
        runTest(dispatcher) {
            val ble = MidiDevice("ble:AA", "Bluetooth Piano")
            val blePiano = FakePiano(device = ble)
            blePiano.unplug()
            blePiano.advertised = listOf(ble)
            OnboardingViewModel(
                blePiano,
                blePiano,
                blePiano,
                LearnerModel(Curriculum.graph, learnerRepository),
                settings,
                clock,
                TimeZone.UTC,
            ).scanBluetooth()
            runCurrent()
            assertNull(settings.settings.first().lastPiano)

            blePiano.press(C4)
            runCurrent()

            assertEquals(ble, settings.settings.first().lastPiano)
        }

    @Test
    fun `a wired piano is not remembered`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            assertNull(settings.settings.first().lastPiano)
            assertEquals(piano.device, vm.state.value.device)
        }

    @Test
    fun `an unwritable store does not stop the echo of a remembered Bluetooth piano`() =
        runTest(dispatcher) {
            val ble = MidiDevice("ble:AA", "Bluetooth Piano")
            val blePiano = FakePiano(device = ble)
            blePiano.unplug()
            blePiano.advertised = listOf(ble)
            val vm =
                OnboardingViewModel(
                    blePiano,
                    blePiano,
                    blePiano,
                    LearnerModel(Curriculum.graph, learnerRepository),
                    settings,
                    clock,
                    TimeZone.UTC,
                )
            vm.scanBluetooth()
            runCurrent()
            settings.failing = true

            blePiano.press(C4)
            runCurrent()

            assertEquals(ble, vm.state.value.device)
        }

    @Test
    fun `scanning without Bluetooth leaves the search running`() =
        runTest(dispatcher) {
            piano.bluetoothProblem = BluetoothUnavailableException.Reason.BLUETOOTH_OFF
            val vm = viewModel()

            vm.scanBluetooth()
            runCurrent()
            assertNull(vm.state.value.device)

            // A piano that is not found again is skipped too.
            piano.bluetoothProblem = null
            piano.advertised = listOf(MidiDevice("ble:BB", "Gone"))
            vm.scanBluetooth()
            runCurrent()
            play(C4)
            assertEquals(piano.device, vm.state.value.device)
        }

    @Test
    fun `restarting from C after a mistake still passes`() =
        runTest(dispatcher) {
            val vm = connectAndListen()

            play(C4, E4, C4, E4, G4)

            assertEquals(EchoPhase.PASSED, vm.state.value.echo)
            assertEquals(1, vm.state.value.misses)
        }

    @Test
    fun `the note that caused a miss counts as the first of the next attempt`() =
        runTest(dispatcher) {
            val vm = connectAndListen()

            // F misses C E F, then stands as the one forgiven stray note before C E G.
            play(C4, E4, F4, C4, E4, G4)

            assertEquals(EchoPhase.PASSED, vm.state.value.echo)
            assertEquals(1, vm.state.value.misses)
        }

    @Test
    fun `scanning stops once a piano is picked`() =
        runTest(dispatcher) {
            val ble = MidiDevice("ble:AA", "Bluetooth Piano")
            val late = MidiDevice("ble:LATE", "Late Piano")
            piano.advertised = listOf(ble)
            val vm = viewModel()
            vm.scanBluetooth()
            runCurrent()

            play(C4)
            piano.advertised = listOf(ble, late)
            runCurrent()
            vm.scanBluetooth() // after the pick: ignored

            val listed = piano.inputDevices.first()
            assertFalse(late in listed)
            assertFalse(ble in listed)
        }

    @Test
    fun `releasing disconnects the scanned pianos that were not chosen`() =
        runTest(dispatcher) {
            val ble = MidiDevice("ble:AA", "Bluetooth Piano")
            piano.advertised = listOf(ble)
            val vm = viewModel()
            vm.scanBluetooth()
            runCurrent()
            assertTrue(ble in piano.inputDevices.first())

            vm.release()

            assertFalse(ble in piano.inputDevices.first())
            assertTrue(piano.device in piano.inputDevices.first())
        }

    @Test
    fun `leaving for the piano list keeps the scanned pianos connected`() =
        runTest(dispatcher) {
            val ble = MidiDevice("ble:AA", "Bluetooth Piano")
            piano.advertised = listOf(ble)
            val vm = viewModel()
            vm.scanBluetooth()
            runCurrent()

            vm.handOverToPianoList()
            vm.release() // what clearing the ViewModel runs

            assertEquals(0, input.open)
            assertTrue(ble in piano.inputDevices.first())
        }

    @Test
    fun `rapid replay then skip never opens the input twice`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            missThreeTimes()

            vm.replay()
            vm.continueToStartPoint()
            runCurrent()

            assertEquals(OnboardingStep.START_POINT, vm.state.value.step)
            assertEquals(0, input.open)
            assertEquals(1, input.maxOpen)
        }

    @Test
    fun `rapid replays keep one reader`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            play(D4, D4)

            repeat(3) { vm.replay() }
            runCurrent()
            advanceToMs(testScheduler.currentTime + PLAYBACK_MS)

            assertEquals(1, input.open)
            assertEquals(1, input.maxOpen)
        }

    private companion object {
        /** Three notes 500 ms apart, the last held 400 ms. */
        const val PLAYBACK_MS = 1_400L
    }
}
