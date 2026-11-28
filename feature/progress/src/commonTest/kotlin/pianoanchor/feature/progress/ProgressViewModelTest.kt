package pianoanchor.feature.progress

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Settings
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
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

private const val MS = 1_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber") // Dates and timestamps of the scenarios.
class ProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    // Wednesday 2026-11-18; the week started on Monday the 16th.
    private val now = Instant.parse("2026-11-18T10:00:00Z")
    private val attempts = FakeAttemptRepository()
    private val learnerRepository = FakeLearnerRepository()
    private val learner = LearnerModel(Curriculum.graph, learnerRepository)
    private val settings = FakeSettingsRepository(Settings(weeklyGoalDays = 4))
    private val tone = FakeTone()
    private val clock = FakeWallClock(now)
    private val piece = Curriculum.passages.first { it.piece }
    private val other = Curriculum.passages.first { it.piece && it.id != piece.id }
    private val opened = mutableListOf<ProgressViewModel>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() {
        opened.forEach { it.stop() }
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): ProgressViewModel =
        ProgressViewModel(attempts, learner, settings, tone, clock, TimeZone.UTC)
            .also {
                opened += it
                runCurrent()
            }

    private suspend fun played(
        id: String,
        at: String,
        passageId: String = piece.id,
        durationMinutes: Long = 1,
        spot: BarRange? = null,
        notes: List<PlayedNote> = emptyList(),
    ) = attempts.save(
        Attempt(
            id = id,
            passageId = passageId,
            deviceId = "usb-1",
            startedAt = Instant.parse(at),
            durationNanos = durationMinutes * 60_000_000_000L,
            stepCount = 1,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = listOf(PassageComplete(0, 0)),
            tempoBpm = null,
            playedNotes = notes,
            spot = spot,
        ),
    )

    private fun note(
        pitch: Int,
        downMs: Long,
    ) = PlayedNote(MidiNote(pitch), 70, downMs * MS, (downMs + 200) * MS, (downMs + 200) * MS)

    @Test
    fun weekStatusFollowsTheGoalAndThePlayedDays() =
        runTest {
            played("a-1", "2026-11-16T09:00:00Z")
            played("a-2", "2026-11-17T09:00:00Z")

            val week = assertNotNull(viewModel().state.value.week)

            assertEquals(LocalDate(2026, 11, 16), week.weekStart)
            assertEquals(2, week.daysPlayed)
            assertEquals(4, week.goalDays)
            assertTrue(week.canPause)
        }

    @Test
    fun minutesRunMondayToSundayWithTodayMarked() =
        runTest {
            played("a-1", "2026-11-16T09:00:00Z", durationMinutes = 12)
            played("a-2", "2026-11-18T09:00:00Z", durationMinutes = 5, passageId = other.id)
            played("a-3", "2026-11-18T10:00:00Z", durationMinutes = 2)
            played("a-old", "2026-11-15T09:00:00Z", durationMinutes = 30)

            val state = viewModel().state.value

            assertEquals(listOf(12, 0, 7, 0, 0, 0, 0), state.minutesByDay)
            assertEquals(2, state.todayIndex)
        }

    @Test
    fun learnedThisWeekListsSkillsLearnedSinceMonday() =
        runTest {
            val skill = Skills.FINGER_NUMBER_TUNES.id
            val passage = Curriculum.passages.first { skill in it.skills }
            learner.record(passage, attempt("r-1", passage.id), LocalDate(2026, 11, 8))
            learner.record(passage, attempt("r-2", passage.id), LocalDate(2026, 11, 17))
            assertTrue(learnerRepository.states().getValue(skill).learned)

            assertTrue(
                skill in
                    viewModel()
                        .state.value.learnedThisWeek
                        .map { it.id },
            )
        }

    private fun attempt(
        id: String,
        passageId: String,
    ) = Attempt(
        id = id,
        passageId = passageId,
        deviceId = "usb-1",
        startedAt = now,
        durationNanos = 1,
        stepCount = 1,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = Band.CLEAN,
        events = listOf(PassageComplete(0, 0)),
        tempoBpm = 60,
        playedNotes = emptyList(),
        mode = RunMode.PERFORM,
        hintShown = false,
    )

    @Test
    fun compareShowsTheFirstAndLatestRunsOnDifferentDays() =
        runTest {
            played("a-1", "2026-11-10T09:00:00Z", notes = listOf(note(60, 0)))
            played("a-2", "2026-11-12T09:00:00Z")
            played("a-3", "2026-11-18T09:00:00Z", notes = listOf(note(62, 0)))

            val compare = assertNotNull(viewModel().state.value.compare)

            assertEquals(piece, compare.passage)
            assertEquals("a-1", compare.day1.id)
            assertEquals("a-3", compare.today.id)
            assertEquals(LocalDate(2026, 11, 10), compare.day1Date)
            assertEquals(LocalDate(2026, 11, 18), compare.todayDate)
            assertEquals(1, compare.day1Lane.size)
        }

    @Test
    fun compareIsHiddenWhenBothTakesAreFromTheSameDay() =
        runTest {
            played("a-1", "2026-11-18T08:00:00Z")
            played("a-2", "2026-11-18T09:00:00Z")

            assertNull(viewModel().state.value.compare)
        }

    @Test
    fun compareIsHiddenWithOneTakeAndWithOnlySpotLoops() =
        runTest {
            played("a-1", "2026-11-10T09:00:00Z")
            played("a-2", "2026-11-12T09:00:00Z", passageId = other.id, spot = BarRange(1, 2))

            assertNull(viewModel().state.value.compare)
        }

    @Test
    fun compareSkipsAPieceWithTakesOnOneDayForAnotherWithTwo() =
        runTest {
            played("a-1", "2026-11-10T09:00:00Z", passageId = other.id)
            played("a-2", "2026-11-12T09:00:00Z", passageId = other.id)
            played("a-3", "2026-11-18T08:00:00Z")
            played("a-4", "2026-11-18T09:00:00Z")

            assertEquals(
                other,
                viewModel()
                    .state.value.compare
                    ?.passage,
            )
        }

    @Test
    fun playDay1SendsItsTonesToTheSpeaker() =
        runTest {
            played("a-1", "2026-11-10T09:00:00Z", notes = listOf(note(60, 0), note(64, 500)))
            played("a-3", "2026-11-18T09:00:00Z", notes = listOf(note(62, 0)))
            val viewModel = viewModel()

            viewModel.play(Take.DAY_1)
            runCurrent()

            assertEquals(Take.DAY_1, viewModel.state.value.playing)
            assertEquals(listOf(listOf(60, 64)), tone.played.map { tones -> tones.map { it.note.number } })
            advanceTimeBy(10_000)
            runCurrent()
            assertNull(viewModel.state.value.playing)
        }

    @Test
    fun playingTheOtherTakeStopsTheFirst() =
        runTest {
            played("a-1", "2026-11-10T09:00:00Z", notes = listOf(note(60, 0), note(64, 5_000)))
            played("a-3", "2026-11-18T09:00:00Z", notes = listOf(note(62, 0)))
            val viewModel = viewModel()
            viewModel.play(Take.DAY_1)
            runCurrent()

            viewModel.play(Take.TODAY)
            runCurrent()

            assertEquals(Take.TODAY, viewModel.state.value.playing)
            assertEquals(2, tone.played.size)
            assertTrue(tone.playing)
            advanceTimeBy(1_000)
            runCurrent()
            assertNull(viewModel.state.value.playing)
            assertFalse(tone.playing)
        }

    @Test
    fun stopSilencesTheTake() =
        runTest {
            played("a-1", "2026-11-10T09:00:00Z", notes = listOf(note(60, 0)))
            played("a-3", "2026-11-18T09:00:00Z", notes = listOf(note(62, 0)))
            val viewModel = viewModel()
            viewModel.play(Take.TODAY)
            runCurrent()

            viewModel.stop()
            runCurrent()

            assertNull(viewModel.state.value.playing)
            assertFalse(tone.playing)
        }

    @Test
    fun playDoesNothingWithoutAComparison() =
        runTest {
            val viewModel = viewModel()

            viewModel.play(Take.DAY_1)
            runCurrent()

            assertNull(viewModel.state.value.playing)
            assertEquals(emptyList(), tone.played)
        }

    @Test
    fun aStoreThatCantBeReadSetsStorageFailedUntilRefresh() =
        runTest {
            attempts.failing = true
            val viewModel = viewModel()
            assertTrue(viewModel.state.value.storageFailed)
            assertNull(viewModel.state.value.week)

            attempts.failing = false
            viewModel.refresh()
            runCurrent()

            assertFalse(viewModel.state.value.storageFailed)
            assertNotNull(viewModel.state.value.week)
        }
}
