package pianoanchor.feature.lesson

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
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.engine.lesson.TryTiming
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NANOS_PER_MS = 1_000_000L
private const val DEMO_DONE_MS = 20_000L
private const val SUSTAIN = 64
private const val CC = 0xB0

/** The pedal and legato Try checks in the lesson: the pedal reaches the run, the See demo cues it. */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelPedalTryTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val tone = FakeTone()
    private val settings = FakeSettingsRepository()
    private val chord = setOf(48, 52, 55)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(check: TryCheck): LessonViewModel =
        LessonViewModel(
            piano.device,
            Skills.MIDDLE_C.id,
            piano,
            piano,
            tone,
            FakeMetronome(piano.clock),
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
            tryCheck = check,
        ).also { runCurrent() }

    private fun TestScope.toTry(vm: LessonViewModel) {
        vm.goTo(LessonStep.TRY)
        runCurrent()
    }

    private fun TestScope.pedal(
        value: Int,
        controller: Int = SUSTAIN,
    ) {
        piano.play(byteArrayOf(CC.toByte(), controller.toByte(), value.toByte()))
        runCurrent()
    }

    private fun TestScope.wait(ms: Long) {
        piano.clock.advanceNanos(ms * NANOS_PER_MS)
    }

    private fun TestScope.playChord(notes: Set<Int>) {
        notes.forEach { piano.press(MidiNote(it)) }
        runCurrent()
    }

    private fun TestScope.liftChord(notes: Set<Int>) {
        notes.forEach { piano.release(MidiNote(it)) }
        runCurrent()
    }

    @Test
    fun theSustainPedalReachesAPedalHoldTry() =
        runTest(dispatcher) {
            val vm = viewModel(TryCheck.PedalHold(chord))
            toTry(vm)

            pedal(127)
            playChord(chord)
            assertEquals(
                1,
                vm.state.value.tryProgress
                    ?.done,
            )
            wait(500)
            liftChord(chord)
            wait(Tunables.TRY_PEDAL_RING_MS + 100)
            pedal(0)

            assertEquals(
                true,
                vm.state.value.tryProgress
                    ?.finished,
            )
            assertEquals(1, vm.state.value.cleanReps)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            vm.close()
        }

    @Test
    fun aPedalLiftedTooSoonResetsTheRepAndShowsTheTiming() =
        runTest(dispatcher) {
            val vm = viewModel(TryCheck.PedalHold(chord))
            toTry(vm)

            pedal(127)
            playChord(chord)
            liftChord(chord)
            wait(100)
            pedal(0)

            assertEquals(
                TryTiming.EARLY,
                vm.state.value.tryProgress
                    ?.timing,
            )
            assertEquals(0, vm.state.value.cleanReps)
            vm.close()
        }

    @Test
    fun otherControllersAndOtherStepsDoNotReachTheRun() =
        runTest(dispatcher) {
            val vm = viewModel(TryCheck.PedalHold(chord))
            pedal(127)
            toTry(vm)

            pedal(127, controller = 1)
            playChord(chord)
            assertEquals(
                0,
                vm.state.value.tryProgress
                    ?.done,
            )
            vm.close()
        }

    @Test
    fun aLegatoTryIsFedNoteOnsAndOffs() =
        runTest(dispatcher) {
            val vm = viewModel(TryCheck.Legato(listOf(60, 62)))
            toTry(vm)

            piano.press(MidiNote(60))
            runCurrent()
            wait(500)
            piano.release(MidiNote(60))
            runCurrent()
            wait(Tunables.TRY_LEGATO_MAX_GAP_MS + 120)
            piano.press(MidiNote(62))
            runCurrent()

            assertEquals(
                TryTiming.GAP,
                vm.state.value.tryProgress
                    ?.timing,
            )
            assertEquals(
                60,
                vm.state.value.tryProgress
                    ?.expected,
            )
            assertTrue(
                vm.state.value.glowKeys
                    .isNotEmpty(),
            )
            vm.close()
        }

    @Test
    fun theSeeDemoCuesThePedalOnThePianoAndOnScreen() =
        runTest(dispatcher) {
            val vm = viewModel(TryCheck.PedalHold(chord))
            vm.goTo(LessonStep.SEE)
            runCurrent()
            assertEquals(true, vm.state.value.pedalCue)
            assertTrue(showsPedalCue(vm.state.value))

            advanceTimeBy(Tunables.TRY_PEDAL_RING_MS + 500)
            runCurrent()
            assertEquals(true, vm.state.value.pedalCue)

            advanceTimeBy(DEMO_DONE_MS)
            runCurrent()
            val controllers = piano.sent.filter { it[0].toInt() and 0xFF == CC }.map { it[2].toInt() }
            assertEquals(listOf(127, 0), controllers)
            assertNull(vm.state.value.pedalCue)
            vm.close()
        }

    @Test
    fun theSpeakerPlaysTheNotesAloneAndTheDemoStillShowsThePedal() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = viewModel(TryCheck.PedalChange(listOf(setOf(48, 52), setOf(53, 57))))
            vm.goTo(LessonStep.SEE)
            runCurrent()
            assertEquals(true, vm.state.value.pedalCue)

            advanceTimeBy(DEMO_DONE_MS)
            runCurrent()
            assertTrue(piano.sent.isEmpty())
            assertEquals(4, tone.played.single().size)
            vm.close()
        }

    @Test
    fun otherStepsShowNoPedalCue() =
        runTest(dispatcher) {
            val vm = viewModel(TryCheck.PedalHold(chord))
            assertEquals(false, showsPedalCue(vm.state.value))
            vm.goTo(LessonStep.TRY)
            runCurrent()
            assertEquals(false, showsPedalCue(vm.state.value))
            vm.close()
        }
}
