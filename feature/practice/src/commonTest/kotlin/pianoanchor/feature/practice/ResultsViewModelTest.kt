package pianoanchor.feature.practice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.Strictness
import pianoanchor.core.model.WrongNote
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.content.Skills
import pianoanchor.engine.grading.NoteValue
import pianoanchor.engine.grading.Observation
import pianoanchor.engine.grading.ResultsWords
import pianoanchor.engine.grading.Tip
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakeSettingsRepository
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

private const val MS = 1_000_000L
private const val C4 = 60
private const val D4 = 62
private const val E4 = 64

/** Holds dispatched blocks until [runQueued]: shows which work went to the analysis dispatcher. */
private class HeldDispatcher : CoroutineDispatcher() {
    val queued = ArrayDeque<Runnable>()

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        queued.add(block)
    }

    fun runQueued() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val attempts = FakeAttemptRepository()
    private val settings = FakeSettingsRepository()
    private val learnerRepository = FakeLearnerRepository()
    private val learner = LearnerModel(Curriculum.graph, learnerRepository)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun attempt(
        id: String,
        startedAtSeconds: Long,
        passageId: String = Passages.CDE.id,
        wrongNotes: Int = 0,
        band: Band = Band.CLEAN,
        tempoBpm: Int? = null,
        playedNotes: List<PlayedNote> = emptyList(),
        latencyOffsetNanos: Long? = null,
        mode: RunMode = RunMode.PRACTICE,
        events: List<MatchEvent> = emptyList(),
    ) = Attempt(
        id = id,
        passageId = passageId,
        deviceId = "usb-1",
        startedAt = Instant.fromEpochSeconds(startedAtSeconds),
        durationNanos = 2_000_000_000,
        stepCount = 3,
        wrongNotes = wrongNotes,
        incompleteChords = 0,
        accuracy = 3.0 / (3 + wrongNotes),
        band = band,
        events = events,
        tempoBpm = tempoBpm,
        playedNotes = playedNotes,
        latencyOffsetNanos = latencyOffsetNanos,
        mode = mode,
    )

    private fun played(
        pitch: Int,
        downMs: Long,
        lengthMs: Long,
    ) = PlayedNote(MidiNote(pitch), 70, downMs * MS, (downMs + lengthMs) * MS, (downMs + lengthMs) * MS)

    private suspend fun TestScope.results(
        id: String,
        vararg saved: Attempt,
        rung: TempoRung = TempoRung.WAIT,
    ): ResultsViewModel {
        saved.forEach { attempts.save(it) }
        return ResultsViewModel(id, rung, attempts, settings, learner, dispatcher).also { runCurrent() }
    }

    // 60 BPM is 1000 ms a beat: Standard's band is max(60 ms, 125 ms) = 125 ms, Gentle's max(90 ms, 187.5 ms).
    private fun lateRun() =
        attempt(
            "a-1",
            100,
            tempoBpm = 60,
            band = Band.GOOD,
            playedNotes = listOf(played(C4, 150, 900), played(D4, 1150, 900), played(E4, 2150, 1900)),
        )

    @Test
    fun `notes late within Gentle's band show no timing finding or loop at Gentle`() =
        runTest(dispatcher) {
            settings.update { it.copy(strictness = Strictness.GENTLE) }
            val state = assertIs<ResultsState.Ready>(results("a-1", lateRun()).state.value)

            assertNull(state.timing?.worst)
            assertNull(state.spot)
        }

    @Test
    fun `the same late notes show timing findings and a loop at Standard`() =
        runTest(dispatcher) {
            val state = assertIs<ResultsState.Ready>(results("a-1", lateRun()).state.value)

            assertNotNull(state.timing?.worst)
            assertNotNull(state.spot)
        }

    @Test
    fun `an unreadable settings store analyses with Standard`() =
        runTest(dispatcher) {
            settings.update { it.copy(strictness = Strictness.GENTLE) }
            settings.failing = true
            val state = assertIs<ResultsState.Ready>(results("a-1", lateRun()).state.value)

            assertNotNull(state.timing?.worst)
        }

    @Test
    fun aSavedAttemptIsReadyWithItsFields() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1", 100, wrongNotes = 1, band = Band.GOOD))

            assertEquals(
                ResultsState.Ready(
                    passageTitle = "C-D-E",
                    runNumber = 1,
                    band = Band.GOOD,
                    correctSteps = 3,
                    stepCount = 3,
                    wrongNotes = 1,
                    incompleteChords = 0,
                    words = ResultsWords(listOf(Observation.WRONG_NOTES), Tip.SLOW_DOWN),
                ),
                vm.state.value,
            )
        }

    @Test
    fun `state carries the words and the shift of a late start`() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 1700, 900), played(D4, 2700, 900), played(E4, 3700, 900))
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 60, band = Band.GOOD, playedNotes = notes))

            val state = assertIs<ResultsState.Ready>(vm.state.value)
            assertEquals(Observation.STARTED_LATE, state.words.observations.first())
            assertEquals(Tip.WAIT_FOR_COUNT_IN, state.words.tip)
            assertEquals(1.5, state.shiftBeats)
        }

    @Test
    fun `words of a wait-mode run with wrong notes and of a clean run`() =
        runTest(dispatcher) {
            val slip = listOf(WrongNote(0, 1, MidiNote(C4)))
            val wrong = results("a-1", attempt("a-1", 100, wrongNotes = 1, band = Band.GOOD, events = slip))
            assertEquals(
                ResultsWords(listOf(Observation.WRONG_NOTES), Tip.SLOW_DOWN),
                assertIs<ResultsState.Ready>(wrong.state.value).words,
            )
            val clean = results("a-2", attempt("a-2", 101))
            assertEquals(
                ResultsWords(listOf(Observation.CLEAN), Tip.NEXT_TEMPO),
                assertIs<ResultsState.Ready>(clean.state.value).words,
            )
        }

    @Test
    fun `offers the worst window`() =
        runTest(dispatcher) {
            val slip = listOf(WrongNote(0, 1, MidiNote(C4)))
            val vm = results("a-1", attempt("a-1", 100, wrongNotes = 1, band = Band.GOOD, events = slip))

            val state = assertIs<ResultsState.Ready>(vm.state.value)
            assertEquals(BarRange(1, 1), state.spot)
            assertEquals(TempoRung.WAIT, state.rung)
        }

    @Test
    fun `offers none for a clean run`() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1", 100))

            assertNull(assertIs<ResultsState.Ready>(vm.state.value).spot)
        }

    @Test
    fun `offers a loop of a timed run with the rung it was played on`() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 1700, 400))
            val vm =
                results(
                    "a-1",
                    attempt("a-1", 100, tempoBpm = 80, playedNotes = notes, band = Band.GOOD),
                    rung = TempoRung.P100,
                )

            val state = assertIs<ResultsState.Ready>(vm.state.value)
            assertEquals(BarRange(1, 1), state.spot)
            assertEquals(TempoRung.P100, state.rung)
        }

    @Test
    fun `a clean timed run with timing findings offers no loop`() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 1700, 400))
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80, playedNotes = notes, band = Band.CLEAN))

            val state = assertIs<ResultsState.Ready>(vm.state.value)
            assertNotNull(state.timing)
            assertNull(state.spot)
        }

    @Test
    fun `reports the rung it was played on even when a lower rung has the same tempo`() =
        runTest(dispatcher) {
            // A tempo that clamps to the same BPM as a lower rung (here P70's 56) still reports the rung played.
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 56), rung = TempoRung.P85)

            assertEquals(TempoRung.P85, assertIs<ResultsState.Ready>(vm.state.value).rung)
        }

    @Test
    fun `a perform take offers no loop`() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 1700, 400))
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80, playedNotes = notes, mode = RunMode.PERFORM))

            assertNull(assertIs<ResultsState.Ready>(vm.state.value).spot)
        }

    @Test
    fun aPerformAttemptIsReadyAsPerformed() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80, mode = RunMode.PERFORM))

            assertEquals(RunMode.PERFORM, assertIs<ResultsState.Ready>(vm.state.value).mode)
        }

    @Test
    fun timedAttemptFillsFourTilesAndTempo() =
        runTest(dispatcher) {
            // C-D-E at 80 BPM (750 ms a beat): the half note E is 200 ms late and let go after 400 ms of 1500.
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 1700, 400))
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80, playedNotes = notes))

            val state = vm.state.value as ResultsState.Ready
            assertEquals(80, state.tempoBpm)
            assertEquals(3, state.correctSteps)
            assertEquals(3, state.stepCount)
            assertEquals(TimingTile(67, TimingWorst(late = true, noteNumber = 3), calibrated = false), state.timing)
            assertEquals(true, state.timing?.offBeat)
            assertEquals(
                LengthTile(67, LengthWorst(tooLong = false, noteNumber = 3, value = NoteValue.HALF)),
                state.length,
            )
            assertEquals(HesitationTile(0, null), state.hesitation)
        }

    @Test
    fun theStoredLatencyOffsetIsSubtractedBeforeAnalysis() =
        runTest(dispatcher) {
            // The same take, every key 200 ms late on the wire: a piano that answers 200 ms late plays in time.
            val notes = listOf(played(C4, 200, 600), played(D4, 950, 600), played(E4, 1700, 1400))
            val vm =
                results(
                    "a-1",
                    attempt(
                        "a-1",
                        100,
                        tempoBpm = 80,
                        playedNotes = notes,
                        latencyOffsetNanos =
                            200 * MS,
                    ),
                )

            val state = vm.state.value as ResultsState.Ready
            assertEquals(TimingTile(0, null, calibrated = true), state.timing)
        }

    @Test
    fun theStoredLatencyOffsetIsSubtractedBeforePickingLoopBars() =
        runTest(dispatcher) {
            // Every key 200 ms late on the wire, from a piano that answers 200 ms late: in time, so nothing to loop.
            val notes = listOf(played(C4, 200, 600), played(D4, 950, 600), played(E4, 1700, 1400))
            val take = attempt("a-1", 100, band = Band.GOOD, tempoBpm = 80, playedNotes = notes)
            val calibrated = results("a-1", take.copy(latencyOffsetNanos = 200 * MS))
            assertNull((calibrated.state.value as ResultsState.Ready).spot)

            val uncalibrated = results("a-2", take.copy(id = "a-2"))
            assertNotNull((uncalibrated.state.value as ResultsState.Ready).spot)
        }

    @Test
    fun aTimedAttemptInTimeHasNoWorstNote() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 1500, 1400))
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80, playedNotes = notes))

            val state = vm.state.value as ResultsState.Ready
            assertEquals(TimingTile(0, null, calibrated = false), state.timing)
            assertEquals(false, state.timing?.offBeat)
            assertEquals(LengthTile(100, null), state.length)
        }

    @Test
    fun waitAttemptFillsPitchAndHesitation() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 1500, 600))
            val vm = results("a-1", attempt("a-1", 100, playedNotes = notes))

            val state = vm.state.value as ResultsState.Ready
            assertNull(state.tempoBpm)
            assertNull(state.timing)
            assertNull(state.length)
            assertEquals(HesitationTile(0, null), state.hesitation)
            assertEquals(3, state.correctSteps)
        }

    @Test
    fun theLongestPauseIsRoundedToTenthsOfASecond() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 4000, 600))
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80, playedNotes = notes))

            assertEquals(HesitationTile(1, Pause(33, "E4")), (vm.state.value as ResultsState.Ready).hesitation)
        }

    @Test
    fun oldAttemptFillsPitchOnly() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1", 100))

            val state = vm.state.value as ResultsState.Ready
            assertEquals(3, state.correctSteps)
            assertNull(state.tempoBpm)
            assertNull(state.timing)
            assertNull(state.length)
            assertNull(state.hesitation)
        }

    @Test
    fun anAttemptOfAnUnknownPassageHasNoTiles() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600))
            val vm = results("a-1", attempt("a-1", 100, passageId = "builtin:gone", tempoBpm = 80, playedNotes = notes))

            val state = vm.state.value as ResultsState.Ready
            assertEquals(80, state.tempoBpm)
            assertNull(state.timing)
            assertNull(state.hesitation)
        }

    @Test
    fun aPauseAfterAMissedNoteHasNoLongest() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(E4, 5000, 600))
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80, playedNotes = notes))

            assertEquals(HesitationTile(1, null), (vm.state.value as ResultsState.Ready).hesitation)
        }

    @Test
    fun timedAttemptWithNoPlayedNotesShowsEveryNoteMissed() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80))

            val state = vm.state.value as ResultsState.Ready
            assertEquals(0, state.correctSteps)
            assertEquals(3, state.stepCount)
            assertEquals(3, state.missedNotes)
            assertEquals(0, state.wrongNotes)
            assertNull(state.timing)
            assertNull(state.length)
            assertEquals(HesitationTile(0, null), state.hesitation)
        }

    @Test
    fun timedPitchSlipsComeFromTheAnalysis() =
        runTest(dispatcher) {
            // D4 played as G4 (wrong note) and E4 left out (missed).
            val notes = listOf(played(C4, 0, 600), played(67, 750, 600))
            val vm = results("a-1", attempt("a-1", 100, wrongNotes = 5, tempoBpm = 80, playedNotes = notes))

            val state = vm.state.value as ResultsState.Ready
            assertEquals(1, state.wrongNotes)
            assertEquals(1, state.missedNotes)
            assertEquals(1, state.correctSteps)
        }

    @Test
    fun theRunNumberCountsOlderAttemptsOfTheSamePassage() =
        runTest(dispatcher) {
            val vm =
                results(
                    "a-3",
                    attempt("a-1", 100),
                    attempt("a-2", 200),
                    attempt("a-3", 300),
                    attempt("a-4", 400),
                    attempt("other", 50, passageId = "builtin:other"),
                )

            assertEquals(3, (vm.state.value as ResultsState.Ready).runNumber)
        }

    @Test
    fun aSpotLoopAttemptIsNotAnalysedAgainstTheWholePiece() =
        runTest(dispatcher) {
            val state = assertIs<ResultsState.Ready>(results("a-1", lateRun().copy(spot = BarRange(1, 1))).state.value)

            assertNull(state.timing)
            assertNull(state.spot)
        }

    @Test
    fun runNumberSkipsSpotLoops() =
        runTest(dispatcher) {
            val loops = (0 until 5).map { attempt("l-$it", 300L + it).copy(spot = BarRange(1, 1)) }
            val vm =
                results("a-4", attempt("a-1", 100), attempt("a-2", 200), *loops.toTypedArray(), attempt("a-4", 400))

            assertEquals(3, (vm.state.value as ResultsState.Ready).runNumber)
        }

    @Test
    fun theRunNumberIsCountedWithoutLoadingTheHistory() =
        runTest(dispatcher) {
            listOf(attempt("a-1", 100), attempt("a-2", 200)).forEach { attempts.save(it) }
            val countOnly =
                object : AttemptRepositoryPort by attempts {
                    override fun recent(
                        passageId: String,
                        limit: Int,
                    ) = error("recent must not be used")
                }
            val vm =
                ResultsViewModel(
                    "a-2",
                    TempoRung.WAIT,
                    countOnly,
                    FakeSettingsRepository(),
                    learner,
                    dispatcher,
                ).also { runCurrent() }

            assertEquals(2, (vm.state.value as ResultsState.Ready).runNumber)
        }

    @Test
    fun anUnknownPassageShowsItsRawId() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1", 100, passageId = "builtin:gone"))

            assertEquals("builtin:gone", (vm.state.value as ResultsState.Ready).passageTitle)
        }

    @Test
    fun anUnknownIdIsNotFound() =
        runTest(dispatcher) {
            val vm = results("missing", attempt("a-1", 100))

            assertEquals(ResultsState.NotFound, vm.state.value)
        }

    @Test
    fun itIsLoadingUntilTheRepositoryAnswers() =
        runTest(dispatcher) {
            val answer = CompletableDeferred<Attempt?>()
            val slow =
                object : AttemptRepositoryPort by attempts {
                    override suspend fun get(id: String): Attempt? = answer.await()
                }
            val vm =
                ResultsViewModel(
                    "a-1",
                    TempoRung.WAIT,
                    slow,
                    FakeSettingsRepository(),
                    learner,
                    dispatcher,
                ).also { runCurrent() }
            assertEquals(ResultsState.Loading, vm.state.value)

            answer.complete(null)
            runCurrent()
            assertEquals(ResultsState.NotFound, vm.state.value)
        }

    @Test
    fun aFailingGetGivesUnavailable() =
        runTest(dispatcher) {
            attempts.save(attempt("a-1", 100))
            attempts.failing = true

            val vm =
                ResultsViewModel(
                    "a-1",
                    TempoRung.WAIT,
                    attempts,
                    FakeSettingsRepository(),
                    learner,
                    dispatcher,
                ).also { runCurrent() }

            assertEquals(ResultsState.Unavailable, vm.state.value)
        }

    @Test
    fun aFailingCountGivesUnavailable() =
        runTest(dispatcher) {
            attempts.save(attempt("a-1", 100))
            val countFails =
                object : AttemptRepositoryPort by attempts {
                    override suspend fun countStartedBefore(
                        passageId: String,
                        before: Instant,
                    ): Int = throw StorageUnavailableException("countStartedBefore failed")
                }

            val vm =
                ResultsViewModel(
                    "a-1",
                    TempoRung.WAIT,
                    countFails,
                    FakeSettingsRepository(),
                    learner,
                    dispatcher,
                ).also { runCurrent() }

            assertEquals(ResultsState.Unavailable, vm.state.value)
        }

    @Test
    fun retryAfterRecoveryGivesReady() =
        runTest(dispatcher) {
            attempts.save(attempt("a-1", 100))
            attempts.failing = true
            val vm =
                ResultsViewModel(
                    "a-1",
                    TempoRung.WAIT,
                    attempts,
                    FakeSettingsRepository(),
                    learner,
                    dispatcher,
                ).also { runCurrent() }

            attempts.failing = false
            vm.retry()
            assertEquals(ResultsState.Loading, vm.state.value)
            runCurrent()

            assertEquals(1, (vm.state.value as ResultsState.Ready).runNumber)
        }

    @Test
    fun aRetryCancelsTheReadStillRunning() =
        runTest(dispatcher) {
            attempts.save(attempt("a-1", 100))
            val firstRead = CompletableDeferred<Attempt?>()
            var reads = 0
            val firstHangs =
                object : AttemptRepositoryPort by attempts {
                    override suspend fun get(id: String): Attempt? =
                        if (++reads == 1) firstRead.await() else attempts.get(id)
                }
            val vm =
                ResultsViewModel(
                    "a-1",
                    TempoRung.WAIT,
                    firstHangs,
                    FakeSettingsRepository(),
                    learner,
                    dispatcher,
                ).also { runCurrent() }

            vm.retry()
            runCurrent()
            firstRead.complete(null) // too late: the first read was cancelled
            runCurrent()

            assertEquals(1, (vm.state.value as ResultsState.Ready).runNumber)
        }

    @Test
    fun theAnalysisRunsOnTheAnalysisDispatcher() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 1500, 1400))
            attempts.save(attempt("a-1", 100, tempoBpm = 80, playedNotes = notes))
            val analysis = HeldDispatcher()

            val vm =
                ResultsViewModel(
                    "a-1",
                    TempoRung.WAIT,
                    attempts,
                    FakeSettingsRepository(),
                    learner,
                    analysis,
                ).also { runCurrent() }
            assertEquals(ResultsState.Loading, vm.state.value)

            analysis.runQueued()
            runCurrent()
            assertEquals(TimingTile(0, null, calibrated = false), (vm.state.value as ResultsState.Ready).timing)
        }

    private suspend fun learnSight(vararg skills: Skill) =
        learnerRepository.replaceStates(
            skills.map {
                SkillState(
                    skillId = it.id,
                    memory = MemoryState(stability = 2.0, difficulty = 5.0),
                    introducedDay = LocalDate(2026, 11, 1),
                    lastReviewDay = LocalDate(2026, 11, 10),
                    dueDay = LocalDate(2026, 12, 1),
                    cleanColdChecks = 0,
                    recalledColdChecks = 0,
                    unaidedRecalledChecks = 0,
                    learned = true,
                )
            },
        )

    private fun sightAttempt(
        id: String,
        seconds: Long,
        seed: Int,
        band: Band,
    ) = attempt(id, seconds, passageId = "sight:1:$seed", band = band)

    private fun sightOutcome(vm: ResultsViewModel) = assertNotNull(assertIs<ResultsState.Ready>(vm.state.value).sight)

    @Test
    fun sightLevelUpLine() =
        runTest(dispatcher) {
            learnSight(Skills.STAFF_STEPS, Skills.TREBLE_CLEF)
            val vm =
                results(
                    "s-2",
                    sightAttempt("s-1", 100, 1, Band.CLEAN),
                    sightAttempt("s-2", 200, 2, Band.CLEAN),
                )

            assertEquals(SightOutcome(level = 1, change = SightChange.UP, newLevel = 2), sightOutcome(vm))
        }

    @Test
    fun sightLevelDownLine() =
        runTest(dispatcher) {
            learnSight(Skills.STAFF_STEPS, Skills.TREBLE_CLEF)
            val vm =
                results(
                    "s-3",
                    sightAttempt("s-1", 100, 1, Band.CLEAN),
                    sightAttempt("s-2", 200, 2, Band.CLEAN),
                    sightAttempt("s-3", 300, 3, Band.KEEP_PRACTISING),
                )

            assertEquals(SightOutcome(level = 1, change = SightChange.DOWN, newLevel = 1), sightOutcome(vm))
        }

    @Test
    fun sightStaysWhenTheStreakIsNotYetComplete() =
        runTest(dispatcher) {
            learnSight(Skills.STAFF_STEPS, Skills.TREBLE_CLEF)
            val vm = results("s-1", sightAttempt("s-1", 100, 1, Band.CLEAN))

            assertEquals(SightOutcome(level = 1, change = SightChange.STAYS, newLevel = 1), sightOutcome(vm))
        }

    @Test
    fun sightReplayLine() =
        runTest(dispatcher) {
            learnSight(Skills.STAFF_STEPS, Skills.TREBLE_CLEF)
            val vm =
                results(
                    "s-2",
                    sightAttempt("s-1", 100, 1, Band.GOOD),
                    sightAttempt("s-2", 200, 1, Band.CLEAN),
                )

            assertEquals(SightChange.REPLAY, sightOutcome(vm).change)
        }

    @Test
    fun sightHasNoRunNumber() =
        runTest(dispatcher) {
            learnSight(Skills.STAFF_STEPS)
            val vm = results("s-1", sightAttempt("s-1", 100, 1, Band.GOOD))

            val ready = assertIs<ResultsState.Ready>(vm.state.value)
            assertNull(ready.runNumber)
            assertNull(ready.spot)
            assertEquals("Sight-reading level 1", ready.passageTitle)
        }

    @Test
    fun sightCleanRunGivesNoNextTempoTip() =
        runTest(dispatcher) {
            learnSight(Skills.STAFF_STEPS)
            val vm = results("s-1", sightAttempt("s-1", 100, 1, Band.CLEAN))

            assertNull(assertIs<ResultsState.Ready>(vm.state.value).words.tip)
        }
}
