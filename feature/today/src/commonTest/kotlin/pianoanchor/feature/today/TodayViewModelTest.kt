package pianoanchor.feature.today

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.Settings
import pianoanchor.core.model.SkillState
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeCalibrationRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    // Wednesday 2026-11-18, 10:00 UTC: the week started on Monday the 16th.
    private val now = Instant.parse("2026-11-18T10:00:00Z")
    private val today = LocalDate(2026, 11, 18)
    private val learnerRepository = FakeLearnerRepository()
    private val settings = FakeSettingsRepository(Settings(dailyBudget = DailyBudget.MIN_20, weeklyGoalDays = 4))
    private val attempts = FakeAttemptRepository()
    private val calibration = FakeCalibrationRepository()
    private val piano = FakePiano(device = MidiDevice("usb:Roland/FP-30X", "FP-30X"))
    private val clock = FakeWallClock(now)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): TodayViewModel =
        TodayViewModel(
            LearnerModel(Curriculum.graph, learnerRepository),
            settings,
            attempts,
            piano,
            calibration,
            clock,
            TimeZone.UTC,
        ).also { runCurrent() }

    private fun skillState(
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
        learned = learned,
    )

    private suspend fun givenStates(vararg states: SkillState) = learnerRepository.replaceStates(states.toList())

    private suspend fun playedOn(
        date: String,
        id: String = "a-$date",
        band: Band = Band.GOOD,
        tempoBpm: Int? = null,
        passageId: String = "builtin:middle-c-march",
    ) = attempts.save(
        Attempt(
            id = id,
            passageId = passageId,
            deviceId = "usb-1",
            startedAt = Instant.parse("${date}T09:00:00Z"),
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

    @Test
    fun cardsOpenPracticeWithPassageAndRung() =
        runTest(dispatcher) {
            givenStates(
                skillState(Skills.KB_MIDDLE_C.id),
                skillState(Skills.RH_TUNES.id, dueDay = LocalDate(2026, 12, 1), learned = true),
            )
            val march = checkNotNull(Curriculum.passage("builtin:middle-c-march"))
            playedOn("2026-11-10", tempoBpm = TempoRung.P60.bpm(march), band = Band.CLEAN)
            playedOn("2026-11-12", id = "other", passageId = "builtin:which-c-is-middle")
            val vm = viewModel()

            val session = assertNotNull(vm.state.value.session)
            val cold = assertIs<Card.ColdOpen>(session.cards.first())
            assertEquals(
                TodayTarget.Practice(piano.device, cold.passage.id, cold.rung),
                vm.target(cold),
            )
            // The march was played least recently, and cleanly at 60%.
            assertEquals("builtin:middle-c-march", cold.passage.id)
            assertEquals(TempoRung.P60, cold.rung)
            val song = session.cards.filterIsInstance<Card.Song>().single()
            assertEquals(TodayTarget.Practice(piano.device, song.passage.id, song.rung), vm.target(song))
            assertEquals(
                "Middle C",
                vm.state.value.coldOpen
                    ?.skill
                    ?.title,
            )
        }

    @Test
    fun weeklyDotsCountPracticeDaysThisWeek() =
        runTest(dispatcher) {
            // Sunday the 15th belongs to last week; Monday, Wednesday (two runs) count once each.
            playedOn("2026-11-15")
            playedOn("2026-11-16")
            playedOn("2026-11-18")
            playedOn("2026-11-18", id = "second")
            val vm = viewModel()

            assertEquals(2, vm.state.value.daysThisWeek)
            assertEquals(4, vm.state.value.weeklyGoalDays)
        }

    @Test
    fun practiceDayLineCountsTheLast16Days() =
        runTest(dispatcher) {
            // The window is the 3rd to the 18th: the 2nd is outside it, and so is a day in the future.
            listOf("2026-11-02", "2026-11-03", "2026-11-09", "2026-11-18", "2026-11-19").forEach { playedOn(it) }
            val vm = viewModel()

            assertEquals(3, vm.state.value.practicedDays)
            assertEquals(16, PRACTICE_WINDOW_DAYS)
        }

    @Test
    fun noPianoShowsTheChipThatOpensPianos() =
        runTest(dispatcher) {
            piano.unplug()
            val vm = viewModel()

            assertNull(vm.state.value.piano)
            val card = assertIs<Card.Lesson>(assertNotNull(vm.state.value.session).cards.first())
            assertEquals(TodayTarget.Pianos, vm.target(card))
        }

    @Test
    fun theChipNamesTheConnectedPianoItsTransportAndLatency() =
        runTest(dispatcher) {
            calibration.setLatencyOffsetNanos(piano.device.id, 18_400_000)
            assertEquals(PianoChip(piano.device, Transport.USB, 18), viewModel().state.value.piano)
        }

    @Test
    fun theChipReadsBluetoothAndUncalibratedPianos() =
        runTest(dispatcher) {
            val ble = FakePiano(device = MidiDevice("ble:AA:BB", "Casio"))
            val vm =
                TodayViewModel(
                    LearnerModel(Curriculum.graph, learnerRepository),
                    settings,
                    attempts,
                    ble,
                    calibration,
                    clock,
                    TimeZone.UTC,
                )
            runCurrent()
            assertEquals(PianoChip(ble.device, Transport.BLE, null), vm.state.value.piano)
            val other = FakePiano(device = MidiDevice("midi-1", "Other"))
            val vm2 =
                TodayViewModel(
                    LearnerModel(Curriculum.graph, learnerRepository),
                    settings,
                    attempts,
                    other,
                    calibration,
                    clock,
                    TimeZone.UTC,
                )
            runCurrent()
            assertNull(
                vm2.state.value.piano
                    ?.transport,
            )
        }

    @Test
    fun aCalibrationStoreThatFailsLeavesTheLatencyOut() =
        runTest(dispatcher) {
            calibration.failing = true
            assertEquals(PianoChip(piano.device, Transport.USB, null), viewModel().state.value.piano)
        }

    @Test
    fun allCaughtUpState() =
        runTest(dispatcher) {
            // A learner with every skill introduced and nothing due: no cold open, no lesson.
            givenStates(
                *Curriculum.graph.skills
                    .map {
                        skillState(it.id, dueDay = LocalDate(2026, 12, 1), learned = true)
                    }.toTypedArray(),
            )
            val vm = viewModel()

            assertTrue(vm.state.value.caughtUp)
            assertNull(vm.state.value.coldOpen)
        }

    @Test
    fun aFreshLearnerHasALessonAndIsNotCaughtUp() =
        runTest(dispatcher) {
            val vm = viewModel()

            assertFalse(vm.state.value.caughtUp)
            assertIs<Card.Lesson>(assertNotNull(vm.state.value.session).cards.single())
        }

    @Test
    fun greetingFollowsTheTimeOfDay() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(Greeting.MORNING, vm.state.value.greeting)
            clock.advance(kotlin.time.Duration.parse("PT3H"))
            vm.refresh()
            runCurrent()
            assertEquals(Greeting.AFTERNOON, vm.state.value.greeting)
            clock.advance(kotlin.time.Duration.parse("PT6H"))
            vm.refresh()
            runCurrent()
            assertEquals(Greeting.EVENING, vm.state.value.greeting)
        }

    @Test
    fun aStoreThatFailsShowsTheErrorUntilRefreshWorks() =
        runTest(dispatcher) {
            attempts.failing = true
            val vm = viewModel()
            assertTrue(vm.state.value.storageFailed)
            assertNull(vm.state.value.session)
            assertFalse(vm.state.value.caughtUp)

            attempts.failing = false
            vm.refresh()
            runCurrent()
            assertFalse(vm.state.value.storageFailed)
            assertNotNull(vm.state.value.session)
        }
}
