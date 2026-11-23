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
import pianoanchor.core.model.MidiNote
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
import kotlin.test.assertNotNull
import kotlin.time.Instant

/** A resumed link (the piano's Bluetooth link dropped and came back) in a wait-mode run. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelLinkTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)
    private val attempts = FakeAttemptRepository()
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val ms = 1_000_000L
    private var ids = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

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
            CalibrationSkips().apply { skip(piano.device.id) },
        ) {
            "attempt-${++ids}"
        }.also { runCurrent() }

    private fun TestScope.tap(note: MidiNote) {
        clock.advanceNanos(300 * ms)
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    @Test
    fun aResumedLinkInWaitModeChangesNothing() =
        runTest(dispatcher) {
            val vm = practice()
            tap(c4)
            val before = vm.state.value

            piano.dropLink()
            runCurrent()

            assertEquals(before, vm.state.value)
            tap(d4)
            tap(e4)
            assertNotNull(attempts.get("attempt-1"))
        }

    @Test
    fun aResumedLinkLightsNoKeyThatWasLetGoInTheGap() =
        runTest(dispatcher) {
            val vm = practice()
            piano.press(c4)
            runCurrent()
            assertEquals(setOf(c4), vm.state.value.keysDown)

            piano.dropLink()
            runCurrent()

            assertEquals(emptySet(), vm.state.value.keysDown)
            assertEquals(1, vm.state.value.currentStep)
        }

    @Test
    fun aResumedLinkDuringTheTailEndsTheTakeWithTheHeldKeysReleased() =
        runTest(dispatcher) {
            val vm = practice()
            tap(c4)
            tap(d4)
            clock.advanceNanos(300 * ms)
            piano.press(e4) // the last key stays down
            runCurrent()

            piano.dropLink()
            runCurrent()

            assertNotNull(attempts.get("attempt-1"))
            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
        }
}
