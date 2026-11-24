package pianoanchor.feature.today

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiTransport
import pianoanchor.core.model.Release
import pianoanchor.core.model.Settings
import pianoanchor.core.model.SkillState
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeCalibrationRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeUpdateCheck
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Instant

internal const val INSTALLED = "0.1.0"
internal val RELEASE_020 = Release("0.2.0", "https://github.com/kaushikvapiwala/Piano-Anchor/releases/tag/v0.2.0")

/** Shared fakes and helpers for the Today ViewModel tests. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class TodayViewModelFixture {
    protected val dispatcher = StandardTestDispatcher()

    // Wednesday 2026-11-18, 10:00 UTC: the week started on Monday the 16th.
    protected val now = Instant.parse("2026-11-18T10:00:00Z")
    protected val today = LocalDate(2026, 11, 18)
    protected val learnerRepository = FakeLearnerRepository()
    protected val settings = FakeSettingsRepository(Settings(dailyBudget = DailyBudget.MIN_20, weeklyGoalDays = 4))
    protected val attempts = FakeAttemptRepository()
    protected val calibration = FakeCalibrationRepository()
    protected val piano = FakePiano(device = MidiDevice("usb:Roland/FP-30X", "FP-30X", MidiTransport.USB))
    protected val clock = FakeWallClock(now)
    protected val updateCheck = FakeUpdateCheck()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    protected fun TestScope.viewModel(): TodayViewModel =
        TodayViewModel(
            LearnerModel(Curriculum.graph, learnerRepository),
            settings,
            attempts,
            piano,
            piano,
            calibration,
            clock,
            TimeZone.UTC,
            updateCheck,
            INSTALLED,
        ).also { runCurrent() }

    protected fun skillState(
        skillId: String,
        dueDay: LocalDate = today,
        learned: Boolean = false,
    ) = SkillState(
        skillId = skillId,
        memory = MemoryState(stability = 2.0, difficulty = 5.0),
        introducedDay = LocalDate(2026, 11, 1),
        lastReviewDay = LocalDate(2026, 11, 10),
        dueDay = dueDay,
        cleanColdChecks = 0,
        recalledColdChecks = 0,
        unaidedRecalledChecks = 0,
        learned = learned,
    )

    protected suspend fun givenStates(vararg states: SkillState) = learnerRepository.replaceStates(states.toList())

    protected suspend fun playedOn(
        date: String,
        id: String = "a-$date",
        band: Band = Band.GOOD,
        tempoBpm: Int? = null,
        passageId: String = "builtin:middle-c-march",
        hour: String = "09",
    ) = attempts.save(
        Attempt(
            id = id,
            passageId = passageId,
            deviceId = "usb-1",
            startedAt = Instant.parse("${date}T$hour:00:00Z"),
            durationNanos = 1,
            stepCount = 1,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = band,
            events = emptyList(),
            tempoBpm = tempoBpm,
            playedNotes = emptyList(),
        ),
    )
}
