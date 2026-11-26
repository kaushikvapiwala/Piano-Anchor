package pianoanchor.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeCalibrationRepository
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakeMetronome
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Timed runs that count in by themselves and start again on a wrong or missed note. CDE at 60 %: a beat is 1250 ms. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelRestartTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock).apply { inputOnly = true }
    private val attempts = FakeAttemptRepository()
    private val metronome = FakeMetronome(clock)
    private val learner = LearnerModel(Curriculum.graph, FakeLearnerRepository())
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val f4 = MidiNote(65)
    private val ms = 1_000_000L
    private val beatMs = 1_250L
    private val countInMs = Tunables.TEMPO_COUNT_IN_LEAD_MS + 4 * beatMs
    private val autoStartMs = Tunables.TEMPO_AUTO_START_DELAY_MS
    private val pauseMs = Tunables.TEMPO_RESTART_PAUSE_MS
    private var ids = 0
    private val opened = mutableListOf<PracticeViewModel>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.practice(): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            piano,
            attempts,
            FakeCalibrationRepository(),
            metronome,
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            learner,
            TimeZone.UTC,
            FakeSettingsRepository(),
        ) {
            "attempt-${++ids}"
        }.also {
            opened += it
            runCurrent()
        }

    private fun runPractice(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                opened.forEach { it.close() }
            }
        }

    private fun TestScope.advance(ms: Long) {
        repeat((ms / 50).toInt()) {
            clock.advanceNanos(50 * this@PracticeViewModelRestartTest.ms)
            advanceTimeBy(50)
            runCurrent()
        }
    }

    private fun TestScope.press(note: MidiNote) {
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    /** Selects the 60 % rung and lets it arm: the count-in begins now. */
    private fun TestScope.startRun(vm: PracticeViewModel) {
        vm.selectRung(TempoRung.P60)
        runCurrent()
        advance(autoStartMs)
    }

    /** The count-in ends and F is played where C is due: the run stops. */
    private fun TestScope.wrongFirstNote() {
        advance(countInMs)
        press(f4)
    }

    private fun TestScope.cleanRun() {
        advance(countInMs)
        press(c4)
        advance(beatMs)
        press(d4)
        advance(beatMs)
        press(e4)
        advance(3_750)
    }

    @Test
    fun `timed rung counts in by itself after the demo`() =
        runPractice {
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            runCurrent()
            assertEquals(RunPhase.READY, vm.state.value.phase)
            advance(autoStartMs)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
            advance(Tunables.TEMPO_COUNT_IN_LEAD_MS + beatMs)
            assertEquals(2, vm.state.value.beat)
            advance(3 * beatMs)
            assertEquals(RunPhase.PLAYING, vm.state.value.phase)
        }

    @Test
    fun `a key during arming is not judged`() =
        runPractice {
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            runCurrent()
            press(f4)

            assertEquals(emptyMap(), vm.state.value.liveMarks)
            assertNull(vm.state.value.restartReason)
            assertEquals(RunPhase.READY, vm.state.value.phase)
        }

    @Test
    fun `wrong note stops and restarts from the top`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs)
            press(c4)
            advance(beatMs)
            press(f4)

            assertEquals(RestartReason(RestartKind.WRONG, f4), vm.state.value.restartReason)
            assertEquals(RunPhase.READY, vm.state.value.phase)
            assertNull(metronome.running.value)
            assertNull(attempts.get("attempt-1"))

            advance(pauseMs)
            assertNull(vm.state.value.restartReason)
            advance(autoStartMs)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
            assertNotNull(metronome.running.value)
            assertNull(attempts.get("attempt-1"))
        }

    @Test
    fun `missed note restarts`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs)
            press(c4)
            advance(beatMs + beatMs / 2 + 100) // D's window closed unplayed

            assertEquals(RestartReason(RestartKind.MISSED, d4), vm.state.value.restartReason)
            assertNull(attempts.get("attempt-1"))
            advance(pauseMs + autoStartMs)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
        }

    @Test
    fun `late note does not restart`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs)
            press(c4)
            advance(beatMs + 150)
            press(d4)

            assertNull(vm.state.value.restartReason)
            assertEquals(RunPhase.PLAYING, vm.state.value.phase)
            assertNotNull(vm.state.value.liveMarks[pianoanchor.core.model.ScoreNoteRef(1, d4)])
        }

    @Test
    fun `clean run opens results and resets the streak`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            wrongFirstNote()
            advance(pauseMs + autoStartMs)
            cleanRun()

            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
            assertNull(vm.state.value.restartReason)
        }

    @Test
    fun `three restarts offer a slower rung`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            repeat(2) {
                wrongFirstNote()
                assertFalse(vm.state.value.offerSlower)
                advance(pauseMs + autoStartMs)
            }
            wrongFirstNote()
            assertTrue(vm.state.value.offerSlower)
            advance(pauseMs + autoStartMs + countInMs)
            assertEquals(RunPhase.READY, vm.state.value.phase) // no re-arm
            assertNull(metronome.running.value)

            vm.slowerRung()
            runCurrent()
            assertEquals(TempoRung.WAIT, vm.state.value.rung)
            assertFalse(vm.state.value.offerSlower)
            assertNull(vm.state.value.restartReason)
        }

    @Test
    fun `keep trying arms again and starts the streak over`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            repeat(2) {
                wrongFirstNote()
                advance(pauseMs + autoStartMs)
            }
            wrongFirstNote()
            assertTrue(vm.state.value.offerSlower)

            vm.keepTrying()
            runCurrent()
            assertFalse(vm.state.value.offerSlower)
            assertNull(vm.state.value.restartReason)
            advance(autoStartMs)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)

            wrongFirstNote() // the streak began again: one restart, no card
            assertFalse(vm.state.value.offerSlower)
        }

    @Test
    fun `slower and keep trying do nothing without the card`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            vm.slowerRung()
            vm.keepTrying()
            runCurrent()

            assertEquals(TempoRung.P60, vm.state.value.rung)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
        }

    @Test
    fun `a rung change resets the restart streak`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            repeat(2) {
                wrongFirstNote()
                advance(pauseMs + autoStartMs)
            }
            vm.selectRung(TempoRung.P70)
            runCurrent()
            advance(autoStartMs)
            wrongFirstNote() // beat is shorter now, still before the first window or on it: wrong either way

            assertFalse(vm.state.value.offerSlower)
        }

    @Test
    fun `perform mode restarts too`() =
        runPractice {
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            vm.selectMode(RunMode.PERFORM)
            runCurrent()
            advance(autoStartMs)
            wrongFirstNote()

            assertEquals(RestartReason(RestartKind.WRONG, f4), vm.state.value.restartReason)
            advance(pauseMs + autoStartMs)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
            assertNull(attempts.get("attempt-1"))
        }

    @Test
    fun `link resume re-arms without a key`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs)
            piano.dropLink()
            runCurrent()

            assertEquals(RunPhase.READY, vm.state.value.phase)
            advance(autoStartMs)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
        }

    @Test
    fun `an idle timed run stops at the offer card`() =
        runPractice {
            val vm = practice()
            startRun(vm)
            // No key at all: every take misses its first note and restarts, until the card replaces the re-arm.
            repeat(Tunables.TEMPO_RESTARTS_BEFORE_OFFER) {
                advance(countInMs + beatMs)
                advance(pauseMs + autoStartMs)
            }

            assertTrue(vm.state.value.offerSlower)
            assertEquals(RunPhase.READY, vm.state.value.phase)
            assertNull(metronome.running.value)
        }
}
