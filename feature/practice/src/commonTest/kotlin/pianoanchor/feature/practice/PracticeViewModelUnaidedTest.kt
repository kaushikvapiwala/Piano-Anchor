package pianoanchor.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Band
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
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
import pianoanchor.testing.FakeTone
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

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelUnaidedTest {
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

    private fun TestScope.practice(
        passage: Passage = Passages.CDE,
        unaided: Boolean = true,
    ): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            passage,
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
            unaided = unaided,
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
    fun anUnaidedRunShowsNoHintAndSavesAnUnaidedAttempt() =
        runTest(dispatcher) {
            val vm = practice()
            val state = vm.state.value
            assertTrue(state.unaided)
            assertEquals(HintLevel.OFF, state.hintLevel)
            assertFalse(state.hintVisible)
            vm.showHint()
            assertFalse(vm.state.value.hintVisible)

            tap(c4)
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(Band.CLEAN, saved.band)
            assertEquals(HintLevel.OFF, saved.hintLevel)
            assertFalse(saved.hintShown)
            assertNull(saved.tempoBpm)
            // The same run is one that passes the concept.
            assertNotNull(attempts.firstCleanUnaidedWaitRun(Passages.CDE.id))
        }

    @Test
    fun aWrongKeyStillShowsTheHintLineButNoGlowAndTheRunStaysUnaided() =
        runTest(dispatcher) {
            val vm = practice()

            tap(d4)

            assertNotNull(vm.state.value.hint)
            assertEquals(emptySet(), vm.state.value.hintGlow)
            tap(c4)
            tap(d4)
            tap(e4)
            assertFalse(assertNotNull(attempts.get("attempt-1")).hintShown)
        }

    @Test
    fun anAidedRunStillHintsAtFull() =
        runTest(dispatcher) {
            val vm = practice(unaided = false)

            assertFalse(vm.state.value.unaided)
            assertTrue(vm.state.value.hintVisible)
            tap(c4)
            tap(d4)
            tap(e4)
            assertTrue(assertNotNull(attempts.get("attempt-1")).hintShown)
        }

    @Test
    fun aUnitZeroPassageGlowsTheNextKeyOnlyWhenAided() =
        runTest(dispatcher) {
            val march = checkNotNull(Curriculum.passage("builtin:middle-c-march"))
            val aided = practice(march, unaided = false)
            assertTrue(
                aided.state.value.glowKeys
                    .isNotEmpty(),
            )
            aided.close()

            val unaided = practice(march)
            assertEquals(emptySet(), unaided.state.value.glowKeys)
            unaided.close()
        }
}
