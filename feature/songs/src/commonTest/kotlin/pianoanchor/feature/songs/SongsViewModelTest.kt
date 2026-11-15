package pianoanchor.feature.songs

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
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.SkillState
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.PieceStatus
import pianoanchor.engine.content.PieceView
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SongsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate(2026, 11, 15)
    private val learner = FakeLearnerRepository()
    private val attempts = FakeAttemptRepository()
    private val piano = FakePiano(device = MidiDevice("usb:Roland/FP-30X", "FP-30X"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = SongsViewModel(learner, attempts, piano).also { runCurrent() }

    private fun learned(skillId: String) =
        SkillState(
            skillId = skillId,
            memory = MemoryState(stability = 2.0, difficulty = 5.0),
            introducedDay = day,
            lastReviewDay = day,
            dueDay = day,
            cleanColdChecks = 0,
            recalledColdChecks = 0,
            unaidedRecalledChecks = 0,
            learned = true,
        )

    private suspend fun learnEverything() = learner.replaceStates(Curriculum.graph.skills.map { learned(it.id) })

    private suspend fun playClean(
        id: String,
        passageId: String,
        rung: TempoRung?,
    ) {
        val passage = checkNotNull(Curriculum.passage(passageId))
        attempts.save(
            Attempt(
                id = id,
                passageId = passageId,
                deviceId = "usb-1",
                startedAt = Instant.parse("2026-11-15T09:00:00Z"),
                durationNanos = 1,
                stepCount = 1,
                wrongNotes = 0,
                incompleteChords = 0,
                accuracy = 1.0,
                band = Band.CLEAN,
                events = emptyList(),
                tempoBpm = rung?.bpm(passage),
                playedNotes = emptyList(),
            ),
        )
    }

    private fun SongsViewModel.piece(id: String): PieceView =
        checkNotNull(state.value.pieces).first { it.passage.id == "builtin:$id" }

    @Test
    fun listsPiecesWithStatus() =
        runTest(dispatcher) {
            learner.replaceStates(
                Curriculum.graph.skills
                    .filter { it.id != "chord-a-minor" }
                    .map { learned(it.id) },
            )
            playClean("a", "builtin:lightly-row", TempoRung.P60)
            val vm = viewModel()

            assertEquals(PieceStatus.IN_PROGRESS, vm.piece("lightly-row").status)
            assertEquals(PieceStatus.NEW, vm.piece("hot-cross-buns").status)
            assertEquals(PieceStatus.NEW, vm.piece("when-the-saints-go-marching-in").status)
            // chord-four needs the A minor skill, which isn't learned.
            assertEquals(PieceStatus.LOCKED, vm.piece("four-chord-walk").status)
            assertEquals(10, checkNotNull(vm.state.value.pieces).size)
            assertFalse(vm.state.value.storageFailed)
        }

    @Test
    fun anUnlockedTapOpensPracticeWithPassageAndRung() =
        runTest(dispatcher) {
            learnEverything()
            playClean("a", "builtin:ode-to-joy", TempoRung.P70)
            playClean("b", "builtin:au-clair-de-la-lune", TempoRung.P100)
            val vm = viewModel()

            assertEquals(
                SongsTarget.Practice(piano.device, "builtin:ode-to-joy", TempoRung.P85),
                vm.target(vm.piece("ode-to-joy")),
            )
            assertEquals(
                SongsTarget.Practice(piano.device, "builtin:hot-cross-buns", TempoRung.WAIT),
                vm.target(vm.piece("hot-cross-buns")),
            )
            // Mastered: back to the top rung.
            assertEquals(
                SongsTarget.Practice(piano.device, "builtin:au-clair-de-la-lune", TempoRung.P100),
                vm.target(vm.piece("au-clair-de-la-lune")),
            )

            piano.unplug()
            runCurrent()
            assertEquals(SongsTarget.Pianos, vm.target(vm.piece("ode-to-joy")))
        }

    @Test
    fun aLockedTapDoesNothing() =
        runTest(dispatcher) {
            val vm = viewModel()

            val locked = vm.piece("four-chord-walk")
            assertEquals(PieceStatus.LOCKED, locked.status)
            assertNull(vm.target(locked))
        }

    @Test
    fun refreshesAfterReturningFromPractice() =
        runTest(dispatcher) {
            learnEverything()
            val vm = viewModel()
            assertEquals(PieceStatus.NEW, vm.piece("lightly-row").status)

            playClean("a", "builtin:lightly-row", TempoRung.P60)
            vm.refresh()
            runCurrent()

            val piece = vm.piece("lightly-row")
            assertEquals(PieceStatus.IN_PROGRESS, piece.status)
            assertEquals(1, piece.cleanRungs)
        }

    @Test
    fun anUnreadableStoreShowsAnErrorUntilRefreshed() =
        runTest(dispatcher) {
            learner.failing = true
            val vm = viewModel()
            assertTrue(vm.state.value.storageFailed)
            assertNull(vm.state.value.pieces)

            learner.failing = false
            vm.refresh()
            runCurrent()
            assertFalse(vm.state.value.storageFailed)
            assertNotNull(vm.state.value.pieces)
        }
}
