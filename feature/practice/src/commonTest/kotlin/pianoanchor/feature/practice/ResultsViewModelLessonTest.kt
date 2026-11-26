package pianoanchor.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** What Results say about the lesson a clean wait-mode run passed (`ResultsState.Ready.lesson`). */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelLessonTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val attempts = FakeAttemptRepository()
    private val learnerRepository = FakeLearnerRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun attempt(
        id: String,
        startedAtSeconds: Long,
        passageId: String,
        band: Band = Band.CLEAN,
        tempoBpm: Int? = null,
        hintShown: Boolean = true,
    ) = Attempt(
        id = id,
        passageId = passageId,
        deviceId = "usb-1",
        startedAt = Instant.fromEpochSeconds(startedAtSeconds),
        durationNanos = 2_000_000_000,
        stepCount = 3,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = band,
        events = emptyList(),
        tempoBpm = tempoBpm,
        playedNotes = emptyList(),
        hintShown = hintShown,
    )

    private suspend fun TestScope.results(
        id: String,
        vararg saved: Attempt,
    ): ResultsViewModel {
        saved.forEach { attempts.save(it) }
        return ResultsViewModel(
            id,
            TempoRung.WAIT,
            attempts,
            FakeSettingsRepository(),
            LearnerModel(Curriculum.graph, learnerRepository),
            piano.device,
            piano,
            dispatcher,
        ).also { runCurrent() }
    }

    private fun ResultsViewModel.lesson() = assertIs<ResultsState.Ready>(state.value).lesson

    @Test
    fun aCleanUnaidedWaitRunOfACourseConceptSaysTheLessonIsPassed() =
        runTest(dispatcher) {
            val march = "builtin:middle-c-march"
            val vm = results("a-1", attempt("a-1", 1_000, march, hintShown = false))
            assertEquals(LessonResult.PASSED, vm.lesson())
        }

    @Test
    fun aSecondUnaidedCleanRunDoesNotPassTheLessonAgain() =
        runTest(dispatcher) {
            val march = "builtin:middle-c-march"
            val vm =
                results(
                    "a-2",
                    attempt("a-1", 1_000, march, hintShown = false),
                    attempt("a-2", 2_000, march, hintShown = false),
                )
            assertNull(vm.lesson())
        }

    @Test
    fun aCleanRunWithHintsAsksForARunWithoutThem() =
        runTest(dispatcher) {
            val march = "builtin:middle-c-march"
            assertEquals(LessonResult.CLEAN_WITH_HINTS, results("a-1", attempt("a-1", 1_000, march)).lesson())
        }

    @Test
    fun aCleanRunWithHintsOfAPassedConceptSaysNothing() =
        runTest(dispatcher) {
            val march = "builtin:middle-c-march"
            val vm = results("a-2", attempt("a-1", 1_000, march, hintShown = false), attempt("a-2", 2_000, march))
            assertNull(vm.lesson())
        }

    @Test
    fun noLessonLineForAnUnpassedTimedOrNonCourseRun() =
        runTest(dispatcher) {
            val march = "builtin:middle-c-march"
            val good = results("a-1", attempt("a-1", 1_000, march, band = Band.GOOD, hintShown = false))
            assertNull(good.lesson())
            val timed = results("a-2", attempt("a-2", 2_000, march, tempoBpm = 60, hintShown = false))
            assertNull(timed.lesson())
            // Not a curriculum passage at all.
            val other = results("a-3", attempt("a-3", 3_000, "sight:1:1", hintShown = false))
            assertNull(other.lesson())
        }

    @Test
    fun placedConceptsGetNoLessonLine() =
        runTest(dispatcher) {
            val march = checkNotNull(Curriculum.passage("builtin:middle-c-march"))
            LearnerModel(Curriculum.graph, learnerRepository).place(march.skills, LocalDate(2026, 11, 1))
            assertNull(results("a-1", attempt("a-1", 1_000, march.id, hintShown = false)).lesson())
            assertNull(results("a-2", attempt("a-2", 2_000, march.id)).lesson())
        }

    @Test
    fun anUnreadableStoreLeavesTheResultsWithoutALessonLine() =
        runTest(dispatcher) {
            val march = "builtin:middle-c-march"
            attempts.save(attempt("a-1", 1_000, march, hintShown = false))
            learnerRepository.failing = true
            val vm = results("a-1")
            assertNull(vm.lesson())
        }
}
