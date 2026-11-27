package pianoanchor.feature.ear

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.EarChoice
import pianoanchor.core.model.EarExercise
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Rating
import pianoanchor.core.model.Skill
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.EarSkills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.engine.lesson.NoteNameStage
import pianoanchor.engine.midi.MidiEncoder
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeTone
import pianoanchor.testing.FakeWallClock
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

private const val PLAYBACK_DONE_MS = 5_000L

/** An input that counts the flows collected from it right now. */
private class CountingInput(
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
class EarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val input = CountingInput(piano)
    private val tone = FakeTone()
    private val repository = FakeLearnerRepository()
    private val clock = FakeWallClock(Instant.parse("2026-11-18T10:00:00Z"))
    private var ids = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        vararg skills: Skill = arrayOf(EarSkills.HIGHER_LOWER),
        random: Random = Random(1),
    ): EarViewModel =
        EarViewModel(
            piano.device,
            skills.toList(),
            input,
            piano,
            tone,
            LearnerModel(Curriculum.graph, repository),
            NoteLabelsUseCase(
                FakeSettingsRepository(),
                FakeAttemptRepository(),
                LearnerModel(Curriculum.graph, repository),
                TimeZone.UTC,
            ),
            clock,
            TimeZone.UTC,
            random,
        ) { "uuid-${++ids}" }.also { runCurrent() }

    /** Lets the item finish playing, so an echo item is listening. */
    private fun TestScope.finishPlaying() {
        advanceTimeBy(PLAYBACK_DONE_MS)
        runCurrent()
    }

    private fun EarViewModel.identify() = assertIs<EarExercise.Identify>(state.value.exercise)

    private fun EarViewModel.echo() = assertIs<EarExercise.Echo>(state.value.exercise)

    private fun TestScope.answerRight(vm: EarViewModel) {
        finishPlaying()
        vm.answer(vm.identify().answer)
        runCurrent()
    }

    private fun TestScope.answerWrong(vm: EarViewModel) {
        finishPlaying()
        val item = vm.identify()
        vm.answer(item.choices.first { it != item.answer })
        runCurrent()
    }

    private fun TestScope.playEcho(vm: EarViewModel) {
        vm.echo().sounds.flatten().forEach {
            piano.press(it)
            runCurrent()
        }
    }

    private fun TestScope.setOf(
        vm: EarViewModel,
        vararg first: Boolean,
    ) {
        first.forEach { right ->
            if (right) {
                answerRight(vm)
            } else {
                answerWrong(vm)
                answerWrong(vm)
                vm.next()
                runCurrent()
            }
        }
    }

    private suspend fun ratingOf(skill: Skill) = repository.reviews(skill.id).map { it.rating }

    @Test
    fun theEchoStripLettersItsKeysForANewLearner() =
        runTest(dispatcher) {
            val vm = viewModel()

            assertEquals(NoteNameStage.ALL_WHITE, vm.state.value.noteLabels.stage)
            vm.close()
        }

    @Test
    fun `an item plays through the piano when it lists an output`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(true, vm.state.value.playing)
            finishPlaying()

            val item = vm.identify()
            val (first, second) = item.sounds.map { it.single() }
            val expected =
                listOf(
                    NoteOn(0, 0, first, Tunables.PLAYBACK_VELOCITY),
                    NoteOff(0, 0, first, 0),
                    NoteOn(0, 0, second, Tunables.PLAYBACK_VELOCITY),
                    NoteOff(0, 0, second, 0),
                )
            assertEquals(expected.size, piano.sent.size)
            expected.zip(piano.sent).forEach { (message, bytes) ->
                assertContentEquals(MidiEncoder.encode(message), bytes)
            }
            assertEquals(emptyList(), tone.played)
            assertEquals(false, vm.state.value.playing)
        }

    @Test
    fun `an item plays on the speaker when the piano has no output`() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = viewModel()
            finishPlaying()

            assertEquals(1, tone.played.size)
            assertEquals(2, tone.played.single().size)
            assertEquals(emptyList(), piano.sent)
            assertEquals(vm.identify().sounds.map { it.single() }, tone.played.single().map { it.note })
        }

    @Test
    fun `a failed MIDI send moves the item and the rest of the session to the speaker`() =
        runTest(dispatcher) {
            piano.failSends = true
            val vm = viewModel()
            finishPlaying()
            assertEquals(1, tone.played.size)

            piano.failSends = false
            answerRight(vm)
            finishPlaying()

            assertEquals(2, tone.played.size)
            assertEquals(emptyList(), piano.sent)
        }

    @Test
    fun `a MIDI failure part way stops the keys that are down`() =
        runTest(dispatcher) {
            viewModel()
            advanceTimeBy(100)
            runCurrent()
            piano.failSends = true
            advanceTimeBy(PLAYBACK_DONE_MS)
            runCurrent()

            assertEquals(1, tone.played.size)
        }

    @Test
    fun `right on the first answer with no replay is first try`() =
        runTest(dispatcher) {
            val vm = viewModel()
            setOf(vm, true, true, true, true)

            assertEquals(listOf(Rating.GOOD), ratingOf(EarSkills.HIGHER_LOWER))
        }

    @Test
    fun `right after a replay or a wrong answer is recovered`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.replay()
            runCurrent()
            answerRight(vm)
            answerWrong(vm)
            assertEquals(EarFeedback.TRY_AGAIN, vm.state.value.feedback)
            answerRight(vm)
            setOf(vm, true, true)

            // A first try is the only way to CLEAN: two recovered items make the set GOOD.
            assertEquals(listOf(Rating.HARD), ratingOf(EarSkills.HIGHER_LOWER))
        }

    @Test
    fun `two wrong answers miss the item and show its answer`() =
        runTest(dispatcher) {
            val vm = viewModel()
            answerWrong(vm)
            assertEquals(true, vm.state.value.canShowMe)
            answerWrong(vm)

            assertEquals(true, vm.state.value.answerShown)
            assertEquals(1, vm.state.value.itemNumber)
            vm.next()
            runCurrent()
            assertEquals(2, vm.state.value.itemNumber)
            assertEquals(EarFeedback.NONE, vm.state.value.feedback)
        }

    @Test
    fun `show me after a wrong answer plays the item again and misses it`() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = viewModel()
            finishPlaying()
            vm.showMe()
            runCurrent()
            assertEquals(1, tone.played.size) // no wrong answer yet: ignored
            assertEquals(EarFeedback.NONE, vm.state.value.feedback)

            answerWrong(vm)
            vm.showMe()
            finishPlaying()

            assertEquals(2, tone.played.size)
            assertEquals(true, vm.state.value.answerShown)
            // A missed item can't be answered any more.
            answerRight(vm)
            assertEquals(1, vm.state.value.itemNumber)
        }

    @Test
    fun `replay plays again at most twice and is not an answer`() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = viewModel()
            finishPlaying()
            repeat(3) {
                vm.replay()
                finishPlaying()
            }

            assertEquals(1 + Tunables.EAR_MAX_REPLAYS, tone.played.size)
            assertEquals(0, vm.state.value.replaysLeft)
            assertEquals(EarFeedback.NONE, vm.state.value.feedback)
        }

    @Test
    fun `a set of four items records its band for the skill`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(1, vm.state.value.itemNumber)
            assertEquals(Tunables.EAR_ITEMS_PER_REVIEW, vm.state.value.itemCount)
            setOf(vm, true, true, true)
            assertEquals(emptyList(), ratingOf(EarSkills.HIGHER_LOWER))
            setOf(vm, false)

            val reviews = repository.reviews("ear-higher-lower")
            assertEquals(listOf(Rating.HARD), reviews.map { it.rating })
            assertEquals("ear:uuid-1", reviews.single().attemptId)
            assertEquals(EarSummary(1, 3, 4), vm.state.value.summary)
        }

    @Test
    fun `two missed items in a set are keep practising`() =
        runTest(dispatcher) {
            val vm = viewModel()
            setOf(vm, true, false, false, true)

            assertEquals(listOf(Rating.AGAIN), ratingOf(EarSkills.HIGHER_LOWER))
        }

    @Test
    fun `skills run in order and end on a summary`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.HIGHER_LOWER, EarSkills.STEP_SKIP)
            assertEquals("Higher or lower", vm.state.value.skillTitle)
            assertEquals(1, vm.state.value.skillNumber)
            setOf(vm, true, true, true, true)
            assertEquals("Step or skip", vm.state.value.skillTitle)
            assertEquals(2, vm.state.value.skillNumber)
            assertEquals(1, vm.state.value.itemNumber)
            assertNull(vm.state.value.summary)
            setOf(vm, true, true, true, true)

            assertEquals(EarSummary(2, 8, 8), vm.state.value.summary)
            assertNull(vm.state.value.exercise)
            assertEquals(2, vm.state.value.skillCount)
        }

    @Test
    fun `leaving part way keeps the finished skills and records nothing for the current one`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.HIGHER_LOWER, EarSkills.STEP_SKIP)
            setOf(vm, true, true, true, true, true, true)
            vm.close()

            assertEquals(1, repository.reviews("ear-higher-lower").size)
            assertEquals(emptyList(), repository.reviews("ear-step-skip"))
        }

    @Test
    fun `leaving right after the last answer of a set still records it`() =
        runTest(dispatcher) {
            val vm = viewModel()
            setOf(vm, true, true, true)
            finishPlaying()
            vm.answer(vm.identify().answer) // the set is complete; its recording is queued, not run yet
            vm.close()

            assertEquals(listOf(Rating.GOOD), ratingOf(EarSkills.HIGHER_LOWER))
        }

    @Test
    fun `nothing starts after leaving`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.ECHO_NOTE)
            finishPlaying()
            vm.close()
            vm.replay()
            runCurrent()

            assertEquals(0, input.open)
            assertEquals(false, vm.state.value.playing)
        }

    @Test
    fun `an identify answer before the item has finished sounding is ignored`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(true, vm.state.value.playing)
            vm.answer(vm.identify().answer)
            runCurrent()
            assertEquals(1, vm.state.value.itemNumber)

            answerRight(vm)
            assertEquals(2, vm.state.value.itemNumber)
        }

    @Test
    fun `a day has one review per skill`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.HIGHER_LOWER, EarSkills.HIGHER_LOWER)
            setOf(vm, true, true, true, true, false, false, false, false)

            assertEquals(1, repository.reviews("ear-higher-lower").size)
        }

    @Test
    fun `a result that cannot be saved is flagged`() =
        runTest(dispatcher) {
            repository.failing = true
            val vm = viewModel()
            setOf(vm, true, true, true, true)

            assertEquals(true, vm.state.value.storageFailed)
            assertNotNull(vm.state.value.summary)
        }

    @Test
    fun `an item repeated by the generator does not stall the set`() =
        runTest(dispatcher) {
            val constant =
                object : Random() {
                    override fun nextBits(bitCount: Int) = 0
                }
            val vm = viewModel(random = constant)
            setOf(vm, true, true, true, true)

            assertNotNull(vm.state.value.summary)
        }

    @Test
    fun `no skills end on an empty summary`() =
        runTest(dispatcher) {
            val vm = viewModel(skills = emptyArray())

            assertEquals(EarSummary(0, 0, 0), vm.state.value.summary)
        }

    @Test
    fun `an echo item reads the piano and the right keys are first try`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.ECHO_NOTE)
            assertEquals(0, input.open)
            finishPlaying()
            assertEquals(1, input.open)

            playEcho(vm)

            assertEquals(2, vm.state.value.itemNumber)
            assertEquals(0, repository.states().size)
        }

    @Test
    fun `a wrong echo asks to listen again and a second wrong one shows the answer`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.ECHO_NOTE)
            finishPlaying()
            val key =
                vm
                    .echo()
                    .sounds
                    .single()
                    .single()
            val other = if (key.number == 60) 62 else 60

            piano.press(MidiNote(other))
            runCurrent()
            assertEquals(EarFeedback.TRY_AGAIN, vm.state.value.feedback)
            piano.press(MidiNote(other))
            runCurrent()

            assertEquals(
                EarFeedback.REVEALED,
                vm.state.value.answerShown
                    .let { EarFeedback.REVEALED },
            )
            assertEquals(true, vm.state.value.answerShown)
            assertEquals(0, input.open)
        }

    @Test
    fun `an echo of three notes needs all three before it is judged`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.ECHO_THREE)
            finishPlaying()
            val notes = vm.echo().sounds.flatten()

            piano.press(notes[0])
            runCurrent()
            piano.press(notes[1])
            runCurrent()
            assertEquals(EarFeedback.NONE, vm.state.value.feedback)
            assertEquals(1, vm.state.value.itemNumber)
            piano.press(notes[2])
            runCurrent()

            assertEquals(2, vm.state.value.itemNumber)
        }

    @Test
    fun `an answer to an echo item by button is ignored and so is a tap on an identify key`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.ECHO_NOTE)
            vm.answer(EarChoice.HIGHER)
            runCurrent()

            assertEquals(1, vm.state.value.itemNumber)
            assertEquals(EarFeedback.NONE, vm.state.value.feedback)
        }

    @Test
    fun `the piano is closed before leaving, even while it plays`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.ECHO_NOTE)
            finishPlaying()
            assertEquals(1, input.open)
            vm.close()
            assertEquals(0, input.open)

            val before = piano.sent.size
            val playing = viewModel()
            advanceTimeBy(100)
            runCurrent()
            playing.close()
            // The key that was down is released.
            assertEquals(2, piano.sent.size - before)
            assertEquals(0x80, piano.sent.last()[0].toInt() and 0xF0)
            assertEquals(false, playing.state.value.playing)
        }

    @Test
    fun `a replay while listening closes the reader before it opens again`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.ECHO_NOTE)
            finishPlaying()
            vm.replay()
            runCurrent()
            assertEquals(0, input.open)
            finishPlaying()
            assertEquals(1, input.open)
            assertEquals(1, Tunables.EAR_MAX_REPLAYS - vm.state.value.replaysLeft)
        }

    @Test
    fun `the piano going away while listening is not an error`() =
        runTest(dispatcher) {
            val vm = viewModel(EarSkills.ECHO_NOTE)
            finishPlaying()
            piano.unplug()
            runCurrent()

            assertEquals(1, vm.state.value.itemNumber)
        }

    @Test
    fun `next does nothing before an item is missed`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.next()
            runCurrent()

            assertEquals(1, vm.state.value.itemNumber)
        }
}
