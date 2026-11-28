package pianoanchor.feature.lesson

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Skill
import pianoanchor.core.ports.AttemptRepositoryPort
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val DEMO_DONE_MS = 20_000L
private const val NANOS_PER_MS = 1_000_000L

/** The hands-free lesson: auto-advance and the sustain-pedal tap as "continue". */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonPedalTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val settings = FakeSettingsRepository()
    private val attempts = FakeAttemptRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        skill: Skill = Skills.MIDDLE_C,
        startAt: LessonStep? = null,
        pedalIsMusic: Boolean = false,
        attemptsPort: AttemptRepositoryPort = attempts,
        usePassage: Passage? = usePassageOf(skill.id),
    ): LessonViewModel =
        LessonViewModel(
            piano.device,
            skill.id,
            piano,
            piano,
            FakeTone(),
            FakeMetronome(piano.clock),
            settings,
            attemptsPort,
            LearnerModel(Curriculum.graph, FakeLearnerRepository()),
            NoteLabelsUseCase(
                settings,
                attemptsPort,
                LearnerModel(Curriculum.graph, FakeLearnerRepository()),
                TimeZone.UTC,
            ),
            piano.clock,
            startAt,
            pedalIsMusic = pedalIsMusic,
            usePassage = usePassage,
        ).also { runCurrent() }

    private fun TestScope.finishDemo() {
        advanceTimeBy(DEMO_DONE_MS)
        runCurrent()
    }

    private fun TestScope.tapPedal(holdMs: Long = 0) {
        piano.play(byteArrayOf(0xB0.toByte(), 64, 127))
        piano.clock.advanceNanos(holdMs * NANOS_PER_MS)
        piano.play(byteArrayOf(0xB0.toByte(), 64, 0))
        runCurrent()
    }

    private fun TestScope.collectExits(vm: LessonViewModel): MutableList<LessonExit> {
        val exits = mutableListOf<LessonExit>()
        backgroundScope.launch(dispatcher) { vm.exits.collect { exits += it } }
        runCurrent()
        return exits
    }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun whyMovesToSeeByItselfAfterOneAnimationLoop() =
        runTest(dispatcher) {
            val vm = viewModel()

            wait(Tunables.LESSON_WHY_AUTO_MS - 1)
            assertEquals(LessonStep.WHY, vm.state.value.step)
            wait(1)

            assertEquals(LessonStep.SEE, vm.state.value.step)
            vm.close()
        }

    @Test
    fun aPedalTapOnWhyGoesToSeeAtOnce() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertTrue(vm.state.value.pedalHint)

            tapPedal()

            assertEquals(LessonStep.SEE, vm.state.value.step)
            vm.close()
        }

    @Test
    fun seeMovesToTryWhenTheDemoEnds() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.MIDDLE_C, startAt = LessonStep.SEE)
            assertEquals(LessonStep.SEE, vm.state.value.step)

            finishDemo()

            assertEquals(LessonStep.TRY, vm.state.value.step)
            vm.close()
        }

    @Test
    fun aPedalTapDuringTheDemoGoesToTry() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.MIDDLE_C, startAt = LessonStep.SEE)
            assertTrue(vm.state.value.playing)

            tapPedal()

            assertEquals(LessonStep.TRY, vm.state.value.step)
            vm.close()
        }

    @Test
    fun playingTheDemoAgainDoesNotMoveOn() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.MIDDLE_C, startAt = LessonStep.SEE)
            finishDemo()
            vm.goTo(LessonStep.SEE)
            runCurrent()
            vm.replaySee()
            runCurrent()

            finishDemo()

            assertEquals(LessonStep.SEE, vm.state.value.step)
            vm.close()
        }

    @Test
    fun aSeeWithNothingToHearMovesOnAfterTheWhyWait() =
        runTest(dispatcher) {
            val skill = Skills.HAND_SHAPE
            val vm = viewModel(skill, startAt = LessonStep.SEE)

            wait(Tunables.LESSON_WHY_AUTO_MS - 1)
            assertEquals(LessonStep.SEE, vm.state.value.step)
            wait(1)

            assertEquals(LessonStep.TRY, vm.state.value.step)
            vm.close()
        }

    @Test
    fun theHintAndThePedalAreOffDuringATry() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.MIDDLE_C, startAt = LessonStep.TRY)
            assertFalse(vm.state.value.pedalHint)

            tapPedal()

            assertEquals(LessonStep.TRY, vm.state.value.step)
            assertEquals(
                false,
                vm.state.value.tryProgress
                    ?.finished,
            )
            vm.close()
        }

    @Test
    fun useOpensPracticeByItselfAfterTheTitleWait() =
        runTest(dispatcher) {
            val vm = viewModel()
            val exits = collectExits(vm)
            vm.goTo(LessonStep.USE)
            runCurrent()

            wait(Tunables.LESSON_AUTO_OPEN_MS - 1)
            assertTrue(exits.isEmpty())
            wait(1)
            wait(Tunables.LESSON_AUTO_OPEN_MS)

            assertEquals(listOf(LessonExit.PLAY_IT), exits)
            vm.close()
        }

    @Test
    fun aLessonResumedAtUseStaysThere() =
        runTest(dispatcher) {
            val vm = viewModel(startAt = LessonStep.USE)
            val exits = collectExits(vm)

            wait(Tunables.LESSON_AUTO_OPEN_MS * 2)

            assertTrue(exits.isEmpty())
            assertEquals(LessonStep.USE, vm.state.value.step)
            vm.close()
        }

    @Test
    fun aPedalTapOnUseOpensPracticeOnce() =
        runTest(dispatcher) {
            val vm = viewModel(startAt = LessonStep.USE)
            val exits = collectExits(vm)

            tapPedal()
            tapPedal()

            assertEquals(listOf(LessonExit.PLAY_IT), exits)
            vm.close()
        }

    @Test
    fun recapWaitsAndAPedalTapGoesToTheNextLesson() =
        runTest(dispatcher) {
            val vm = viewModel(startAt = LessonStep.RECAP)
            val exits = collectExits(vm)

            wait(Tunables.LESSON_WHY_AUTO_MS * 2)
            assertEquals(LessonStep.RECAP, vm.state.value.step)
            assertTrue(exits.isEmpty())
            tapPedal()

            assertEquals(listOf(LessonExit.NEXT_LESSON), exits)
            vm.close()
        }

    @Test
    fun aPedalTapOnTheLastRecapIsDone() =
        runTest(dispatcher) {
            attempts.failing = true
            val vm = viewModel(Skills.MELODY_CHORDS_IN_G, startAt = LessonStep.RECAP)
            val exits = collectExits(vm)

            tapPedal()

            assertEquals(listOf(LessonExit.DONE), exits)
            vm.close()
        }

    @Test
    fun withThePedalSettingOffTheStepsIgnoreIt() =
        runTest(dispatcher) {
            settings.update { it.copy(pedalControlOn = false) }
            val vm = viewModel()
            val exits = collectExits(vm)
            assertFalse(vm.state.value.pedalHint)

            tapPedal()
            assertEquals(LessonStep.WHY, vm.state.value.step)
            vm.goTo(LessonStep.USE)
            runCurrent()
            tapPedal()
            vm.goTo(LessonStep.RECAP)
            runCurrent()
            tapPedal()

            assertTrue(exits.isEmpty())
            vm.close()
        }

    @Test
    fun aConceptThatUsesThePedalAsMusicIgnoresIt() =
        runTest(dispatcher) {
            val vm = viewModel(pedalIsMusic = true)

            tapPedal()

            assertEquals(LessonStep.WHY, vm.state.value.step)
            assertFalse(vm.state.value.pedalHint)
            vm.close()
        }

    @Test
    fun aHeldPedalIsNotATapAndAnUnreadableSettingIsOff() =
        runTest(dispatcher) {
            val vm = viewModel()
            tapPedal(holdMs = Tunables.PEDAL_TAP_MAX_MS + 1)
            assertEquals(LessonStep.WHY, vm.state.value.step)
            vm.close()

            settings.failing = true
            val other = viewModel()
            tapPedal()
            assertEquals(LessonStep.WHY, other.state.value.step)
            other.close()
        }

    @Test
    fun noHintWithoutAConnection() =
        runTest(dispatcher) {
            val vm = viewModel()
            piano.unplug()
            runCurrent()

            assertFalse(vm.state.value.pedalHint)
            vm.close()
        }

    @Test
    fun aConceptWithoutAUsePassageNeverLatchesTheExit() =
        runTest(dispatcher) {
            val vm = viewModel(usePassage = null)
            val exits = collectExits(vm)
            vm.goTo(LessonStep.USE)
            runCurrent()

            wait(Tunables.LESSON_AUTO_OPEN_MS * 2)
            vm.goTo(LessonStep.RECAP)
            runCurrent()
            tapPedal()

            assertEquals(1, exits.size)
            assertNotNull(exits.single())
            vm.close()
        }

    @Test
    fun aRecapTapBeforeTheRecapHasLoadedIsIgnored() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val slow =
                object : AttemptRepositoryPort by attempts {
                    override suspend fun lastPlayed(passageId: String): Instant? {
                        gate.await()
                        return attempts.lastPlayed(passageId)
                    }
                }
            val vm = viewModel(startAt = LessonStep.RECAP, attemptsPort = slow)
            val exits = collectExits(vm)
            assertNull(vm.state.value.recap)
            assertFalse(vm.state.value.pedalHint)

            tapPedal()
            assertTrue(exits.isEmpty())
            gate.complete(Unit)
            runCurrent()
            assertTrue(vm.state.value.pedalHint)
            tapPedal()

            assertEquals(1, exits.size)
            vm.close()
        }

    @Test
    fun aPressThatBeganInATryIsNotATapOnTheNextStep() =
        runTest(dispatcher) {
            val vm = viewModel(startAt = LessonStep.TRY)
            val exits = collectExits(vm)
            piano.play(byteArrayOf(0xB0.toByte(), 64, 127))
            runCurrent()
            vm.goTo(LessonStep.USE)
            runCurrent()

            piano.play(byteArrayOf(0xB0.toByte(), 64, 0))
            runCurrent()

            assertTrue(exits.isEmpty())
            vm.close()
        }

    @Test
    fun useDoesNotOpenPracticeWhileThePianoIsDisconnected() =
        runTest(dispatcher) {
            val vm = viewModel()
            val exits = collectExits(vm)
            vm.goTo(LessonStep.USE)
            runCurrent()
            piano.unplug()
            runCurrent()

            wait(Tunables.LESSON_AUTO_OPEN_MS * 2)

            assertTrue(exits.isEmpty())
            vm.close()
        }
}
