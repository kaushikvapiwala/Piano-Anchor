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
import pianoanchor.core.model.SceneHand
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

private const val DEMO_STEP_MS = 10L

/** The See step's hand (`HandDemo`): its timeline and elapsed time while the demo plays. */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelHandTest {
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

    @Test
    fun seeOfAnEvenScaleShowsNoHandButStillPlays() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.THUMB_UNDER)
            vm.goTo(LessonStep.SEE)
            runCurrent()

            assertNull(vm.state.value.seeHand)
            assertTrue(vm.state.value.playing)
            vm.close()
        }

    @Test
    fun seeOfAMelodicCheckExposesTheHandAndTheElapsedTime() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.RH_C_POSITION)
            vm.goTo(LessonStep.SEE)
            runCurrent()

            val hand = checkNotNull(vm.state.value.seeHand)
            assertEquals(SceneHand.RIGHT, hand.hand)
            assertEquals(MidiNote(60), hand.thumbNote)
            assertEquals(0L, vm.state.value.seeElapsedMs)
            advanceTimeBy(Tunables.PLAYBACK_NOTE_GAP_MS)
            runCurrent()
            assertEquals(Tunables.PLAYBACK_NOTE_GAP_MS, vm.state.value.seeElapsedMs)

            advanceTimeBy(DEMO_DONE_MS)
            runCurrent()
            assertEquals(0L, vm.state.value.seeElapsedMs)
            vm.close()
        }

    @Test
    fun replaySeeRestartsTheHandFromTheStart() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.RH_C_POSITION)
            vm.goTo(LessonStep.SEE)
            runCurrent()
            advanceTimeBy(Tunables.PLAYBACK_NOTE_GAP_MS * 2)
            runCurrent()
            assertTrue(vm.state.value.seeElapsedMs > 0)

            vm.replaySee()
            runCurrent()

            assertEquals(0L, vm.state.value.seeElapsedMs)
            assertNotNull(vm.state.value.seeHand)
            vm.close()
        }

    @Test
    fun theHandGoesWhenTheStepChanges() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.RH_C_POSITION)
            vm.goTo(LessonStep.SEE)
            runCurrent()
            vm.goTo(LessonStep.WHY)
            runCurrent()
            assertNull(vm.state.value.seeHand)
            vm.close()
        }

    @Test
    fun aCheckWithNothingToHearHasNoHand() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.HAND_SHAPE)
            vm.goTo(LessonStep.SEE)
            runCurrent()

            assertNull(vm.state.value.seeHand)
            vm.close()
        }

    @Test
    fun aLeftHandConceptGetsALeftHand() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.LH_C_POSITION)
            vm.goTo(LessonStep.SEE)
            runCurrent()

            val hand = checkNotNull(vm.state.value.seeHand)
            assertEquals(SceneHand.LEFT, hand.hand)
            // The lone C3 is the little finger of the left-hand C position (C3..G3), the thumb on G3.
            assertEquals(MidiNote(55), hand.thumbNote)
            assertEquals(listOf(5), hand.presses.map { it.finger })
            vm.close()
        }

    private val handWaitMs = Tunables.HAND_HOLD_AFTER_MS + Tunables.HAND_FADE_MS + Tunables.HAND_YOUR_TURN_MS

    /** Runs the demo to its end and returns the virtual time at which it stopped. */
    private fun TestScope.untilDemoEnds(vm: LessonViewModel): Long {
        while (vm.state.value.playing) {
            advanceTimeBy(DEMO_STEP_MS)
            runCurrent()
        }
        return testScheduler.currentTime
    }

    @Test
    fun seeWaitsForTheHandsCueBeforeTry() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.RH_C_POSITION)
            vm.goTo(LessonStep.SEE)
            runCurrent()
            untilDemoEnds(vm)
            assertNotNull(vm.state.value.seeHand)

            advanceTimeBy(handWaitMs - DEMO_STEP_MS)
            runCurrent()
            assertEquals(LessonStep.SEE, vm.state.value.step)
            advanceTimeBy(DEMO_STEP_MS)
            runCurrent()

            assertEquals(LessonStep.TRY, vm.state.value.step)
            vm.close()
        }

    @Test
    fun seeWithoutAHandMovesToTryRightAfterTheDemo() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.HIGH_AND_LOW)
            vm.goTo(LessonStep.SEE)
            runCurrent()
            untilDemoEnds(vm)

            assertNull(vm.state.value.seeHand)
            assertEquals(LessonStep.TRY, vm.state.value.step)
            vm.close()
        }

    @Test
    fun playAgainDuringTheHandWaitRestartsTheDemoAndTheWait() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.RH_C_POSITION)
            vm.goTo(LessonStep.SEE)
            runCurrent()
            untilDemoEnds(vm)
            advanceTimeBy(handWaitMs - DEMO_STEP_MS)
            runCurrent()

            vm.replaySee()
            runCurrent()
            assertTrue(vm.state.value.playing)
            untilDemoEnds(vm)
            advanceTimeBy(handWaitMs - DEMO_STEP_MS)
            runCurrent()
            // Played again by hand, so the lesson stays in See.
            assertEquals(LessonStep.SEE, vm.state.value.step)
            vm.close()
        }
}

private const val DEMO_DONE_MS = 20_000L
