package pianoanchor.feature.practice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.guidance.slice
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.engine.matcher.TempoRung
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelHintTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)

    // No MIDI output: a timed run takes the transport default at once instead of measuring the link.
    private val piano = FakePiano(clock).apply { inputOnly = true }
    private val attempts = FakeAttemptRepository()
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val ms = 1_000_000L
    private var ids = 0

    private val opened = mutableListOf<PracticeViewModel>()

    /** Like runTest, but closes every ViewModel afterwards: a timed run's ticker never ends on a fake clock. */
    private fun runHints(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                opened.forEach { it.close() }
            }
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** [bands] stored runs of C D E, oldest first, each with a 300 ms gap between its steps. */
    private suspend fun store(vararg bands: Band) {
        bands.forEachIndexed { i, band ->
            attempts.save(
                Attempt(
                    id = "old-$i",
                    passageId = Passages.CDE.id,
                    deviceId = piano.device.id,
                    startedAt = Instant.fromEpochSeconds(1_000L + i),
                    durationNanos = 600 * ms,
                    stepCount = 3,
                    wrongNotes = 0,
                    incompleteChords = 0,
                    accuracy = 1.0,
                    band = band,
                    events =
                        listOf(
                            StepCorrect(0, 0),
                            StepCorrect(300 * ms, 1),
                            StepCorrect(600 * ms, 2),
                            PassageComplete(600 * ms, 2),
                        ),
                    tempoBpm = null,
                    playedNotes = emptyList(),
                ),
            )
        }
    }

    private fun TestScope.practice(store: AttemptRepositoryPort = attempts): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            piano,
            store,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            LearnerModel(Curriculum.graph, FakeLearnerRepository()),
            TimeZone.UTC,
            FakeSettingsRepository(),
            NoteLabelsUseCase(
                FakeSettingsRepository(),
                attempts,
                LearnerModel(Curriculum.graph, FakeLearnerRepository()),
                TimeZone.UTC,
            ),
        ) {
            "attempt-${++ids}"
        }.also {
            opened += it
            runCurrent()
        }

    /** A spot loop on bar 1 of C D E: its attempts are saved under the piece's id. */
    private fun TestScope.loop(): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            Passages.CDE.slice(BarRange(1, 1)),
            piano,
            piano,
            attempts,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            LearnerModel(Curriculum.graph, FakeLearnerRepository()),
            TimeZone.UTC,
            FakeSettingsRepository(),
            NoteLabelsUseCase(
                FakeSettingsRepository(),
                attempts,
                LearnerModel(Curriculum.graph, FakeLearnerRepository()),
                TimeZone.UTC,
            ),
            TempoRung.WAIT,
            RunMode.PRACTICE,
            BarRange(1, 1),
            pieceId = Passages.CDE.id,
        ) {
            "attempt-${++ids}"
        }.also {
            opened += it
            runCurrent()
        }

    private fun TestScope.tap(
        note: MidiNote,
        gapMs: Long = 300,
    ) {
        clock.advanceNanos(gapMs * ms)
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    private fun TestScope.wait(millis: Long) {
        clock.advanceNanos(millis * ms)
        advanceTimeBy(millis)
        runCurrent()
    }

    /** Like [wait], in 50 ms steps so the clock the cursor reads and the timers' virtual time stay together. */
    private fun TestScope.tick(millis: Long) = repeat((millis / 50).toInt()) { wait(50) }

    private val delayedHistory = arrayOf(Band.CLEAN, Band.CLEAN)
    private val offHistory = arrayOf(Band.CLEAN, Band.CLEAN, Band.CLEAN, Band.CLEAN)

    @Test
    fun aLoopOnAPieceAtOffStartsUnaided() =
        runHints {
            store(*offHistory)
            val vm = loop()

            assertEquals(HintLevel.OFF, vm.state.value.hintLevel)
            assertFalse(vm.state.value.hintVisible)
        }

    @Test
    fun aLoopOnAPieceAtDelayedUsesThePiecesDelay() =
        runHints {
            store(*delayedHistory)
            val vm = loop()

            assertEquals(HintLevel.DELAYED, vm.state.value.hintLevel)
            assertFalse(vm.state.value.hintVisible)
            wait(799)
            assertFalse(vm.state.value.hintVisible)
            wait(1)
            assertTrue(vm.state.value.hintVisible)
        }

    @Test
    fun hintFadingIgnoresSpotLoopsFillingHistory() =
        runHints {
            store(*offHistory)
            repeat(Tunables.HINT_FADE_HISTORY + 1) { i ->
                attempts.save(
                    attempts.get("old-0")!!.copy(
                        id = "loop-$i",
                        startedAt = Instant.fromEpochSeconds(2_000L + i),
                        band = Band.KEEP_PRACTISING,
                        spot = BarRange(1, 1),
                    ),
                )
            }

            assertEquals(HintLevel.OFF, loop().state.value.hintLevel)
        }

    @Test
    fun aLoopOnANewPieceStartsAtFull() =
        runHints {
            val vm = loop()

            assertEquals(HintLevel.FULL, vm.state.value.hintLevel)
            assertTrue(vm.state.value.hintVisible)
        }

    @Test
    fun fullShowsTheBandAtOnceAndRecordsAnAidedRun() =
        runHints {
            val vm = practice()

            assertEquals(HintLevel.FULL, vm.state.value.hintLevel)
            assertTrue(vm.state.value.hintVisible)
            tap(c4)
            assertTrue(vm.state.value.hintVisible)
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.FULL, saved.hintLevel)
            assertTrue(saved.hintShown)
        }

    @Test
    fun delayedShowsTheBandOnlyAfterTheDelayAndMarksTheRunAided() =
        runHints {
            store(*delayedHistory)
            val vm = practice()

            assertEquals(HintLevel.DELAYED, vm.state.value.hintLevel)
            assertFalse(vm.state.value.hintVisible)
            wait(799)
            assertFalse(vm.state.value.hintVisible)
            wait(1)
            assertTrue(vm.state.value.hintVisible)

            tap(c4)
            assertFalse(vm.state.value.hintVisible) // the next step starts without the band
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.DELAYED, saved.hintLevel)
            assertTrue(saved.hintShown)
        }

    @Test
    fun delayedKeyPressedInTimeKeepsTheRunUnaided() =
        runHints {
            store(*delayedHistory)
            val vm = practice()

            tap(c4, gapMs = 500)
            assertFalse(vm.state.value.hintVisible)
            tap(d4, gapMs = 500)
            tap(e4, gapMs = 500)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.DELAYED, saved.hintLevel)
            assertFalse(saved.hintShown)
        }

    @Test
    fun delayedHintDoesNotFireInTheReleaseTailAfterTheLastStep() =
        runHints {
            store(*delayedHistory)
            val vm = practice()
            tap(c4)
            tap(d4)
            clock.advanceNanos(300 * ms)
            piano.press(e4) // held: the run waits in the release tail
            runCurrent()

            wait(900)

            assertFalse(vm.state.value.hintVisible)
        }

    @Test
    fun offShowsNothingUntilTheHintButtonThenForOneStepOnly() =
        runHints {
            store(*offHistory)
            val vm = practice()

            assertEquals(HintLevel.OFF, vm.state.value.hintLevel)
            assertFalse(vm.state.value.hintVisible)
            wait(5_000)
            assertFalse(vm.state.value.hintVisible)

            vm.showHint()
            assertTrue(vm.state.value.hintVisible)
            tap(c4)
            assertFalse(vm.state.value.hintVisible)
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.OFF, saved.hintLevel)
            assertTrue(saved.hintShown)
        }

    @Test
    fun offWithoutTheHintButtonRecordsAnUnaidedRun() =
        runHints {
            store(*offHistory)
            practice()

            tap(c4)
            tap(d4)
            tap(e4)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.OFF, saved.hintLevel)
            assertFalse(saved.hintShown)
        }

    @Test
    fun delayedTimedStepPlayedEarlyGetsNoHintWhenTheCursorReachesIt() =
        runHints {
            store(*delayedHistory)
            val vm = practice()
            vm.selectRung(TempoRung.P60) // 48 BPM: 1250 ms a beat, so the steps are further apart than the delay
            runCurrent()
            val beatMs = 1_250L
            tick(Tunables.TEMPO_AUTO_START_DELAY_MS) // no key: the run counts in by itself
            tick(Tunables.TEMPO_COUNT_IN_LEAD_MS + 4 * beatMs)
            piano.press(c4)
            piano.release(c4)
            runCurrent()
            tick(beatMs - 400)
            piano.press(d4) // 400 ms before D's onset, inside its window
            piano.release(d4)
            runCurrent()

            tick(400 + 1_000) // past D's onset and the delay
            assertEquals(1, vm.state.value.currentStep)
            assertFalse(vm.state.value.hintVisible)

            tick(beatMs - 1_000)
            piano.press(e4)
            piano.release(e4)
            runCurrent()
            tick(4 * beatMs)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(HintLevel.DELAYED, saved.hintLevel)
            assertFalse(saved.hintShown)
        }

    @Test
    fun aPerformTakeOnADelayedPassageShowsNoHintButSavesTheLevel() =
        runHints {
            store(*delayedHistory)
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            vm.selectMode(RunMode.PERFORM)
            runCurrent()
            val beatMs = 1_250L
            tick(Tunables.TEMPO_AUTO_START_DELAY_MS) // no key: the run counts in by itself
            tick(Tunables.TEMPO_COUNT_IN_LEAD_MS + 4 * beatMs)

            listOf(c4, d4, e4).forEach {
                tick(500) // the step waits unplayed (within its window): still no hint in perform
                assertFalse(vm.state.value.hintVisible)
                tap(it, gapMs = 0)
                tick(beatMs - 500)
            }
            tick(3 * beatMs)

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(RunMode.PERFORM, saved.mode)
            assertEquals(HintLevel.DELAYED, saved.hintLevel)
            assertFalse(saved.hintShown)
        }

    @Test
    fun anUnreadableStoreStartsAtFull() =
        runHints {
            store(*offHistory)
            attempts.failing = true

            val vm = practice()

            assertEquals(HintLevel.FULL, vm.state.value.hintLevel)
            assertTrue(vm.state.value.hintVisible)
        }

    /** [attempts] behind a slow store: each `recent` answers only once the gate [opened] returns completes. */
    private fun slowStore(opened: () -> CompletableDeferred<Unit>): AttemptRepositoryPort =
        object : AttemptRepositoryPort by attempts {
            override fun recent(
                passageId: String,
                limit: Int,
            ): Flow<List<Attempt>> =
                flow {
                    opened().await()
                    emitAll(attempts.recent(passageId, limit))
                }
        }

    @Test
    fun theBandStaysHiddenUntilADelayedLevelIsRead() =
        runHints {
            store(*delayedHistory)
            val opened = CompletableDeferred<Unit>()
            val vm = practice(slowStore { opened })

            assertFalse(vm.state.value.hintVisible) // no FULL flash while the store is slow

            opened.complete(Unit)
            runCurrent()
            assertEquals(HintLevel.DELAYED, vm.state.value.hintLevel)
            assertFalse(vm.state.value.hintVisible)
        }

    @Test
    fun theBandShowsOnceAFullLevelIsRead() =
        runHints {
            val opened = CompletableDeferred<Unit>()
            val vm = practice(slowStore { opened })

            assertFalse(vm.state.value.hintVisible)

            opened.complete(Unit)
            runCurrent()
            assertEquals(HintLevel.FULL, vm.state.value.hintLevel)
            assertTrue(vm.state.value.hintVisible)
        }

    @Test
    fun aRestartHidesTheBandUntilTheLevelIsReadAgain() =
        runHints {
            store(*delayedHistory)
            var gate = CompletableDeferred(Unit)
            val vm = practice(slowStore { gate })
            assertEquals(HintLevel.DELAYED, vm.state.value.hintLevel)
            wait(800)
            assertTrue(vm.state.value.hintVisible) // the delayed band is up

            gate = CompletableDeferred()
            vm.restart()
            runCurrent()

            assertFalse(vm.state.value.hintVisible)
        }

    /** [attempts] that counts how often `recent` is collected. */
    private fun countingStore(reads: () -> Unit): AttemptRepositoryPort =
        object : AttemptRepositoryPort by attempts {
            override fun recent(
                passageId: String,
                limit: Int,
            ): Flow<List<Attempt>> =
                flow {
                    reads()
                    emitAll(attempts.recent(passageId, limit))
                }
        }

    @Test
    fun restartsReuseTheHintLevelOfTheFirstRead() =
        runHints {
            store(*delayedHistory)
            var reads = 0
            val vm = practice(countingStore { reads++ })
            runCurrent()

            vm.restart()
            runCurrent()
            vm.selectRung(TempoRung.P60)
            runCurrent()

            assertEquals(1, reads)
            assertEquals(HintLevel.DELAYED, vm.state.value.hintLevel)
        }

    @Test
    fun anUnreadableStoreIsReadAgainOnTheNextStart() =
        runHints {
            store(*delayedHistory)
            var reads = 0
            attempts.failing = true
            val vm = practice(countingStore { reads++ })
            runCurrent()
            assertEquals(HintLevel.FULL, vm.state.value.hintLevel)

            attempts.failing = false
            vm.restart()
            runCurrent()

            assertEquals(2, reads)
            assertEquals(HintLevel.DELAYED, vm.state.value.hintLevel)
        }

    @Test
    fun aFailedSaveMakesTheNextStartReadTheHistoryAgain() =
        runHints {
            var reads = 0
            val vm = practice(countingStore { reads++ })
            tap(c4)
            tap(d4)
            attempts.failing = true // the save fails, though a real store may have kept the run
            tap(e4)
            assertTrue(vm.state.value.saveFailed)

            attempts.failing = false
            vm.discardRun()
            runCurrent()

            assertEquals(2, reads)
        }
}
