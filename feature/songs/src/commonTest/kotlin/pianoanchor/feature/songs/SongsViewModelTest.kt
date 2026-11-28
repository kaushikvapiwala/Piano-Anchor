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
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.Rating
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.PieceStatus
import pianoanchor.engine.content.PieceView
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.CourseProgress
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
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
class SongsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val attempts = FakeAttemptRepository()
    private val learnerRepository = FakeLearnerRepository()
    private val piano = FakePiano(device = MidiDevice("usb:Roland/FP-30X", "FP-30X"))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() =
        SongsViewModel(attempts, LearnerModel(Curriculum.graph, learnerRepository), piano, TimeZone.UTC).also {
            runCurrent()
        }

    private suspend fun playClean(
        id: String,
        passageId: String,
        rung: TempoRung?,
        hintShown: Boolean = false,
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
                hintShown = hintShown,
            ),
        )
    }

    private fun SongsViewModel.piece(id: String): PieceView =
        checkNotNull(state.value.pieces).first { it.passage.id == "builtin:$id" }

    @Test
    fun aPlacedLearnersNextMarkerIsTheStartUnitsFirstConcept() =
        runTest(dispatcher) {
            val unit0 =
                Curriculum.units
                    .first()
                    .skills
                    .toSet()
            LearnerModel(Curriculum.graph, learnerRepository).place(unit0, LocalDate(2026, 11, 15))
            val vm = viewModel()

            assertEquals(Skills.RH_C_POSITION.id, vm.state.value.next)
            assertEquals(unit0, vm.state.value.passed)
        }

    @Test
    fun listsPiecesWithStatus() =
        runTest(dispatcher) {
            playClean("a", "builtin:lightly-row", TempoRung.P60)
            val vm = viewModel()

            assertEquals(PieceStatus.IN_PROGRESS, vm.piece("lightly-row").status)
            assertEquals(PieceStatus.NEW, vm.piece("hot-cross-buns").status)
            assertEquals(PieceStatus.NEW, vm.piece("when-the-saints-go-marching-in").status)
            // Nothing is learned, yet the last piece is as open as the first.
            assertEquals(PieceStatus.NEW, vm.piece("four-chord-walk").status)
            assertEquals(PieceStatus.NEW, vm.piece("jingle-bells").status)
            assertEquals(PieceStatus.NEW, vm.piece("go-tell-aunt-rhody").status)
            assertEquals(13, checkNotNull(vm.state.value.pieces).size)
            assertFalse(vm.state.value.storageFailed)
        }

    @Test
    fun aTapOpensPracticeWithPassageAndRung() =
        runTest(dispatcher) {
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
    fun everyPieceOpensPracticeWhateverIsLearned() =
        runTest(dispatcher) {
            val vm = viewModel()

            val pieces = checkNotNull(vm.state.value.pieces)
            assertTrue(pieces.isNotEmpty())
            pieces.forEach { assertIs<SongsTarget.Practice>(vm.target(it), it.passage.id) }
        }

    @Test
    fun refreshesAfterReturningFromPractice() =
        runTest(dispatcher) {
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
    fun aConceptPassesOnACleanUnaidedWaitRunAndTheFirstUnpassedIsNext() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(emptySet(), vm.state.value.passed)
            assertEquals(Skills.HAND_SHAPE.id, vm.state.value.next)

            // A clean wait-mode run of Middle C march passes middle-c; hand-shape stays next (map order).
            playClean("a", "builtin:middle-c-march", null)
            vm.refresh()
            runCurrent()

            assertEquals(setOf(Skills.MIDDLE_C.id), vm.state.value.passed)
            assertEquals(Skills.HAND_SHAPE.id, vm.state.value.next)
        }

    @Test
    fun aCleanRunWithHintsDoesNotPassTheConcept() =
        runTest(dispatcher) {
            playClean("a", "builtin:middle-c-march", null, hintShown = true)
            val vm = viewModel()
            assertEquals(emptySet(), vm.state.value.passed)
            assertEquals(Skills.HAND_SHAPE.id, vm.state.value.next)
        }

    @Test
    fun aPassedConceptIsLearnedOnceAQuizOnALaterDayIsPassed() =
        runTest(dispatcher) {
            val hot = checkNotNull(Curriculum.passage("builtin:hot-cross-buns"))
            val concepts = hot.skills.filter { id -> CourseProgress.skills(Curriculum).any { it.id == id } }
            playClean("a", hot.id, null)
            val model = LearnerModel(Curriculum.graph, learnerRepository)
            // The run was on 2026-11-15; a quiz the same day doesn't count, one the next day does.
            concepts.forEach { model.recordQuiz(it, Rating.GOOD, LocalDate(2026, 11, 15), "q1") }
            val vm = viewModel()
            // Everything the piece trains but its own concepts stays unpassed, so only the piece's row is checked.
            assertEquals(emptySet(), vm.state.value.learned)
            assertEquals(concepts.toSet(), vm.state.value.passed)
            assertFalse(vm.state.value.isLearned(vm.piece("hot-cross-buns")))

            concepts.forEach { model.recordQuiz(it, Rating.GOOD, LocalDate(2026, 11, 16), "q2") }
            vm.refresh()
            runCurrent()

            assertEquals(concepts.toSet(), vm.state.value.learned)
            assertTrue(vm.state.value.isPassed(vm.piece("hot-cross-buns")))
            assertTrue(vm.state.value.isLearned(vm.piece("hot-cross-buns")))
        }

    @Test
    fun aPieceIsPassedWhenItsConceptsAreAndMarksTheNextConcept() =
        runTest(dispatcher) {
            // The concepts before the first tunes are passed through their first passages.
            CourseProgress
                .skills(Curriculum)
                .takeWhile { it != Skills.FINGER_NUMBER_TUNES }
                .forEach { skill ->
                    val passage = Curriculum.passages.first { skill.id in it.skills }
                    playClean("p-${passage.id}", passage.id, null)
                }
            val vm = viewModel()

            val hot = vm.piece("hot-cross-buns")
            assertEquals(Skills.FINGER_NUMBER_TUNES.id, vm.state.value.next)
            assertTrue(vm.state.value.isNext(hot))
            assertFalse(vm.state.value.isPassed(hot))

            playClean("hot", hot.passage.id, null)
            vm.refresh()
            runCurrent()

            assertTrue(vm.state.value.isPassed(vm.piece("hot-cross-buns")))
            assertFalse(vm.state.value.isNext(vm.piece("hot-cross-buns")))
            // A piece of the More songs unit has no course concept: no tick, no chip.
            val saints = vm.piece("when-the-saints-go-marching-in")
            assertFalse(vm.state.value.isPassed(saints))
            assertFalse(vm.state.value.isNext(saints))
        }

    @Test
    fun aFailedReadClearsThePasses() =
        runTest(dispatcher) {
            playClean("a", "builtin:middle-c-march", null)
            val vm = viewModel()
            assertEquals(setOf(Skills.MIDDLE_C.id), vm.state.value.passed)

            attempts.failing = true
            vm.refresh()
            runCurrent()

            assertEquals(emptySet(), vm.state.value.passed)
            assertNull(vm.state.value.next)
        }

    @Test
    fun anUnreadableStoreShowsAnErrorUntilRefreshed() =
        runTest(dispatcher) {
            attempts.failing = true
            val vm = viewModel()
            assertTrue(vm.state.value.storageFailed)
            assertNull(vm.state.value.pieces)

            attempts.failing = false
            vm.refresh()
            runCurrent()
            assertFalse(vm.state.value.storageFailed)
            assertNotNull(vm.state.value.pieces)
        }
}
