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
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.unaided
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.NoteLabelsUseCase
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

/** Perform mode: one unaided take at a timed rung (`docs/algorithms/guidance.md`). CDE at 60 % runs at 48 BPM. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelPerformTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)

    // No MIDI output: a timed run takes the transport default at once instead of measuring the link.
    private val piano = FakePiano(clock).apply { inputOnly = true }
    private val attempts = FakeAttemptRepository()
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val f4 = MidiNote(65)
    private val ms = 1_000_000L
    private val beatMs = 1_250L
    private val countInMs = Tunables.TEMPO_COUNT_IN_LEAD_MS + 4 * beatMs
    private val runMs = 6_250L
    private var ids = 0
    private val opened = mutableListOf<PracticeViewModel>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.practice(
        initialRung: TempoRung = TempoRung.WAIT,
        initialMode: RunMode = RunMode.PRACTICE,
    ): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            piano,
            attempts,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            LearnerModel(Curriculum.graph, FakeLearnerRepository()),
            TimeZone.UTC,
            FakeSettingsRepository(),
            NoteLabelsUseCase(
                FakeSettingsRepository(),
                attempts,
                LearnerModel(Curriculum.graph, FakeLearnerRepository()),
                TimeZone.UTC,
            ),
            initialRung,
            initialMode,
        ) {
            "attempt-${++ids}"
        }.also {
            opened += it
            runCurrent()
        }

    private fun perform(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                opened.forEach { it.close() }
            }
        }

    private fun TestScope.advance(millis: Long) {
        repeat((millis / 50).toInt()) {
            clock.advanceNanos(50 * ms)
            advanceTimeBy(50)
            runCurrent()
        }
    }

    private fun TestScope.press(note: MidiNote) {
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    private fun TestScope.hold(
        note: MidiNote,
        heldMs: Long = 900,
    ) {
        piano.press(note)
        runCurrent()
        advance(heldMs)
        piano.release(note)
        runCurrent()
    }

    /** Opens the 60 % rung in perform mode, presses the starting key and plays the count-in. */
    private fun TestScope.startTake(): PracticeViewModel {
        val vm = practice(TempoRung.P60, RunMode.PERFORM)
        advance(Tunables.TEMPO_AUTO_START_DELAY_MS) // no key: the run counts in by itself
        advance(countInMs)
        return vm
    }

    @Test
    fun performRecordsAPerformRunWithoutHints() =
        perform {
            val vm = startTake()
            hold(c4)
            advance(beatMs - 900)
            hold(d4)
            advance(beatMs - 900)
            hold(e4, heldMs = 2 * beatMs)
            vm.showHint() // the Hint button is gone, but a stray call must not count either
            advance(runMs)

            val saved = assertNotNull(attempts.get(assertNotNull(vm.state.value.finishedAttemptId)))
            assertEquals(RunMode.PERFORM, saved.mode)
            assertFalse(saved.hintShown)
            assertTrue(saved.unaided)
            assertEquals(Band.CLEAN, saved.band)
        }

    @Test
    fun performHidesTheBandTheColoursTheTagsAndTheVerdictText() =
        perform {
            val vm = startTake()
            assertFalse(vm.state.value.hintVisible)
            assertFalse(vm.state.value.showsVerdicts)

            press(c4)
            advance(beatMs + 300)
            press(d4) // 300 ms late
            piano.press(f4) // wrong, and still held
            runCurrent()

            assertTrue(
                vm.state.value.liveMarks
                    .isEmpty(),
            )
            assertNull(vm.state.value.flash)
            assertNull(vm.state.value.lastKey)
            assertTrue(
                vm.state.value.wrongKeys
                    .isEmpty(),
            )
            assertEquals(setOf(f4), vm.state.value.keysDown) // pressed keys still show, neutrally
            assertFalse(vm.state.value.hintVisible)
        }

    @Test
    fun performDrawsEveryNoteNeutrally() =
        perform {
            val vm = startTake()
            val neutral = List(3) { StepStatus.UPCOMING }

            assertEquals(RunPhase.PLAYING, vm.state.value.phase)
            assertEquals(0, vm.state.value.currentStep)
            assertEquals(
                neutral,
                vm.state.value.steps
                    .map { it.status },
            )
            press(c4) // a missed note would restart the run
            advance(beatMs)
            assertEquals(1, vm.state.value.currentStep) // the cursor still moves with the clock
            assertEquals(
                neutral,
                vm.state.value.steps
                    .map { it.status },
            )
        }

    @Test
    fun performIsUnavailableAtWait() =
        perform {
            val vm = practice()
            vm.selectMode(RunMode.PERFORM)
            runCurrent()
            assertEquals(RunMode.PRACTICE, vm.state.value.mode)

            vm.selectRung(TempoRung.P60)
            vm.selectMode(RunMode.PERFORM)
            runCurrent()
            assertEquals(RunMode.PERFORM, vm.state.value.mode)
            vm.selectRung(TempoRung.WAIT)
            runCurrent()
            assertEquals(RunMode.PRACTICE, vm.state.value.mode)
        }

    @Test
    fun aViewModelOpenedOnWaitInPerformModePractises() =
        perform { assertEquals(RunMode.PRACTICE, practice(initialMode = RunMode.PERFORM).state.value.mode) }

    @Test
    fun switchingModeDiscardsTheRun() =
        perform {
            val vm = practice(TempoRung.P60)
            advance(Tunables.TEMPO_AUTO_START_DELAY_MS)
            advance(countInMs)
            assertEquals(RunPhase.PLAYING, vm.state.value.phase)

            vm.selectMode(RunMode.PERFORM)
            runCurrent()
            assertEquals(RunPhase.READY, vm.state.value.phase)
            assertEquals(RunMode.PERFORM, vm.state.value.mode)
            advance(Tunables.TEMPO_AUTO_START_DELAY_MS / 2) // arming again, not yet counting in
            assertNull(vm.state.value.finishedAttemptId)

            vm.selectMode(RunMode.PERFORM) // the mode already selected changes nothing
            assertEquals(RunPhase.READY, vm.state.value.phase)
            vm.selectMode(RunMode.PRACTICE)
            runCurrent()
            assertEquals(RunMode.PRACTICE, vm.state.value.mode)
            assertTrue(vm.state.value.showsVerdicts)
        }
}
