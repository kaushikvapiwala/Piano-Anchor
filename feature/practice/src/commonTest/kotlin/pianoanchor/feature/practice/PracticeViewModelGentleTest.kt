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
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.guidance.Direction
import pianoanchor.engine.guidance.Landmark
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
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
class PracticeViewModelGentleTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)

    // Not listed as an output: a timed run takes the transport default at once instead of measuring the link.
    private val noOutput = FakePiano(clock, MidiDevice("no-output", "No output"))
    private val attempts = FakeAttemptRepository()
    private val tone = FakeTone()
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val f4 = MidiNote(65)
    private val ms = 1_000_000L
    private var ids = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.practice(
        passage: Passage = plain,
        player: PassagePlayer? = null,
        rung: TempoRung = TempoRung.WAIT,
        store: AttemptRepositoryPort = attempts,
    ): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            passage,
            piano,
            noOutput,
            store,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            LearnerModel(Curriculum.graph, FakeLearnerRepository()),
            TimeZone.UTC,
            FakeSettingsRepository(),
            rung,
            player = player,
        ) {
            "attempt-${++ids}"
        }.also { runCurrent() }

    /** C D E outside Unit 0's skills, so it keeps its staff. */
    private val plain = Passages.CDE.copy(skills = setOf("other"))

    private fun player() = PassagePlayer(piano.device, piano, tone)

    private suspend fun storeRun(
        passageId: String,
        index: Int = 0,
    ) = attempts.save(
        Attempt(
            id = "old-$index",
            passageId = passageId,
            deviceId = piano.device.id,
            startedAt = Instant.fromEpochSeconds(1_000L + index),
            durationNanos = 600 * ms,
            stepCount = 3,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = listOf(StepCorrect(0, 0), PassageComplete(600 * ms, 0)),
            tempoBpm = null,
            playedNotes = emptyList(),
        ),
    )

    private fun TestScope.tap(note: MidiNote) {
        clock.advanceNanos(300 * ms)
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    @Test
    fun aWrongKeyInWaitModeFlashesAndGlowsTheRightKeyWithAHint() =
        runTest(dispatcher) {
            val vm = practice()

            tap(f4)

            val state = vm.state.value
            assertEquals(KeyFlash(f4, correct = false), state.flash)
            assertEquals(setOf(c4), state.glowKeys)
            val hint = assertNotNull(state.hint)
            assertEquals(Landmark.LEFT_OF_TWO, hint.landmark)
            assertEquals(Direction.LEFT, hint.direction)
            assertEquals(65, hint.played)
            assertEquals(60, hint.expected)
        }

    @Test
    fun theRightKeyEndsTheGlowAndTheHint() =
        runTest(dispatcher) {
            val vm = practice()
            tap(f4)

            tap(c4)

            assertNull(vm.state.value.hint)
            assertEquals(emptySet(), vm.state.value.glowKeys)
        }

    @Test
    fun theHintNamesTheNearestKeyStillToPlayInAChord() =
        runTest(dispatcher) {
            val vm = practice(plain.copy(steps = listOf(chord(c4, e4))))
            tap(f4)
            assertEquals(
                64,
                vm.state.value.hint
                    ?.expected,
            )
            assertEquals(setOf(c4, e4), vm.state.value.glowKeys)

            clock.advanceNanos(10 * ms)
            piano.press(e4)
            runCurrent()
            assertEquals(setOf(c4), vm.state.value.glowKeys)
            assertNotNull(vm.state.value.hint)

            clock.advanceNanos(10 * ms)
            piano.press(c4)
            runCurrent()
            assertNull(vm.state.value.hint)
        }

    @Test
    fun aKeyAlreadyHeldIsNotTheOneThePointerFinds() =
        runTest(dispatcher) {
            val g4 = MidiNote(67)
            val vm = practice(plain.copy(steps = listOf(chord(c4, g4))))
            piano.press(c4) // held: correct
            runCurrent()
            piano.press(d4) // wrong: C is nearer but already down, so the hint points to G
            runCurrent()

            assertEquals(
                67,
                vm.state.value.hint
                    ?.expected,
            )
        }

    @Test
    fun aWrongKeyInATimedRunFlashesOnly() =
        runTest(dispatcher) {
            val vm = practice(rung = TempoRung.P60)
            advanceTimeBy(Tunables.TEMPO_AUTO_START_DELAY_MS) // the run arms itself and begins its count-in
            runCurrent()
            clock.advanceNanos(5_000 * ms) // virtual time stays put: the run's clock does not tick on
            tap(MidiNote(70))

            val state = vm.state.value
            assertEquals(KeyFlash(MidiNote(70), correct = false), state.flash)
            assertNull(state.hint)
            assertEquals(emptySet(), state.glowKeys)
            vm.close() // the clock of the run would tick on in virtual time
        }

    @Test
    fun theFirstOpenPlaysThePassageBeforeReadyAndSkipStopsIt() =
        runTest(dispatcher) {
            val vm = practice(player = player())

            assertTrue(vm.state.value.listening)
            assertFalse(vm.state.value.showsListen)
            advanceTimeBy(1)
            runCurrent()
            assertTrue(piano.sent.isNotEmpty())
            assertEquals(0, vm.state.value.demoStep)
            assertEquals(setOf(c4), vm.state.value.demoKeys)
            assertEquals(
                0,
                vm.state.value.shownSteps
                    .indexOfFirst { it.status == StepStatus.WAITING },
            )

            vm.skipListening()
            runCurrent()

            assertFalse(vm.state.value.listening)
            assertNull(vm.state.value.demoStep)
            assertEquals(emptySet(), vm.state.value.demoKeys)
            tap(c4) // the run reads the piano now
            assertEquals(1, vm.state.value.currentStep)
        }

    @Test
    fun theDemoEndsOnItsOwnAndTheRunBegins() =
        runTest(dispatcher) {
            val vm = practice(player = player())

            advanceTimeBy(30_000)
            runCurrent()

            assertFalse(vm.state.value.listening)
            assertTrue(vm.state.value.showsListen)
            tap(c4)
            assertEquals(1, vm.state.value.currentStep)
        }

    @Test
    fun aPassageAlreadyPlayedDoesNotPlayItself() =
        runTest(dispatcher) {
            storeRun(plain.id)
            val vm = practice(player = player())

            assertFalse(vm.state.value.listening)
            assertTrue(piano.sent.isEmpty())
            assertTrue(vm.state.value.showsListen)
        }

    @Test
    fun aRestartDoesNotPlayTheDemoAgain() =
        runTest(dispatcher) {
            val vm = practice(player = player())
            vm.skipListening()
            runCurrent()
            val sent = piano.sent.size

            vm.restart()
            runCurrent()

            assertFalse(vm.state.value.listening)
            assertEquals(sent, piano.sent.size)
        }

    @Test
    fun anUnreadableStoreDoesNotMakeTheLearnerWait() =
        runTest(dispatcher) {
            val broken =
                object : AttemptRepositoryPort by attempts {
                    override suspend fun lastPlayed(passageId: String): Instant? =
                        throw StorageUnavailableException("x")
                }
            val vm = practice(player = player(), store = broken)

            assertFalse(vm.state.value.listening)
        }

    @Test
    fun listenPlaysThePassageAgainWhileNoRunIsUnderWay() =
        runTest(dispatcher) {
            storeRun(plain.id)
            val vm = practice(player = player())

            vm.listen()
            runCurrent()
            assertTrue(vm.state.value.listening)
            vm.listen() // already playing: ignored
            advanceTimeBy(30_000)
            runCurrent()

            assertFalse(vm.state.value.listening)
            assertEquals(6, piano.sent.size) // three notes, each on and off
            tap(c4)
            assertFalse(vm.state.value.showsListen)
            vm.listen()
            runCurrent()
            assertFalse(vm.state.value.listening)
        }

    @Test
    fun listenGoesToTheSpeakerWhenThePianoCannotPlay() =
        runTest(dispatcher) {
            storeRun(plain.id)
            piano.inputOnly = true
            val vm = practice(player = player())

            vm.listen()
            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(1, tone.played.size)
            assertEquals(listOf(0L, 750L, 1500L), tone.played.single().map { it.startMs })
        }

    @Test
    fun aPianoThatFailsMidSendMovesPlaybackToTheSpeakerForGood() =
        runTest(dispatcher) {
            storeRun(plain.id)
            piano.failSends = true
            val vm = practice(player = player())

            vm.listen()
            advanceTimeBy(30_000)
            runCurrent()
            vm.skipListening() // nothing to stop
            assertEquals(1, tone.played.size)
            assertFalse(vm.state.value.listening)
            tone.played.size.let { assertEquals(1, it) }
        }

    @Test
    fun withoutAPlayerThereIsNothingToListenTo() =
        runTest(dispatcher) {
            val vm = practice()

            vm.listen()
            runCurrent()

            assertFalse(vm.state.value.canListen)
            assertFalse(vm.state.value.listening)
        }

    @Test
    fun aUnitZeroPassageHidesTheStaffAndGlowsTheNextKeyAtHintOff() =
        runTest(dispatcher) {
            val unit0 =
                Curriculum.units
                    .first { it.id == "unit-0" }
                    .passages
                    .first()
            repeat(4) { storeRun(unit0.id, it) }
            val vm = practice(unit0)

            assertEquals(HintLevel.OFF, vm.state.value.hintLevel)
            assertTrue(vm.state.value.staffHidden)
            assertEquals(unit0.steps.first().pitches, vm.state.value.glowKeys)
        }

    @Test
    fun otherPassagesKeepTheirStaff() =
        runTest(dispatcher) {
            val vm = practice()

            assertFalse(vm.state.value.staffHidden)
            assertEquals(emptySet(), vm.state.value.glowKeys)
        }

    @Test
    fun theCountInShowsItsLastThreeBeatsAsNumeralsThenPlay() =
        runTest(dispatcher) {
            val vm = practice(rung = TempoRung.P60)
            tap(c4) // the starting key; 4/4 at the rung's tempo
            val seen = mutableListOf<Int?>()
            var playNow = false
            repeat(400) {
                clock.advanceNanos(25 * ms)
                advanceTimeBy(25)
                runCurrent()
                val state = vm.state.value
                if (seen.lastOrNull() != state.countInNumeral) seen += state.countInNumeral
                if (state.playNow && state.phase == RunPhase.PLAYING) playNow = true
            }

            vm.close()
            assertEquals(listOf(3, 2, 1, null), seen.take(4))
            assertTrue(playNow)
            assertFalse(vm.state.value.playNow)
        }

    @Test
    fun theHintFollowsWhatTheMatcherStillNeedsWhenItForgetsAChordPress() =
        runTest(dispatcher) {
            val g4 = MidiNote(67)
            val vm = practice(plain.copy(steps = listOf(chord(c4, g4))))
            clock.advanceNanos(300 * ms)
            piano.press(c4) // held and correct
            runCurrent()
            clock.advanceNanos(10 * ms)
            piano.press(d4) // wrong: only G is needed
            runCurrent()
            assertEquals(
                67,
                vm.state.value.hint
                    ?.expected,
            )

            clock.advanceNanos(100 * ms) // past the chord window: the matcher forgets C
            piano.press(g4)
            runCurrent()

            assertEquals(
                60,
                vm.state.value.hint
                    ?.expected,
            )
            assertEquals(setOf(c4), vm.state.value.glowKeys)
        }

    @Test
    fun aHintDoesNotOutliveItsStep() =
        runTest(dispatcher) {
            val g4 = MidiNote(67)
            val vm = practice(plain.copy(steps = listOf(chord(c4, g4), step(e4, 960))))
            clock.advanceNanos(300 * ms)
            piano.press(c4)
            runCurrent()
            clock.advanceNanos(10 * ms)
            piano.press(d4)
            runCurrent()
            assertNotNull(vm.state.value.hint)

            clock.advanceNanos(10 * ms)
            piano.press(g4) // completes the chord: the step moves on
            runCurrent()

            assertEquals(1, vm.state.value.currentStep)
            assertNull(vm.state.value.hint)
            assertEquals(emptySet(), vm.state.value.glowKeys)
        }

    @Test
    fun aKeyPressedWhileTheDemoPlaysEndsTheDemoAndHidesListen() =
        runTest(dispatcher) {
            storeRun(plain.id)
            val vm = practice(player = player())
            vm.listen()
            runCurrent()
            assertTrue(vm.state.value.listening)

            tap(f4)
            runCurrent()

            assertFalse(vm.state.value.listening)
            assertNull(vm.state.value.demoStep)
            assertFalse(vm.state.value.showsListen)
            tap(c4)
            assertEquals(1, vm.state.value.currentStep)
        }

    @Test
    fun unitZeroGlowsOnlyWhileARunIsUnderWay() {
        val unit0 = PracticeState.start(Passages.CDE).copy(staffHidden = true)
        val first = setOf(c4)
        assertEquals(first, unit0.glowKeys)
        assertEquals(emptySet(), unit0.copy(listening = true).glowKeys)
        val timed = unit0.copy(rung = TempoRung.P60)
        assertEquals(emptySet(), timed.glowKeys)
        assertEquals(emptySet(), timed.copy(phase = RunPhase.COUNT_IN).glowKeys)
        assertEquals(first, timed.copy(phase = RunPhase.PLAYING).glowKeys)
    }

    private fun step(
        note: MidiNote,
        onset: Int,
    ) = pianoanchor.core.model.Step(onset, setOf(pianoanchor.core.model.ScoreNote(note, 960)))

    private fun chord(vararg notes: MidiNote) =
        pianoanchor.core.model.Step(
            0,
            notes.mapTo(LinkedHashSet()) { pianoanchor.core.model.ScoreNote(it, 960) },
        )
}
