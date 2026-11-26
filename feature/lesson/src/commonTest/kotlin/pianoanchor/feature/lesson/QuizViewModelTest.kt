package pianoanchor.feature.lesson

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Rating
import pianoanchor.core.model.TryCheck
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.Quiz
import pianoanchor.engine.lesson.QuizPrompt
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakeMetronome
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** An input that counts the flows collected from it right now. */
private class OpenCountInput(
    private val delegate: MidiInputPort,
) : MidiInputPort by delegate {
    var open = 0
        private set

    override fun events(device: MidiDevice): Flow<MidiEvent> =
        delegate
            .events(device)
            .onStart { open++ }
            .onCompletion { open-- }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val input = OpenCountInput(piano)
    private val metronome = FakeMetronome(piano.clock)
    private val repository = FakeLearnerRepository()
    private val learner = LearnerModel(Curriculum.graph, repository)
    private val settings = FakeSettingsRepository()
    private val noon =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-11-16T12:00:00Z")
        }
    private val today = LocalDate(2026, 11, 16)
    private var ids = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun key(
        skillId: String,
        note: Int,
    ) = Quiz(skillId, TryCheck.PlayKey(note, lit = false), QuizPrompt.PlayKey(note))

    private val three =
        listOf(
            key(Skills.MIDDLE_C.id, 60),
            key(Skills.BLACK_KEY_GROUPS.id, 62),
            key(Skills.TREBLE_CLEF.id, 67),
        )

    private fun TestScope.viewModel(quizzes: List<Quiz> = three): QuizViewModel =
        QuizViewModel(
            piano.device,
            quizzes,
            input,
            metronome,
            piano.clock,
            learner,
            settings,
            noon,
            TimeZone.UTC,
        ) { "${ids++}" }.also { runCurrent() }

    private fun TestScope.press(note: Int) {
        piano.press(MidiNote(note))
        runCurrent()
    }

    /** Plays the right key and waits out the tick. */
    private fun TestScope.pass(note: Int) {
        press(note)
        advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
        runCurrent()
    }

    private suspend fun rating(skillId: String): Rating? = repository.reviews(skillId).singleOrNull()?.rating

    @Test
    fun `starts on the first quiz`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val state = vm.state.value
            assertEquals(1, state.number)
            assertEquals(3, state.count)
            assertEquals(three.first(), state.quiz)
            assertEquals(0, state.progress?.done)
            assertNull(state.summary)
            assertEquals(LessonConnection.CONNECTED, state.connection)
        }

    @Test
    fun `progresses through the quizzes and shows the summary`() =
        runTest(dispatcher) {
            val vm = viewModel()
            press(60)
            assertEquals(
                true,
                vm.state.value.progress
                    ?.finished,
            )
            assertEquals(1, vm.state.value.number)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            assertEquals(2, vm.state.value.number)
            assertEquals(three[1], vm.state.value.quiz)
            pass(62)
            assertEquals(3, vm.state.value.number)
            pass(67)
            assertNull(vm.state.value.quiz)
            assertEquals(QuizSummary(3, 3), vm.state.value.summary)
            assertEquals(Rating.GOOD, rating(Skills.MIDDLE_C.id))
            assertEquals(Rating.GOOD, rating(Skills.TREBLE_CLEF.id))
        }

    @Test
    fun `a wrong key counts and rates the quiz hard`() =
        runTest(dispatcher) {
            val vm = viewModel()
            press(62)
            assertEquals(
                1,
                vm.state.value.progress
                    ?.wrongCount,
            )
            assertEquals(
                62,
                vm.state.value.progress
                    ?.lastWrong,
            )
            assertEquals(setOf(60), vm.state.value.glowKeys)
            pass(60)
            pass(62)
            pass(67)
            assertEquals(Rating.HARD, rating(Skills.MIDDLE_C.id))
            assertEquals(QuizSummary(3, 2), vm.state.value.summary)
        }

    @Test
    fun `skip rates the quiz again and moves on at once`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.skip()
            runCurrent()
            assertEquals(2, vm.state.value.number)
            assertEquals(Rating.AGAIN, rating(Skills.MIDDLE_C.id))
            pass(62)
            vm.skip()
            runCurrent()
            assertEquals(QuizSummary(3, 1), vm.state.value.summary)
            // Nothing more to skip.
            vm.skip()
            runCurrent()
            assertEquals(1, repository.reviews(Skills.TREBLE_CLEF.id).size)
        }

    @Test
    fun `a skill already reviewed that day records nothing`() =
        runTest(dispatcher) {
            learner.recordQuiz(Skills.MIDDLE_C.id, Rating.HARD, today, "earlier")
            val vm = viewModel()
            pass(60)
            assertEquals(1, repository.reviews(Skills.MIDDLE_C.id).size)
            assertEquals(Rating.HARD, rating(Skills.MIDDLE_C.id))
            assertEquals(2, vm.state.value.number)
        }

    @Test
    fun `a store that fails is told and the quizzes go on`() =
        runTest(dispatcher) {
            val vm = viewModel()
            repository.failing = true
            pass(60)
            assertTrue(vm.state.value.storageFailed)
            assertEquals(2, vm.state.value.number)
        }

    @Test
    fun `keys held light the strip and notes during the tick do not count`() =
        runTest(dispatcher) {
            val vm = viewModel()
            press(60)
            assertEquals(setOf(60), vm.state.value.litKeys)
            press(61)
            assertEquals(
                0,
                vm.state.value.progress
                    ?.wrongCount,
            )
            piano.release(MidiNote(60))
            runCurrent()
            assertEquals(setOf(61), vm.state.value.litKeys)
        }

    @Test
    fun `a quiz with a click counts in and stops the click when it ends`() =
        runTest(dispatcher) {
            val beat = Quiz(Skills.STEADY_BEAT.id, TryCheck.Repeat(60, 2, 60), QuizPrompt.ClickTimes(60, 2))
            val vm = viewModel(listOf(beat, three[0]))
            assertNotNull(metronome.running.value)
            // During the count-in nothing counts.
            press(60)
            assertEquals(
                0,
                vm.state.value.progress
                    ?.done,
            )
            advanceTimeBy(5_000)
            runCurrent()
            assertNull(vm.state.value.countIn)
            press(60)
            press(60)
            assertEquals(
                true,
                vm.state.value.progress
                    ?.finished,
            )
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            assertNull(metronome.running.value)
            assertEquals(2, vm.state.value.number)
        }

    @Test
    fun `skipping during a count-in stops the click at once`() =
        runTest(dispatcher) {
            val beat = Quiz(Skills.STEADY_BEAT.id, TryCheck.Repeat(60, 2, 60), QuizPrompt.ClickTimes(60, 2))
            val vm = viewModel(listOf(beat, three[0]))
            assertNotNull(metronome.running.value)
            vm.skip()
            runCurrent()
            assertNull(metronome.running.value)
            assertNull(vm.state.value.countIn)
            assertEquals(2, vm.state.value.number)
            // The skipped quiz's count-in never opens the next quiz to notes early, nor shows its numerals.
            advanceTimeBy(5_000)
            runCurrent()
            assertNull(vm.state.value.countIn)
        }

    @Test
    fun `leaving closes the piano and the click and starts nothing more`() =
        runTest(dispatcher) {
            val beat = Quiz(Skills.STEADY_BEAT.id, TryCheck.Repeat(60, 2, 60), QuizPrompt.ClickTimes(60, 2))
            val vm = viewModel(listOf(beat, three[0]))
            assertEquals(1, input.open)
            vm.close()
            assertEquals(0, input.open)
            assertNull(metronome.running.value)
            vm.skip()
            runCurrent()
            assertEquals(1, vm.state.value.number)
        }

    @Test
    fun `a piano that goes away is shown`() =
        runTest(dispatcher) {
            val vm = viewModel()
            piano.unplug()
            runCurrent()
            assertEquals(LessonConnection.DISCONNECTED, vm.state.value.connection)
        }

    @Test
    fun `an empty list of quizzes is refused`() =
        runTest(dispatcher) {
            assertFailsWith<IllegalArgumentException> { viewModel(emptyList()) }
        }

    private fun TestScope.tapPedal() {
        piano.play(byteArrayOf(0xB0.toByte(), 64, 127))
        piano.play(byteArrayOf(0xB0.toByte(), 64, 0))
        runCurrent()
    }

    private fun TestScope.collectDone(vm: QuizViewModel): MutableList<Unit> {
        val done = mutableListOf<Unit>()
        backgroundScope.launch(dispatcher) { vm.pedalDone.collect { done += it } }
        runCurrent()
        return done
    }

    private fun TestScope.toSummary(vm: QuizViewModel) {
        press(60)
        advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
        runCurrent()
        pass(62)
        pass(67)
        assertNotNull(vm.state.value.summary)
    }

    @Test
    fun `a pedal tap during the summary asks for Done once`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val done = collectDone(vm)
            toSummary(vm)
            assertTrue(vm.state.value.pedalHint)

            tapPedal()
            tapPedal()

            assertEquals(1, done.size)
            vm.close()
        }

    @Test
    fun `the pedal is ignored during a quiz`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val done = collectDone(vm)

            tapPedal()

            assertTrue(done.isEmpty())
            assertEquals(1, vm.state.value.number)
            assertEquals(false, vm.state.value.pedalHint)
            vm.close()
        }

    @Test
    fun `a pedal press that began during a quiz is not a tap in the summary`() =
        runTest(dispatcher) {
            val vm = viewModel(listOf(three[0]))
            val done = collectDone(vm)
            piano.play(byteArrayOf(0xB0.toByte(), 64, 127))
            press(60)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            assertNotNull(vm.state.value.summary)

            piano.play(byteArrayOf(0xB0.toByte(), 64, 0))
            runCurrent()

            assertTrue(done.isEmpty())
            vm.close()
        }

    @Test
    fun `the pedal does nothing with the setting off`() =
        runTest(dispatcher) {
            settings.update { it.copy(pedalControlOn = false) }
            val vm = viewModel()
            val done = collectDone(vm)
            toSummary(vm)

            tapPedal()

            assertTrue(done.isEmpty())
            assertEquals(false, vm.state.value.pedalHint)
            vm.close()
        }

    @Test
    fun `an unreadable setting leaves the pedal alone`() =
        runTest(dispatcher) {
            settings.failing = true
            val vm = viewModel()
            val done = collectDone(vm)
            toSummary(vm)

            tapPedal()

            assertTrue(done.isEmpty())
            vm.close()
        }

    @Test
    fun `a pedal tap after leaving asks for nothing`() =
        runTest(dispatcher) {
            val vm = viewModel(listOf(three[0]))
            val done = collectDone(vm)
            press(60)
            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS)
            runCurrent()
            vm.close()

            tapPedal()

            assertTrue(done.isEmpty())
        }
}
