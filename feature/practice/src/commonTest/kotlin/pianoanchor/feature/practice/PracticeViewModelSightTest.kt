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
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.sight.SightReadingGenerator
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
class PracticeViewModelSightTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)
    private val noOutput = FakePiano(clock, MidiDevice("no-output", "No output"))
    private val attempts = FakeAttemptRepository()
    private val tone = FakeTone()
    private val sight = SightReadingGenerator.passage(1, 20_000)
    private var ids = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Asks for the timed perform rung with a player, which a sight-reading exercise must ignore. */
    private fun TestScope.practice(): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            sight,
            piano,
            noOutput,
            attempts,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            LearnerModel(Curriculum.graph, FakeLearnerRepository()),
            TimeZone.UTC,
            FakeSettingsRepository(),
            TempoRung.P100,
            RunMode.PERFORM,
            player = PassagePlayer(piano.device, piano, tone),
        ) {
            "attempt-${++ids}"
        }.also { runCurrent() }

    private fun TestScope.playThrough(vm: PracticeViewModel) {
        for (step in sight.steps) {
            clock.advanceNanos(300_000_000)
            step.pitches.forEach { piano.press(it) }
            step.pitches.forEach { piano.release(it) }
            runCurrent()
        }
        assertNotNull(vm.state.value.finishedAttemptId)
    }

    @Test
    fun sightRunHasNoHintsAndNoHearFirst() =
        runTest(dispatcher) {
            val vm = practice()
            advanceTimeBy(30_000)
            runCurrent()

            val state = vm.state.value
            assertTrue(state.sightReading)
            assertFalse(state.listening)
            assertFalse(state.canListen)
            assertFalse(state.showsListen)
            assertTrue(tone.played.isEmpty())
            assertTrue(piano.sent.isEmpty())
            assertEquals(HintLevel.OFF, state.hintLevel)
            assertFalse(state.hintVisible)

            vm.showHint()
            assertFalse(vm.state.value.hintVisible)

            playThrough(vm)
            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.OFF, saved.hintLevel)
            assertFalse(saved.hintShown)
            assertEquals(sight.id, saved.passageId)
            assertEquals(RunMode.PRACTICE, saved.mode)
            assertEquals(null, saved.tempoBpm)
        }

    @Test
    fun sightRunHidesTempoAndPerform() =
        runTest(dispatcher) {
            val vm = practice()

            // Asked for the 100% perform rung, it still runs in wait mode as practice.
            assertEquals(TempoRung.WAIT, vm.state.value.rung)
            assertEquals(RunMode.PRACTICE, vm.state.value.mode)
            vm.selectRung(TempoRung.P60)
            runCurrent()
            assertEquals(TempoRung.WAIT, vm.state.value.rung)
            vm.selectMode(RunMode.PERFORM)
            runCurrent()
            assertEquals(RunMode.PRACTICE, vm.state.value.mode)
            assertTrue(vm.state.value.sightReading)
        }

    @Test
    fun aWrongKeyShowsTheLandmarkLineButLightsNoKey() =
        runTest(dispatcher) {
            val vm = practice()
            val wrong =
                sight.steps
                    .first()
                    .pitches
                    .first()
                    .number + 1

            clock.advanceNanos(300_000_000)
            piano.press(MidiNote(wrong))
            runCurrent()

            assertNotNull(vm.state.value.hint)
            assertTrue(
                vm.state.value.hintGlow
                    .isEmpty(),
            )
        }
}
