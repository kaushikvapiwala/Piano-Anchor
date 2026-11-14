package pianoanchor.feature.practice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.Step
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.content.Passages
import pianoanchor.testing.FakeAttemptRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private const val MS = 1_000_000L
private const val C4 = 60
private const val D4 = 62
private const val E4 = 64

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val attempts = FakeAttemptRepository()

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
        events = emptyList(),
        tempoBpm = tempoBpm,
        playedNotes = playedNotes,
    )

    private fun played(
        pitch: Int,
        downMs: Long,
        lengthMs: Long,
    ) = PlayedNote(MidiNote(pitch), 70, downMs * MS, (downMs + lengthMs) * MS, (downMs + lengthMs) * MS)

    private suspend fun TestScope.results(
        id: String,
        vararg saved: Attempt,
    ): ResultsViewModel {
        saved.forEach { attempts.save(it) }
        return ResultsViewModel(id, attempts).also { runCurrent() }
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
                ),
                vm.state.value,
            )
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
            assertEquals(TimingTile(67, TimingWorst(late = true, noteNumber = 3)), state.timing)
            assertEquals(true, state.timing?.offBeat)
            assertEquals(
                LengthTile(67, LengthWorst(tooLong = false, noteNumber = 3, value = NoteValue.HALF)),
                state.length,
            )
            assertEquals(HesitationTile(0, null), state.hesitation)
        }

    @Test
    fun aTimedAttemptInTimeHasNoWorstNote() =
        runTest(dispatcher) {
            val notes = listOf(played(C4, 0, 600), played(D4, 750, 600), played(E4, 1500, 1400))
            val vm = results("a-1", attempt("a-1", 100, tempoBpm = 80, playedNotes = notes))

            val state = vm.state.value as ResultsState.Ready
            assertEquals(TimingTile(0, null), state.timing)
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
    fun notesAreNumberedInScoreOrderAndNamedByValue() {
        val ticks = TICKS_PER_QUARTER
        val passage =
            Passage(
                id = "test:p",
                title = "p",
                steps =
                    listOf(
                        Step(0, setOf(ScoreNote(MidiNote(64), ticks), ScoreNote(MidiNote(60), 4 * ticks))),
                        Step(4 * ticks, setOf(ScoreNote(MidiNote(62), ticks / 2))),
                    ),
                timeSignature = TimeSignature(4, 4),
                tempoBpm = 80,
            )

        assertEquals(1, noteNumber(passage, ScoreNoteRef(0, MidiNote(60))))
        assertEquals(2, noteNumber(passage, ScoreNoteRef(0, MidiNote(64))))
        assertEquals(3, noteNumber(passage, ScoreNoteRef(1, MidiNote(62))))
        assertEquals(NoteValue.WHOLE, noteValue(passage, ScoreNoteRef(0, MidiNote(60))))
        assertEquals(NoteValue.QUARTER, noteValue(passage, ScoreNoteRef(0, MidiNote(64))))
        assertEquals(NoteValue.HALF, noteValue(Passages.CDE, ScoreNoteRef(2, MidiNote(64))))
        assertNull(noteValue(passage, ScoreNoteRef(1, MidiNote(62))))
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
            val vm = ResultsViewModel("a-2", countOnly).also { runCurrent() }

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
            val vm = ResultsViewModel("a-1", slow).also { runCurrent() }
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

            val vm = ResultsViewModel("a-1", attempts).also { runCurrent() }

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

            val vm = ResultsViewModel("a-1", countFails).also { runCurrent() }

            assertEquals(ResultsState.Unavailable, vm.state.value)
        }

    @Test
    fun retryAfterRecoveryGivesReady() =
        runTest(dispatcher) {
            attempts.save(attempt("a-1", 100))
            attempts.failing = true
            val vm = ResultsViewModel("a-1", attempts).also { runCurrent() }

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
            val vm = ResultsViewModel("a-1", firstHangs).also { runCurrent() }

            vm.retry()
            runCurrent()
            firstRead.complete(null) // too late: the first read was cancelled
            runCurrent()

            assertEquals(1, (vm.state.value as ResultsState.Ready).runNumber)
        }
}
