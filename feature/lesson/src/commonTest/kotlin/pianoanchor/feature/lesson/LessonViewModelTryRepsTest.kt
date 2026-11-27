package pianoanchor.feature.lesson

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Skill
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakeMetronome
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeTone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Try's clean reps in a row (`tryRepsNeeded`): when it passes, resets and restarts. */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelTryRepsTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val tone = FakeTone()
    private val metronome = FakeMetronome(piano.clock)
    private val settings = FakeSettingsRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(skill: Skill = Skills.MIDDLE_C): LessonViewModel =
        LessonViewModel(
            piano.device,
            skill.id,
            piano,
            piano,
            tone,
            metronome,
            settings,
            FakeAttemptRepository(),
            LearnerModel(Curriculum.graph, FakeLearnerRepository()),
            NoteLabelsUseCase(
                settings,
                FakeAttemptRepository(),
                LearnerModel(Curriculum.graph, FakeLearnerRepository()),
                TimeZone.UTC,
            ),
            piano.clock,
        ).also { runCurrent() }

    private fun TestScope.press(
        note: Int,
        velocity: Int = 64,
    ) {
        piano.press(MidiNote(note), velocity)
        runCurrent()
    }

    private fun TestScope.toTry(vm: LessonViewModel) {
        vm.goTo(LessonStep.TRY)
        runCurrent()
    }

    @Test
    fun threeCleanRepsPassTheTryAfterEachTickPause() =
        runTest(dispatcher) {
            val vm = viewModel()
            toTry(vm)
            assertEquals(3, vm.state.value.repsNeeded)

            repeat(2) { rep ->
                press(60)
                piano.release(MidiNote(60))
                assertTrue(
                    vm.state.value.tryProgress
                        ?.finished == true,
                )
                assertEquals(rep + 1, vm.state.value.cleanReps)
                advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS - 1)
                runCurrent()
                assertTrue(
                    vm.state.value.tryProgress
                        ?.finished == true,
                )
                advanceTimeBy(1)
                runCurrent()
                // The next rep starts fresh, still in Try.
                assertEquals(LessonStep.TRY, vm.state.value.step)
                assertEquals(
                    false,
                    vm.state.value.tryProgress
                        ?.finished,
                )
            }
            press(60)
            assertEquals(3, vm.state.value.cleanReps)
            assertEquals(LessonStep.TRY, vm.state.value.step)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            assertEquals(LessonStep.USE, vm.state.value.step)
            assertEquals(LessonStep.USE, settings.settings.first().lessonSteps[Skills.MIDDLE_C.id])
        }

    @Test
    fun aWrongKeyResetsTheCleanRepsToZero() =
        runTest(dispatcher) {
            val vm = viewModel()
            toTry(vm)
            press(60)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            assertEquals(1, vm.state.value.cleanReps)

            press(62)
            assertEquals(0, vm.state.value.cleanReps)
            // The hint line is still the existing wrong-key feedback.
            assertEquals(
                62,
                vm.state.value.tryProgress
                    ?.lastWrong,
            )
            // The rep that had a wrong key doesn't count when it finishes.
            press(60)
            assertEquals(0, vm.state.value.cleanReps)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            assertEquals(LessonStep.TRY, vm.state.value.step)
        }

    @Test
    fun aLongCheckNeedsTwoCleanReps() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.RH_C_POSITION)
            toTry(vm)
            assertEquals(2, vm.state.value.repsNeeded)
            repeat(2) {
                listOf(60, 62, 64, 65, 67, 65, 64, 62, 60).forEach { press(it) }
                advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
                runCurrent()
            }
            assertEquals(LessonStep.USE, vm.state.value.step)
        }

    @Test
    fun aCheckTheAppCannotVerifyNeedsOneRep() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.HAND_SHAPE)
            toTry(vm)
            assertEquals(1, vm.state.value.repsNeeded)
            press(60)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            assertEquals(LessonStep.USE, vm.state.value.step)
        }

    private fun TestScope.stressBar(bars: Int = 2) =
        repeat(bars * 4) { i ->
            press(60, if (i % 4 == 0) 110 else 60)
            piano.release(MidiNote(60))
            runCurrent()
        }

    @Test
    fun aClickedTrysNextRepStartsWithAFreshCountIn() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.BARS_AND_COUNTING)
            toTry(vm)
            assertEquals(2, vm.state.value.repsNeeded)
            advanceTimeBy(5_000)
            runCurrent()
            stressBar()
            assertEquals(1, vm.state.value.cleanReps)

            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            // Rep 2 waits for a count-in: notes pressed now are not counted.
            advanceTimeBy(2_000)
            runCurrent()
            assertNotNull(vm.state.value.countIn)
            press(60, 110)
            assertEquals(
                0,
                vm.state.value.tryProgress
                    ?.done,
            )

            advanceTimeBy(3_000)
            runCurrent()
            assertNull(vm.state.value.countIn)
            stressBar()
            assertEquals(2, vm.state.value.cleanReps)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            assertEquals(LessonStep.USE, vm.state.value.step)
            vm.close()
        }
}
