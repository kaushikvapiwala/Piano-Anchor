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
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
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
import pianoanchor.testing.FakeTone
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelPlacementTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)
    private val noOutput = FakePiano(clock, MidiDevice("no-output", "No output"))
    private val attempts = FakeAttemptRepository()
    private val learnerRepository = FakeLearnerRepository()
    private val tone = FakeTone()
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val f4 = MidiNote(65)
    private var ids = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Asks for the timed perform rung with a player, which a placement piece must ignore. */
    private fun TestScope.practice(): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            noOutput,
            attempts,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            LearnerModel(Curriculum.graph, learnerRepository),
            TimeZone.UTC,
            FakeSettingsRepository(),
            NoteLabelsUseCase(
                FakeSettingsRepository(),
                attempts,
                LearnerModel(Curriculum.graph, learnerRepository),
                TimeZone.UTC,
            ),
            TempoRung.P100,
            RunMode.PERFORM,
            player = PassagePlayer(piano.device, piano, tone),
            placement = true,
        ) {
            "attempt-${++ids}"
        }.also { runCurrent() }

    private fun TestScope.tap(note: MidiNote) {
        clock.advanceNanos(300_000_000)
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    @Test
    fun placementRunHasNoHintsNoHearFirstNoResults() =
        runTest(dispatcher) {
            val vm = practice()
            advanceTimeBy(30_000)
            runCurrent()

            val state = vm.state.value
            assertTrue(state.sightReading) // the screen hides tempo chips, perform and Hint
            assertEquals(TempoRung.WAIT, state.rung)
            assertEquals(RunMode.PRACTICE, state.mode)
            assertFalse(state.listening)
            assertFalse(state.showsListen)
            assertTrue(tone.played.isEmpty())
            assertEquals(HintLevel.OFF, state.hintLevel)
            assertFalse(state.hintVisible)
            vm.showHint()
            assertFalse(vm.state.value.hintVisible)

            tap(c4)
            tap(d4)
            tap(e4)

            // The run is saved as a normal attempt and the band is handed on for the onboarding: no Results screen.
            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.OFF, saved.hintLevel)
            assertFalse(saved.hintShown)
            assertEquals(Band.CLEAN, vm.state.value.finishedBand)
            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
            assertTrue(learnerRepository.states().isEmpty()) // placement, not the run, marks skills
        }

    @Test
    fun failedPlacementRunRecordsNoReview() =
        runTest(dispatcher) {
            val vm = practice()

            tap(c4)
            tap(f4)
            tap(d4)
            tap(e4)

            assertEquals(Band.KEEP_PRACTISING, vm.state.value.finishedBand)
            assertNotNull(attempts.get("attempt-1"))
            assertTrue(learnerRepository.states().isEmpty())
            assertFalse(vm.state.value.saveFailed)
        }

    @Test
    fun passedPlacementRunRecordsNoReview() =
        runTest(dispatcher) {
            val vm = practice()

            tap(c4)
            tap(d4)
            tap(e4)

            assertEquals(Band.CLEAN, vm.state.value.finishedBand)
            assertTrue(learnerRepository.states().isEmpty())
        }
}
