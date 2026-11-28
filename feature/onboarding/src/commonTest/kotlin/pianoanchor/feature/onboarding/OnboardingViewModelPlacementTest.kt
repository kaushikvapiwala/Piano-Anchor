package pianoanchor.feature.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.PLACEMENT_ATTEMPT_ID
import pianoanchor.engine.onboarding.PlacementTest
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
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

/** The placement test of the onboarding ("Test me") and the start choices. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelPlacementTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val learnerRepository = FakeLearnerRepository()
    private val settings = FakeSettingsRepository()
    private val clock = FakeWallClock(Instant.parse("2026-11-18T10:00:00Z"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.play(vararg notes: MidiNote) {
        notes.forEach {
            piano.press(it)
            runCurrent()
        }
    }

    /** Connects, passes the echo and continues to the start step. */
    private fun TestScope.connectAndListen(): OnboardingViewModel {
        val vm =
            OnboardingViewModel(
                piano,
                piano,
                piano,
                LearnerModel(Curriculum.graph, learnerRepository),
                settings,
                clock,
                TimeZone.UTC,
            )
        runCurrent()
        play(MidiNote(60))
        advanceTimeBy(PLAYBACK_MS)
        runCurrent()
        return vm
    }

    private fun TestScope.toStartPoint(vm: OnboardingViewModel) {
        play(MidiNote(60), MidiNote(64), MidiNote(67))
        vm.continueToStartPoint()
        runCurrent()
    }

    /** Plays the test pieces with [bands]: each run finishes with its band, and the learner taps on after each. */
    private fun TestScope.playTest(
        vm: OnboardingViewModel,
        vararg bands: Band,
    ) {
        for ((i, band) in bands.withIndex()) {
            assertEquals(
                PlacementTest.passageIds[i],
                vm.state.value.piece
                    ?.passageId,
            )
            assertNull(vm.state.value.verdict)
            vm.placementRunFinished(band)
            vm.continueTest()
            runCurrent()
        }
    }

    private fun placedSkills(units: Int) =
        Curriculum.units
            .take(units)
            .flatMap { it.skills }
            .toSet()

    @Test
    fun `testMePlaysUnit0First`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)

            vm.testMe()
            runCurrent()

            val piece = assertNotNull(vm.state.value.piece)
            assertEquals(PlacementPiece(1, 7, "builtin:all-the-cs", "All the Cs"), piece)
            assertNull(vm.state.value.verdict)
            assertFalse(vm.state.value.finished)
            assertTrue(learnerRepository.states().isEmpty())
        }

    @Test
    fun `aPassedPieceSaysSoAndTheNextOneFollows`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()

            vm.placementRunFinished(Band.GOOD)
            assertEquals(PlacementVerdict.PASSED, vm.state.value.verdict)
            assertEquals(
                1,
                vm.state.value.piece
                    ?.number,
            )
            vm.continueTest()

            assertEquals(
                2,
                vm.state.value.piece
                    ?.number,
            )
            assertEquals(
                "builtin:mary-had-a-little-lamb",
                vm.state.value.piece
                    ?.passageId,
            )
            assertNull(vm.state.value.verdict)
        }

    @Test
    fun `aFailureEndsTheTestAndPlacesEarlierUnits`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()
            playTest(vm, Band.CLEAN)

            vm.placementRunFinished(Band.KEEP_PRACTISING)
            assertEquals(PlacementVerdict.START_HERE, vm.state.value.verdict)
            assertFalse(vm.state.value.finished)
            vm.continueTest()
            runCurrent()

            val placed = learnerRepository.states()
            assertEquals(placedSkills(1), placed.keys)
            assertTrue(placed.values.all { it.learned })
            assertEquals(LocalDate(2026, 11, 18), placed.values.first().introducedDay)
            assertEquals(PLACEMENT_ATTEMPT_ID, learnerRepository.reviews(placed.keys.first()).single().attemptId)
            assertTrue(settings.settings.first().onboarded)
            assertTrue(vm.state.value.finished)
        }

    @Test
    fun `allPassedPlacesTheWholeCourse`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()
            playTest(vm, Band.CLEAN, Band.GOOD, Band.GOOD, Band.CLEAN, Band.GOOD, Band.GOOD)

            vm.placementRunFinished(Band.CLEAN)
            assertEquals(PlacementVerdict.START_HERE, vm.state.value.verdict)
            vm.continueTest()
            runCurrent()

            assertEquals(placedSkills(7), learnerRepository.states().keys)
            assertTrue(vm.state.value.finished)
        }

    @Test
    fun `aFirstFailurePlacesNothing`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()

            vm.placementRunFinished(Band.KEEP_PRACTISING)
            vm.continueTest()
            runCurrent()

            assertTrue(learnerRepository.states().isEmpty())
            assertTrue(vm.state.value.finished)
        }

    @Test
    fun `stopHereCountsAsFailure`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()
            playTest(vm, Band.GOOD, Band.GOOD)

            vm.stopHere()
            assertEquals(PlacementVerdict.START_HERE, vm.state.value.verdict)
            vm.continueTest()
            runCurrent()

            assertEquals(placedSkills(2), learnerRepository.states().keys)
        }

    @Test
    fun `aGoodFirstPieceAndAFailedSecondLeaveUnit0Placed`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()
            playTest(vm, Band.GOOD)

            vm.placementRunFinished(Band.KEEP_PRACTISING)
            vm.continueTest()
            runCurrent()

            val model = LearnerModel(Curriculum.graph, learnerRepository)
            assertEquals(placedSkills(1), model.placedSkills())
        }

    @Test
    fun `aStorageFailureAfterTheTestIsRetriedFromTheVerdict`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()
            playTest(vm, Band.GOOD)
            settings.failing = true
            vm.stopHere()

            vm.continueTest()
            runCurrent()
            assertTrue(vm.state.value.storageFailed)
            assertFalse(vm.state.value.finished)
            settings.failing = false
            vm.continueTest()
            runCurrent()

            assertTrue(vm.state.value.finished)
            assertEquals(placedSkills(1), learnerRepository.states().keys)
        }

    @Test
    fun `a second start while saving is ignored`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()
            playTest(vm, Band.GOOD, Band.GOOD)
            vm.stopHere()

            vm.continueTest()
            vm.continueTest()
            runCurrent()

            assertEquals(placedSkills(2), learnerRepository.states().keys)
        }

    @Test
    fun `a test under way ignores the other choices`() =
        runTest(dispatcher) {
            val vm = connectAndListen()
            toStartPoint(vm)
            vm.testMe()
            runCurrent()
            val piece = vm.state.value.piece

            vm.testMe()
            vm.startFromBeginning()
            vm.continueTest() // no verdict yet
            runCurrent()

            assertEquals(piece, vm.state.value.piece)
            assertFalse(vm.state.value.finished)

            vm.placementRunFinished(Band.GOOD)
            vm.placementRunFinished(Band.KEEP_PRACTISING) // a second report for the same piece
            assertEquals(PlacementVerdict.PASSED, vm.state.value.verdict)
        }

    private companion object {
        /** Three notes 500 ms apart, the last held 400 ms. */
        const val PLAYBACK_MS = 1_400L
    }
}
