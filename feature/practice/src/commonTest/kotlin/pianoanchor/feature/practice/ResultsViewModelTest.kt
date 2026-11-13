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
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.content.Passages
import pianoanchor.testing.FakeAttemptRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

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
    )

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
}
