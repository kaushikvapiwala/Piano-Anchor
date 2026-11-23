package pianoanchor.feature.today

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.Release
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Settings
import pianoanchor.core.model.SkillState
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.core.ports.UpdateCheckPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.EarSkills
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeCalibrationRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeUpdateCheck
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private const val INSTALLED = "0.1.0"
private val RELEASE_020 = Release("0.2.0", "https://github.com/kaushikvapiwala/Piano-Anchor/releases/tag/v0.2.0")

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
    private val updateCheck = FakeUpdateCheck()

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
            piano,
            calibration,
            clock,
            TimeZone.UTC,
            updateCheck,
            INSTALLED,
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
        unaidedRecalledChecks = 0,
        learned = learned,
    )

    private suspend fun givenStates(vararg states: SkillState) = learnerRepository.replaceStates(states.toList())

    private suspend fun playedOn(
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

    @Test
    fun cardsOpenPracticeWithPassageAndRung() =
        runTest(dispatcher) {
            givenStates(
                skillState(Skills.MIDDLE_C.id),
                skillState(Skills.FINGER_NUMBER_TUNES.id, dueDay = LocalDate(2026, 12, 1), learned = true),
                skillState(Skills.RH_C_POSITION.id, dueDay = LocalDate(2026, 12, 1), learned = true),
                skillState(Skills.STEADY_BEAT.id, dueDay = LocalDate(2026, 12, 1), learned = true),
            )
            val march = checkNotNull(Curriculum.passage("builtin:middle-c-march"))
            playedOn("2026-11-10", tempoBpm = TempoRung.P60.bpm(march), band = Band.CLEAN)
            playedOn("2026-11-12", id = "other", passageId = "builtin:which-c-is-middle")
            val vm = viewModel()

            val session = assertNotNull(vm.state.value.session)
            val cold = assertIs<Card.ColdOpen>(session.cards.first())
            assertEquals(
                TodayTarget.Practice(piano.device, cold.passage.id, cold.rung, RunMode.PERFORM),
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
    fun theEarCardOpensTheEarScreenWithItsSkills() =
        runTest(dispatcher) {
            givenStates(skillState(EarSkills.ECHO_NOTE.id, learned = true))
            val vm = viewModel()

            val ear = assertIs<Card.Ear>(assertNotNull(vm.state.value.session).cards.first { it is Card.Ear })
            assertEquals(TodayTarget.Ear(piano.device, ear.skills), vm.target(ear))
            assertEquals(EarSkills.ECHO_NOTE, ear.skills.first())

            piano.unplug()
            runCurrent()
            assertEquals(TodayTarget.Pianos, vm.target(ear))
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

    private suspend fun earReviewOn(date: LocalDate) =
        LearnerModel(Curriculum.graph, learnerRepository)
            .recordEarSet(EarSkills.ECHO_NOTE.id, Band.GOOD, date, "$date")

    @Test
    fun aDayWithOnlyAnEarReviewCountsAsAPracticeDay() =
        runTest(dispatcher) {
            earReviewOn(today)
            val vm = viewModel()

            assertEquals(1, vm.state.value.daysThisWeek)
            assertEquals(1, vm.state.value.practicedDays)
        }

    @Test
    fun aDayWithAnAttemptAndAnEarReviewCountsOnce() =
        runTest(dispatcher) {
            playedOn("2026-11-18")
            earReviewOn(today)
            val vm = viewModel()

            assertEquals(1, vm.state.value.daysThisWeek)
            assertEquals(1, vm.state.value.practicedDays)
        }

    @Test
    fun anEarReviewOlderThanTheWindowAndTheWeekIsNotCounted() =
        runTest(dispatcher) {
            earReviewOn(LocalDate(2026, 11, 1))
            val vm = viewModel()

            assertEquals(0, vm.state.value.daysThisWeek)
            assertEquals(0, vm.state.value.practicedDays)
        }

    @Test
    fun aLearnerStoreThatFailsShowsTheError() =
        runTest(dispatcher) {
            learnerRepository.failing = true
            val vm = viewModel()

            assertTrue(vm.state.value.storageFailed)
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

    private val remembered = MidiDevice("ble:AA:BB:CC:DD:EE:01", "Casio CDP-S160")

    /** A Today whose wired piano is unplugged, with [remembered] stored (or not) and advertising. */
    private suspend fun TestScope.reconnecting(
        remember: Boolean = true,
        discovery: MidiDiscoveryPort = piano,
    ): TodayViewModel {
        piano.unplug()
        piano.advertised = listOf(remembered)
        if (remember) settings.update { it.copy(lastPiano = remembered) }
        return TodayViewModel(
            LearnerModel(Curriculum.graph, learnerRepository),
            settings,
            attempts,
            piano,
            discovery,
            calibration,
            clock,
            TimeZone.UTC,
            updateCheck,
            INSTALLED,
        )
    }

    @Test
    fun theRememberedPianoIsReconnectedAndShownConnectingUntilItIsListed() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val vm =
                reconnecting(
                    discovery =
                        object : MidiDiscoveryPort by piano {
                            override suspend fun connect(device: MidiDevice) {
                                gate.await()
                                piano.connect(device)
                            }
                        },
                )
            runCurrent()
            assertTrue(vm.state.value.connecting)
            assertNull(vm.state.value.piano)

            gate.complete(Unit)
            runCurrent()

            assertFalse(vm.state.value.connecting)
            assertEquals(PianoChip(remembered, Transport.BLE, null), vm.state.value.piano)
            assertEquals(listOf(remembered), piano.connectCalls)
        }

    @Test
    fun aFailedReconnectLeavesNoPianoAndNoRetry() =
        runTest(dispatcher) {
            piano.bluetoothProblem = BluetoothUnavailableException.Reason.PERMISSION_DENIED
            val vm = reconnecting()
            runCurrent()
            assertFalse(vm.state.value.connecting)
            assertNull(vm.state.value.piano)
            piano.bluetoothProblem = null
            val unreachable =
                reconnecting(
                    discovery =
                        object : MidiDiscoveryPort by piano {
                            override suspend fun connect(device: MidiDevice) {
                                piano.connectCalls += device
                                throw MidiDeviceUnavailableException(device.id)
                            }
                        },
                )
            runCurrent()
            assertFalse(unreachable.state.value.connecting)
            assertNull(unreachable.state.value.piano)
            assertEquals(listOf(remembered, remembered), piano.connectCalls)
        }

    @Test
    fun aReconnectThatNeverReturnsGivesUpAfterTheTimeout() =
        runTest(dispatcher) {
            val vm =
                reconnecting(
                    discovery =
                        object : MidiDiscoveryPort by piano {
                            override suspend fun connect(device: MidiDevice) = awaitCancellation()
                        },
                )
            runCurrent()
            assertTrue(vm.state.value.connecting)

            advanceTimeBy(Tunables.RECONNECT_LAST_PIANO_TIMEOUT_MS + 1)

            assertFalse(vm.state.value.connecting)
            assertNull(vm.state.value.piano)
        }

    @Test
    fun aRememberedPianoThatIsAlreadyListedIsNotConnectedAgain() =
        runTest(dispatcher) {
            settings.update { it.copy(lastPiano = piano.device) }
            val vm = viewModel()
            assertFalse(vm.state.value.connecting)
            assertEquals(
                piano.device,
                vm.state.value.piano
                    ?.device,
            )
            assertEquals(emptyList(), piano.connectCalls)
        }

    @Test
    fun nothingRememberedMeansNoConnect() =
        runTest(dispatcher) {
            val vm = reconnecting(remember = false)
            runCurrent()
            assertFalse(vm.state.value.connecting)
            assertNull(vm.state.value.piano)
            assertEquals(emptyList(), piano.connectCalls)
        }

    @Test
    fun anUnreadableStoreSkipsTheReconnect() =
        runTest(dispatcher) {
            val vm = reconnecting(remember = false)
            settings.failing = true
            runCurrent()
            assertFalse(vm.state.value.connecting)
            assertEquals(emptyList(), piano.connectCalls)
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
                    ble,
                    calibration,
                    clock,
                    TimeZone.UTC,
                    updateCheck,
                    INSTALLED,
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
                    other,
                    calibration,
                    clock,
                    TimeZone.UTC,
                    updateCheck,
                    INSTALLED,
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

    @Test
    fun aRunAfterMidnightCountsInThisWeeksDots() =
        runTest(dispatcher) {
            // Monday 02:00: the learner day is still Sunday, but a run at 01:00 on Monday is a Monday practice day.
            val monday = FakeWallClock(Instant.parse("2026-11-16T02:00:00Z"))
            playedOn("2026-11-16", id = "early", hour = "01")
            val vm =
                TodayViewModel(
                    LearnerModel(Curriculum.graph, learnerRepository),
                    settings,
                    attempts,
                    piano,
                    piano,
                    calibration,
                    monday,
                    TimeZone.UTC,
                    updateCheck,
                    INSTALLED,
                ).also { runCurrent() }

            assertEquals(1, vm.state.value.daysThisWeek)
            assertEquals(1, vm.state.value.practicedDays)
        }

    @Test
    fun nothingDueOrNewStillOffersTheSongOfALearnedUnit() =
        runTest(dispatcher) {
            givenStates(
                *Curriculum.graph.skills
                    .map { skillState(it.id, dueDay = LocalDate(2026, 12, 1), learned = true) }
                    .toTypedArray(),
            )
            val vm = viewModel()

            val song = assertIs<Card.Song>(assertNotNull(vm.state.value.session).cards.single())
            assertEquals(DailyBudget.MIN_20.minutes, song.minutes)
        }

    @Test
    fun aCleanWaitRunMovesTheSongToTheNextRung() =
        runTest(dispatcher) {
            givenStates(
                skillState(Skills.FINGER_NUMBER_TUNES.id, dueDay = LocalDate(2026, 12, 1), learned = true),
                skillState(Skills.RH_C_POSITION.id, dueDay = LocalDate(2026, 12, 1), learned = true),
                skillState(Skills.STEADY_BEAT.id, dueDay = LocalDate(2026, 12, 1), learned = true),
            )
            val first =
                assertIs<Card.Song>(
                    assertNotNull(viewModel().state.value.session).cards.filterIsInstance<Card.Song>().single(),
                )
            playedOn("2026-11-10", band = Band.CLEAN, passageId = first.passage.id)
            val vm = viewModel()

            val song = assertNotNull(vm.state.value.session).cards.filterIsInstance<Card.Song>().single()
            assertEquals(first.passage.id, song.passage.id)
            assertEquals(TempoRung.P60, song.rung)
        }

    @Test
    fun `checks at most once per day`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            viewModel()
            viewModel()
            assertEquals(1, updateCheck.calls)
            assertEquals(today, settings.settings.first().lastUpdateCheck)

            clock.advance(1.days)
            val tomorrow = viewModel()
            assertEquals(2, updateCheck.calls)
            assertEquals(RELEASE_020, tomorrow.state.value.update)
        }

    @Test
    fun `a day starts at the learner day, not at midnight`() =
        runTest(dispatcher) {
            val twoAm = FakeWallClock(Instant.parse("2026-11-19T02:00:00Z"))
            settings.update { it.copy(lastUpdateCheck = today) }
            TodayViewModel(
                LearnerModel(Curriculum.graph, learnerRepository),
                settings,
                attempts,
                piano,
                piano,
                calibration,
                twoAm,
                TimeZone.UTC,
                updateCheck,
                INSTALLED,
            )
            runCurrent()
            assertEquals(0, updateCheck.calls)
        }

    @Test
    fun `a request cut short by leaving Today gives the day back`() =
        runTest(dispatcher) {
            val yesterday = LocalDate(2026, 11, 17)
            settings.update { it.copy(lastUpdateCheck = yesterday) }
            val leaving =
                object : UpdateCheckPort {
                    override suspend fun latest(): Release? = throw CancellationException("left Today")
                }
            TodayViewModel(
                LearnerModel(Curriculum.graph, learnerRepository),
                settings,
                attempts,
                piano,
                piano,
                calibration,
                clock,
                TimeZone.UTC,
                leaving,
                INSTALLED,
            )
            runCurrent()
            assertEquals(yesterday, settings.settings.first().lastUpdateCheck)

            updateCheck.release = RELEASE_020
            assertEquals(RELEASE_020, viewModel().state.value.update)
            assertEquals(1, updateCheck.calls)
        }

    @Test
    fun `a day claimed by another Today screen meanwhile is not asked again`() =
        runTest(dispatcher) {
            settings.update { it.copy(lastUpdateCheck = today) }
            // This screen's first read is stale (the day looks unchecked); the claiming update sees the real store.
            val staleRead =
                object : SettingsRepositoryPort by settings {
                    override val settings: Flow<Settings> = flowOf(Settings(weeklyGoalDays = 4))
                }
            TodayViewModel(
                LearnerModel(Curriculum.graph, learnerRepository),
                staleRead,
                attempts,
                piano,
                piano,
                calibration,
                clock,
                TimeZone.UTC,
                updateCheck,
                INSTALLED,
            )
            runCurrent()
            assertEquals(0, updateCheck.calls)
            assertEquals(today, settings.settings.first().lastUpdateCheck)
        }

    @Test
    fun `no request when the setting is off`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            settings.update { it.copy(updateCheckOn = false, latestRelease = RELEASE_020) }
            val vm = viewModel()
            assertEquals(0, updateCheck.calls)
            assertNull(vm.state.value.update)
        }

    @Test
    fun `banner when newer`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            assertEquals(RELEASE_020, viewModel().state.value.update)
            // Reopened the same day: no request, the stored release still shows.
            assertEquals(RELEASE_020, viewModel().state.value.update)
            assertEquals(1, updateCheck.calls)
        }

    @Test
    fun `no banner when the release is not newer`() =
        runTest(dispatcher) {
            updateCheck.release = Release(INSTALLED, RELEASE_020.pageUrl)
            assertNull(viewModel().state.value.update)
        }

    @Test
    fun `not now hides until a newer version`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            val vm = viewModel()
            vm.dismissUpdate()
            runCurrent()
            assertNull(vm.state.value.update)
            assertEquals("0.2.0", settings.settings.first().dismissedUpdate)
            assertNull(viewModel().state.value.update)

            clock.advance(1.days)
            updateCheck.release = Release("0.2.1", RELEASE_020.pageUrl)
            assertEquals(
                "0.2.1",
                viewModel()
                    .state.value.update
                    ?.version,
            )
        }

    @Test
    fun `dismissing with no banner does nothing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dismissUpdate()
            runCurrent()
            assertNull(settings.settings.first().dismissedUpdate)
        }

    @Test
    fun `failure shows nothing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertNull(vm.state.value.update)
            assertFalse(vm.state.value.storageFailed)
            assertEquals(1, updateCheck.calls)
        }

    @Test
    fun `a settings store that fails shows no banner and no update error`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            settings.failing = true
            val vm = viewModel()
            assertNull(vm.state.value.update)
            assertEquals(0, updateCheck.calls)
        }

    @Test
    fun `dismissing while the store fails still hides the banner`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            val vm = viewModel()
            settings.failing = true
            vm.dismissUpdate()
            runCurrent()
            assertNull(vm.state.value.update)
        }
}
