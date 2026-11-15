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
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StepCorrect
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.learner.LearnerModel
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
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelHintTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)
    private val attempts = FakeAttemptRepository()
    private val skips = CalibrationSkips().apply { skip(piano.device.id) }
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val ms = 1_000_000L
    private var ids = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** [bands] stored runs of C D E, oldest first, each with a 300 ms gap between its steps. */
    private suspend fun store(vararg bands: Band) {
        bands.forEachIndexed { i, band ->
            attempts.save(
                Attempt(
                    id = "old-$i",
                    passageId = Passages.CDE.id,
                    deviceId = piano.device.id,
                    startedAt = Instant.fromEpochSeconds(1_000L + i),
                    durationNanos = 600 * ms,
                    stepCount = 3,
                    wrongNotes = 0,
                    incompleteChords = 0,
                    accuracy = 1.0,
                    band = band,
                    events =
                        listOf(
                            StepCorrect(0, 0),
                            StepCorrect(300 * ms, 1),
                            StepCorrect(600 * ms, 2),
                            PassageComplete(600 * ms, 2),
                        ),
                    tempoBpm = null,
                    playedNotes = emptyList(),
                ),
            )
        }
    }

    private fun TestScope.practice(): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            attempts,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            LearnerModel(Curriculum.graph, FakeLearnerRepository()),
            TimeZone.UTC,
            FakeSettingsRepository(),
            skips,
        ) {
            "attempt-${++ids}"
        }.also { runCurrent() }

    private fun TestScope.tap(
        note: MidiNote,
        gapMs: Long = 300,
    ) {
        clock.advanceNanos(gapMs * ms)
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    private fun TestScope.wait(millis: Long) {
        clock.advanceNanos(millis * ms)
        advanceTimeBy(millis)
        runCurrent()
    }

    private val delayedHistory = arrayOf(Band.CLEAN, Band.CLEAN)
    private val offHistory = arrayOf(Band.CLEAN, Band.CLEAN, Band.CLEAN, Band.CLEAN)

    @Test
    fun fullShowsTheBandAtOnceAndRecordsAnAidedRun() =
        runTest(dispatcher) {
            val vm = practice()

            assertEquals(HintLevel.FULL, vm.state.value.hintLevel)
            assertTrue(vm.state.value.hintVisible)
            tap(c4)
            assertTrue(vm.state.value.hintVisible)
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.FULL, saved.hintLevel)
            assertTrue(saved.hintShown)
        }

    @Test
    fun delayedShowsTheBandOnlyAfterTheDelayAndMarksTheRunAided() =
        runTest(dispatcher) {
            store(*delayedHistory)
            val vm = practice()

            assertEquals(HintLevel.DELAYED, vm.state.value.hintLevel)
            assertFalse(vm.state.value.hintVisible)
            wait(799)
            assertFalse(vm.state.value.hintVisible)
            wait(1)
            assertTrue(vm.state.value.hintVisible)

            tap(c4)
            assertFalse(vm.state.value.hintVisible) // the next step starts without the band
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.DELAYED, saved.hintLevel)
            assertTrue(saved.hintShown)
        }

    @Test
    fun delayedKeyPressedInTimeKeepsTheRunUnaided() =
        runTest(dispatcher) {
            store(*delayedHistory)
            val vm = practice()

            tap(c4, gapMs = 500)
            assertFalse(vm.state.value.hintVisible)
            tap(d4, gapMs = 500)
            tap(e4, gapMs = 500)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.DELAYED, saved.hintLevel)
            assertFalse(saved.hintShown)
        }

    @Test
    fun delayedHintDoesNotFireInTheReleaseTailAfterTheLastStep() =
        runTest(dispatcher) {
            store(*delayedHistory)
            val vm = practice()
            tap(c4)
            tap(d4)
            clock.advanceNanos(300 * ms)
            piano.press(e4) // held: the run waits in the release tail
            runCurrent()

            wait(900)

            assertFalse(vm.state.value.hintVisible)
        }

    @Test
    fun offShowsNothingUntilTheHintButtonThenForOneStepOnly() =
        runTest(dispatcher) {
            store(*offHistory)
            val vm = practice()

            assertEquals(HintLevel.OFF, vm.state.value.hintLevel)
            assertFalse(vm.state.value.hintVisible)
            wait(5_000)
            assertFalse(vm.state.value.hintVisible)

            vm.showHint()
            assertTrue(vm.state.value.hintVisible)
            tap(c4)
            assertFalse(vm.state.value.hintVisible)
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.OFF, saved.hintLevel)
            assertTrue(saved.hintShown)
        }

    @Test
    fun offWithoutTheHintButtonRecordsAnUnaidedRun() =
        runTest(dispatcher) {
            store(*offHistory)
            practice()

            tap(c4)
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.OFF, saved.hintLevel)
            assertFalse(saved.hintShown)
        }

    @Test
    fun anUnreadableStoreStartsAtFull() =
        runTest(dispatcher) {
            store(*offHistory)
            attempts.failing = true

            val vm = practice()

            assertEquals(HintLevel.FULL, vm.state.value.hintLevel)
            assertTrue(vm.state.value.hintVisible)
        }
}
