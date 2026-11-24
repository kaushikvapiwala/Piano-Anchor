package pianoanchor.feature.today

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Band
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiTransport
import pianoanchor.core.model.Rating
import pianoanchor.core.model.RunMode
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.EarSkills
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.sight.SightReadingGenerator
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeWallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest : TodayViewModelFixture() {
    /** A clean wait-mode run of every passage: every course concept is ticked and no Lesson card is offered. */
    private suspend fun tickTheWholeCourse() =
        Curriculum.passages.forEachIndexed { i, passage ->
            playedOn("2026-11-10", id = "tick-$i", passageId = passage.id, band = Band.CLEAN)
        }

    @Test
    fun cardsOpenPracticeWithPassageAndRung() =
        runTest(dispatcher) {
            // Three more due quiz skills sort ahead of middle C by id: they get the quizzes, it gets the cold open.
            givenStates(
                skillState(Skills.BARS_AND_COUNTING.id),
                skillState(Skills.BASS_CLEF_WHOLE_NOTE.id),
                skillState(Skills.BLACK_KEY_GROUPS.id),
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
            val quizzes = assertIs<Card.Quizzes>(session.cards.first())
            assertEquals(3, quizzes.quizzes.size)
            assertEquals(TodayTarget.Quizzes(piano.device, quizzes.quizzes), vm.target(quizzes))
            val cold = session.cards.filterIsInstance<Card.ColdOpen>().single()
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
    fun aQuizOnlyDayCountsAsAPracticeDay() =
        runTest(dispatcher) {
            LearnerModel(Curriculum.graph, learnerRepository)
                .recordQuiz(EarSkills.ECHO_NOTE.id, Rating.GOOD, today, "q")
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

    @Test
    fun theLessonCardOpensTheFirstUntickedConceptOnThePiano() =
        runTest(dispatcher) {
            val vm = viewModel()

            val card = assertIs<Card.Lesson>(assertNotNull(vm.state.value.session).cards.first())
            assertEquals(Skills.HAND_SHAPE, card.skill)
            assertEquals(TodayTarget.Lesson(piano.device, Skills.HAND_SHAPE.id), vm.target(card))
        }

    @Test
    fun theLessonMovesOnOnceAConceptHasACleanWaitRun() =
        runTest(dispatcher) {
            val handShape = checkNotNull(Curriculum.passages.first { Skills.HAND_SHAPE.id in it.skills })
            playedOn("2026-11-10", passageId = handShape.id, band = Band.CLEAN)
            val vm = viewModel()

            val card = assertIs<Card.Lesson>(assertNotNull(vm.state.value.session).cards.first())
            assertEquals(Skills.MIDDLE_C, card.skill)
        }

    @Test
    fun placedLearnerGetsTheStartUnitsFirstLesson() =
        runTest(dispatcher) {
            val unit0 =
                Curriculum.units
                    .first()
                    .skills
                    .toSet()
            LearnerModel(Curriculum.graph, learnerRepository).place(unit0, today)
            val vm = viewModel()

            val card = assertIs<Card.Lesson>(assertNotNull(vm.state.value.session).cards.first())
            assertEquals(Skills.RH_C_POSITION, card.skill)
        }

    private val remembered = MidiDevice("ble:AA:BB:CC:DD:EE:01", "Casio CDP-S160", MidiTransport.BLUETOOTH)

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
            val ble = FakePiano(device = MidiDevice("ble:AA:BB", "Casio", MidiTransport.BLUETOOTH))
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
            // A learner with every skill introduced, nothing due and every concept ticked: no cold open, no lesson.
            tickTheWholeCourse()
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
            tickTheWholeCourse()
            givenStates(
                *Curriculum.graph.skills
                    .map { skillState(it.id, dueDay = LocalDate(2026, 12, 1), learned = true) }
                    .toTypedArray(),
            )
            val vm = viewModel()

            // Everything learned also opens the top sight-reading level: the song shares the day with that card.
            val cards = assertNotNull(vm.state.value.session).cards
            assertEquals(listOf(Card.Song::class, Card.SightReading::class), cards.map { it::class })
            assertEquals(DailyBudget.MIN_20.minutes, cards.sumOf { it.minutes })
            assertEquals(
                0,
                vm.state.value.session
                    ?.lessonMinutes,
            )
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

    private fun sightCard(vm: TodayViewModel) =
        assertNotNull(vm.state.value.session).cards.filterIsInstance<Card.SightReading>().singleOrNull()

    @Test
    fun sightLevelFromStoredAttempts() =
        runTest(dispatcher) {
            givenStates(
                skillState(Skills.STAFF_STEPS.id, dueDay = LocalDate(2026, 12, 1), learned = true),
                skillState(Skills.TREBLE_CLEF.id, dueDay = LocalDate(2026, 12, 1), learned = true),
            )
            // Two clean first plays on earlier days move level 1 up to 2; the card is offered at level 2.
            playedOn("2026-11-16", id = "s-1", passageId = "sight:1:1", band = Band.CLEAN)
            playedOn("2026-11-17", id = "s-2", passageId = "sight:1:2", band = Band.CLEAN)
            val vm = viewModel()

            val card = assertNotNull(sightCard(vm))
            assertEquals(2, card.level)
            assertEquals(SightReadingGenerator.idOf(2, today.toEpochDays()), card.passage.id)
            assertEquals(TodayTarget.SightReading(piano.device, card.passage.id), vm.target(card))
        }

    @Test
    fun noSightCardUntilTheFirstSightSkillIsLearned() =
        runTest(dispatcher) {
            givenStates(skillState(Skills.MIDDLE_C.id))
            assertNull(sightCard(viewModel()))
        }

    @Test
    fun noSightCardOnceTodaysExerciseWasPlayed() =
        runTest(dispatcher) {
            givenStates(skillState(Skills.STAFF_STEPS.id, dueDay = LocalDate(2026, 12, 1), learned = true))
            playedOn("2026-11-18", id = "s-1", passageId = "sight:1:1", band = Band.GOOD)
            assertNull(sightCard(viewModel()))
        }

    @Test
    fun seedIsTheLearnerDay() =
        runTest(dispatcher) {
            givenStates(skillState(Skills.STAFF_STEPS.id, dueDay = LocalDate(2026, 12, 1), learned = true))
            assertEquals(
                SightReadingGenerator.idOf(1, today.toEpochDays()),
                assertNotNull(sightCard(viewModel())).passage.id,
            )
            // 02:00 UTC is still the previous learner day (the day starts at 04:00).
            val early =
                TodayViewModel(
                    LearnerModel(Curriculum.graph, learnerRepository),
                    settings,
                    attempts,
                    piano,
                    piano,
                    calibration,
                    FakeWallClock(Instant.parse("2026-11-18T02:00:00Z")),
                    TimeZone.UTC,
                    updateCheck,
                    INSTALLED,
                ).also { runCurrent() }
            assertEquals(
                SightReadingGenerator.idOf(1, LocalDate(2026, 11, 17).toEpochDays()),
                assertNotNull(sightCard(early)).passage.id,
            )
        }

    @Test
    fun noSightCardAfterMidnightBeforeTheLearnerDayStarts() =
        runTest(dispatcher) {
            givenStates(skillState(Skills.STAFF_STEPS.id, dueDay = LocalDate(2026, 12, 1), learned = true))
            // Played at 23:00 on the 17th; at 01:00 on the 18th it is still the same learner day.
            playedOn("2026-11-17", id = "s-1", passageId = "sight:1:1", hour = "23")
            val vm =
                TodayViewModel(
                    LearnerModel(Curriculum.graph, learnerRepository),
                    settings,
                    attempts,
                    piano,
                    piano,
                    calibration,
                    FakeWallClock(Instant.parse("2026-11-18T01:00:00Z")),
                    TimeZone.UTC,
                    updateCheck,
                    INSTALLED,
                ).also { runCurrent() }

            assertNull(sightCard(vm))
        }
}
