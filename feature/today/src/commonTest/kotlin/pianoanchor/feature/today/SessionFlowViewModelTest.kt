package pianoanchor.feature.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.Session
import pianoanchor.engine.session.SessionStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionFlowViewModelTest : TodayViewModelFixture() {
    private val passage = checkNotNull(Curriculum.passage("builtin:middle-c-march"))
    private val lesson = Card.Lesson(passage, TempoRung.WAIT, 5, Skills.HAND_SHAPE, emptyList())
    private val song = Card.Song(passage, TempoRung.WAIT, 6)
    private val ear = Card.Ear(emptyList(), 2)
    private val session = Session(listOf(lesson, song, ear), 0, 5, 6, 2, emptyList())

    private fun TestScope.flow(stored: SessionProgress = progress): SessionFlowViewModel =
        SessionFlowViewModel(
            LearnerModel(Curriculum.graph, learnerRepository),
            settings,
            attempts,
            piano,
            clock,
            TimeZone.UTC,
            stored,
        ).also { runCurrent() }

    private fun TestScope.opened(vm: SessionFlowViewModel): MutableList<TodayTarget> {
        val targets = mutableListOf<TodayTarget>()
        backgroundScope.launch(dispatcher) { vm.opens.collect { targets += it } }
        runCurrent()
        return targets
    }

    private fun TestScope.tapPedal() {
        piano.play(byteArrayOf(0xB0.toByte(), 64, 127))
        piano.play(byteArrayOf(0xB0.toByte(), 64, 0))
        runCurrent()
    }

    private fun TestScope.started(
        vm: SessionFlowViewModel,
        skipped: Set<Int> = emptySet(),
    ) {
        vm.start(session, skipped)
        runCurrent()
    }

    @Test
    fun `start opens the first step on the piano`() =
        runTest(dispatcher) {
            val vm = flow()
            val targets = opened(vm)

            started(vm)

            assertEquals(listOf<TodayTarget>(TodayTarget.Lesson(piano.device, Skills.HAND_SHAPE.id)), targets)
            assertEquals(3, vm.state.value.steps.size)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `a song with chords runs its play-along after practise, with the backing on`() =
        runTest(dispatcher) {
            val chordSong = Card.Song(checkNotNull(Curriculum.passage("builtin:hot-cross-buns")), TempoRung.WAIT, 8)
            val vm = flow()
            val targets = opened(vm)

            vm.start(Session(listOf(chordSong), 0, 0, 8, 0, emptyList()), emptySet())
            runCurrent()
            assertEquals(
                listOf(SessionStep.Practise(chordSong, 4), SessionStep.PlayAlong(chordSong, 4)),
                vm.state.value.steps,
            )
            vm.stepFinished()
            runCurrent()
            assertEquals(SessionStep.PlayAlong(chordSong, 4), assertNotNull(vm.state.value.between).next)
            vm.goNow()
            runCurrent()

            assertEquals(listOf(false, true), targets.map { assertIs<TodayTarget.Practice>(it).backing })
            vm.exit()
            runCurrent()
        }

    @Test
    fun `finishing practise leaves the play-along to do on a restart`() =
        runTest(dispatcher) {
            val chordSong = Card.Song(checkNotNull(Curriculum.passage("builtin:hot-cross-buns")), TempoRung.WAIT, 8)
            val chordSession = Session(listOf(chordSong), 0, 0, 8, 0, emptyList())
            val vm = flow()
            vm.start(chordSession, emptySet())
            runCurrent()
            vm.stepFinished()
            runCurrent()
            vm.exit()
            runCurrent()

            vm.start(chordSession, emptySet())
            runCurrent()

            assertEquals(listOf<SessionStep>(SessionStep.PlayAlong(chordSong, 4)), vm.state.value.steps)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `with no piano the first step opens Pianos`() =
        runTest(dispatcher) {
            piano.unplug()
            val vm = flow()
            val targets = opened(vm)

            started(vm)
            vm.stepFinished()
            runCurrent()

            assertEquals(listOf<TodayTarget>(TodayTarget.Pianos), targets)
            assertNull(vm.state.value.between)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `a finished step shows the between card with the countdown and the pedal hint`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm)

            vm.stepFinished()
            runCurrent()

            val between = assertNotNull(vm.state.value.between)
            assertEquals(SessionStep.Practise(song, 6), between.next)
            assertEquals(Tunables.SESSION_NEXT_COUNTDOWN_S, between.secondsLeft)
            assertEquals(false, between.paused)
            assertTrue(vm.state.value.pedalHint)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `the countdown reaching zero opens the next step`() =
        runTest(dispatcher) {
            val vm = flow()
            val targets = opened(vm)
            started(vm)
            vm.stepFinished()
            runCurrent()

            advanceTimeBy(Tunables.SESSION_NEXT_COUNTDOWN_S * 1_000L - 1)
            assertEquals(1, assertNotNull(vm.state.value.between).secondsLeft)
            assertEquals(1, targets.size)
            advanceTimeBy(2)
            runCurrent()

            assertEquals(2, targets.size)
            assertIs<TodayTarget.Practice>(targets.last())
            assertNull(vm.state.value.between)
            assertEquals(1, vm.state.value.current)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `pausing freezes the seconds and resuming goes on`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm)
            vm.stepFinished()
            runCurrent()
            advanceTimeBy(3_500)

            vm.pauseCountdown()
            vm.pauseCountdown()
            val frozen = assertNotNull(vm.state.value.between)
            advanceTimeBy(30_000)

            assertEquals(Tunables.SESSION_NEXT_COUNTDOWN_S - 3, frozen.secondsLeft)
            assertTrue(frozen.paused)
            assertEquals(frozen, vm.state.value.between)

            vm.resumeCountdown()
            vm.resumeCountdown()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(frozen.secondsLeft - 1, assertNotNull(vm.state.value.between).secondsLeft)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `pause and resume with no between card do nothing`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm)

            vm.pauseCountdown()
            vm.resumeCountdown()

            assertNull(vm.state.value.between)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `skipping drops the next step and shows the one after`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm)
            vm.stepFinished()
            runCurrent()

            vm.skipStep()
            runCurrent()

            val state = vm.state.value
            assertEquals(listOf<SessionStep>(SessionStep.Learn(lesson, 5), SessionStep.Ear(ear, 2)), state.steps)
            assertEquals(SessionStep.Ear(ear, 2), assertNotNull(state.between).next)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `skipping the last step goes to the recap`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(2))
            vm.stepFinished()
            runCurrent()

            vm.skipStep()
            runCurrent()

            assertNull(vm.state.value.between)
            assertEquals(1, assertNotNull(vm.state.value.recap).stepsDone)
            vm.skipStep()
        }

    @Test
    fun `go now opens the next step at once and a second tap does nothing`() =
        runTest(dispatcher) {
            val vm = flow()
            val targets = opened(vm)
            started(vm)
            vm.stepFinished()
            runCurrent()

            vm.goNow()
            vm.goNow()
            runCurrent()

            assertEquals(2, targets.size)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `a pedal tap on the between card goes now`() =
        runTest(dispatcher) {
            val vm = flow()
            val targets = opened(vm)
            started(vm)
            vm.stepFinished()
            runCurrent()

            tapPedal()

            assertEquals(2, targets.size)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `the piano is not read with the pedal control off`() =
        runTest(dispatcher) {
            settings.update { it.copy(pedalControlOn = false) }
            val vm = flow()
            val targets = opened(vm)
            started(vm)
            vm.stepFinished()
            runCurrent()

            tapPedal()

            assertEquals(1, targets.size)
            assertEquals(false, vm.state.value.pedalHint)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `an unreadable setting leaves the pedal off`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm)
            settings.failing = true
            vm.stepFinished()
            runCurrent()

            assertEquals(false, vm.state.value.pedalHint)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `a pedal reader whose piano is unplugged ends quietly`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm)
            vm.stepFinished()
            runCurrent()

            piano.unplug()
            runCurrent()

            assertNotNull(vm.state.value.between)
            vm.exit()
            runCurrent()
        }

    @Test
    fun `the last step leads to the recap with minutes and steps done`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(1, 2))
            clock.advance(12.minutes + 20.seconds)

            vm.stepFinished()
            vm.stepFinished()
            runCurrent()

            val recap = assertNotNull(vm.state.value.recap)
            assertEquals(12, recap.minutes)
            assertEquals(1, recap.stepsDone)
            assertTrue(recap.learned.isEmpty())
            assertNull(recap.tip)
            assertNull(vm.state.value.between)
            assertTrue(progress.completedOn(today))
        }

    @Test
    fun `the recap names the skills learned during the run, at most three`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(1, 2))
            givenStates(
                skillState(Skills.MIDDLE_C.id, learned = true),
                skillState(Skills.HAND_SHAPE.id, learned = true),
                skillState(Skills.STEADY_BEAT.id, learned = true),
                skillState(Skills.TREBLE_CLEF.id, learned = true),
            )

            vm.stepFinished()
            runCurrent()

            assertEquals(3, assertNotNull(vm.state.value.recap).learned.size)
        }

    @Test
    fun `an unreadable learner store leaves the recap without skills`() =
        runTest(dispatcher) {
            learnerRepository.failing = true
            val vm = flow()
            started(vm, skipped = setOf(1, 2))

            vm.stepFinished()
            runCurrent()

            assertTrue(assertNotNull(vm.state.value.recap).learned.isEmpty())
        }

    private suspend fun playedDuringRun(
        mistakes: Int,
        at: Instant = clock.now(),
        band: Band = Band.KEEP_PRACTISING,
    ) = attempts.save(
        Attempt(
            id = "run-$mistakes",
            passageId = passage.id,
            deviceId = "usb-1",
            startedAt = at,
            durationNanos = 1,
            stepCount = 1,
            wrongNotes = mistakes,
            incompleteChords = 0,
            accuracy = 0.5,
            band = band,
            events = List(mistakes) { WrongNote(it.toLong(), stepIndex = 2, note = MidiNote(61)) },
            tempoBpm = null,
            playedNotes = emptyList(),
        ),
    )

    @Test
    fun `the tip names the worst bars of the weakest practice run`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(0, 2))
            playedDuringRun(mistakes = 3)

            vm.stepFinished()
            runCurrent()

            val tip = assertNotNull(assertNotNull(vm.state.value.recap).tip)
            assertEquals(passage.title, tip.passageTitle)
            assertTrue(tip.bars.first >= 1 && tip.bars.first <= tip.bars.last)
        }

    @Test
    fun `a clean run or one from before the session gives no tip`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(0, 2))
            playedDuringRun(mistakes = 3, band = Band.CLEAN)
            vm.stepFinished()
            runCurrent()
            assertNull(assertNotNull(vm.state.value.recap).tip)

            val older = flow()
            started(older, skipped = setOf(0, 2))
            playedDuringRun(mistakes = 2, at = now - 1.minutes)
            older.stepFinished()
            runCurrent()
            assertNull(assertNotNull(older.state.value.recap).tip)
        }

    @Test
    fun `an unreadable settings store at the recap gives no tip`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(0, 2))
            playedDuringRun(mistakes = 3)
            settings.failing = true

            vm.stepFinished()
            runCurrent()

            assertNull(assertNotNull(vm.state.value.recap).tip)
        }

    @Test
    fun `a restart resumes after the steps stored as finished`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm)
            vm.stepFinished()
            runCurrent()
            vm.exit()
            runCurrent()

            val restarted = flow(SessionProgress(settings))
            val targets = opened(restarted)
            started(restarted)

            assertEquals(listOf(SessionStep.Practise(song, 6), SessionStep.Ear(ear, 2)), restarted.state.value.steps)
            assertIs<TodayTarget.Practice>(targets.single())
            restarted.exit()
            runCurrent()
        }

    @Test
    fun `a restart after the recap runs the whole session again`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(1, 2))
            vm.stepFinished()
            runCurrent()

            val restarted = flow(SessionProgress(settings))
            started(restarted)

            assertEquals(3, restarted.state.value.steps.size)
            restarted.exit()
            runCurrent()
        }

    @Test
    fun `exit keeps the finished steps done and a new start resumes after them`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm)
            vm.stepFinished()
            runCurrent()

            vm.exit()
            runCurrent()

            assertTrue(
                vm.state.value.steps
                    .isEmpty(),
            )
            val again = flow()
            val targets = opened(again)
            started(again)
            assertEquals(listOf(SessionStep.Practise(song, 6), SessionStep.Ear(ear, 2)), again.state.value.steps)
            assertIs<TodayTarget.Practice>(targets.single())
            again.exit()
            runCurrent()
        }

    @Test
    fun `starting with every step done shows the recap`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(0, 1, 2))

            assertEquals(0, assertNotNull(vm.state.value.recap).stepsDone)
        }

    @Test
    fun `stepFinished with no step running is ignored`() =
        runTest(dispatcher) {
            val vm = flow()

            vm.stepFinished()
            runCurrent()

            assertNull(vm.state.value.between)
            assertNull(vm.state.value.recap)
            assertTrue(piano.inputDevices.first().isNotEmpty())
        }

    @Test
    fun `starting again after a completed session runs the whole session`() =
        runTest(dispatcher) {
            val vm = flow()
            started(vm, skipped = setOf(1, 2))
            vm.stepFinished()
            runCurrent()
            assertTrue(progress.completedOn(today))

            val again = flow()
            val targets = opened(again)
            started(again)

            assertEquals(3, again.state.value.steps.size)
            assertNull(again.state.value.recap)
            assertIs<TodayTarget.Lesson>(targets.single())
            again.exit()
            runCurrent()
        }

    @Test
    fun `skipping the last step with a second left goes to the recap without a stray tick`() =
        runTest(dispatcher) {
            val vm = flow()
            val targets = opened(vm)
            started(vm, skipped = setOf(2))
            vm.stepFinished()
            runCurrent()
            advanceTimeBy(Tunables.SESSION_NEXT_COUNTDOWN_S * 1_000L - 500)

            vm.skipStep()
            advanceTimeBy(5_000)
            runCurrent()

            assertNotNull(vm.state.value.recap)
            assertNull(vm.state.value.between)
            assertEquals(1, targets.size)
        }
}
