package pianoanchor.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.NoteNames
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.NoteLabels
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.engine.lesson.NoteNameStage
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
import kotlin.test.assertNull
import kotlin.time.Instant

/** The strip's note labels: null until the injected [NoteLabelsUseCase] has read storage, then what it returns. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelLabelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock).apply { inputOnly = true }
    private val attempts = FakeAttemptRepository()
    private val learner = LearnerModel(Curriculum.graph, FakeLearnerRepository())
    private val settings = FakeSettingsRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun practice() =
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            piano,
            attempts,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            learner,
            TimeZone.UTC,
            settings,
            NoteLabelsUseCase(settings, attempts, learner, TimeZone.UTC),
        ) {
            "attempt-1"
        }

    @Test
    fun theLabelsAreNullUntilStorageAnswersThenLoaded() =
        runTest(dispatcher) {
            val vm = practice()

            assertNull(vm.state.value.noteLabels) // nothing has run yet
            runCurrent()

            assertEquals(
                NoteLabels(NoteNameStage.ALL_WHITE, sharpsPassed = false, hints = true),
                vm.state.value.noteLabels,
            )
            vm.close()
        }

    @Test
    fun aNewLearnerSeesEveryWhiteKeyLettered() =
        runTest(dispatcher) {
            val vm = practice()
            runCurrent()

            assertEquals(
                NoteLabels(NoteNameStage.ALL_WHITE, sharpsPassed = false, hints = true),
                vm.state.value.noteLabels,
            )
            vm.close()
        }

    @Test
    fun turningNoteNamesOffLettersNothing() =
        runTest(dispatcher) {
            settings.update { it.copy(noteNames = NoteNames.OFF) }
            val vm = practice()
            runCurrent()

            assertEquals(NoteLabels(NoteNameStage.NONE, sharpsPassed = false, hints = false), vm.state.value.noteLabels)
            vm.close()
        }
}
