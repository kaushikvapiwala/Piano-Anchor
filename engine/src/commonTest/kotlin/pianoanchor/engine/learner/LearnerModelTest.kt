package pianoanchor.engine.learner

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rating
import pianoanchor.core.model.RunMode
import pianoanchor.engine.Tunables
import pianoanchor.engine.scheduler.Fsrs
import pianoanchor.engine.scheduler.FsrsParameters
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.passageOf
import pianoanchor.testing.skillGraphOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LearnerModelTest {
    private val fsrs = Fsrs()
    private val repository = FakeLearnerRepository()
    private val graph =
        skillGraphOf(
            "a" to emptySet(),
            "b" to setOf("a"),
            "c" to setOf("b"),
            "d" to emptySet(),
            "e" to emptySet(),
        )
    private val model = LearnerModel(graph, repository)
    private val day1 = LocalDate(2026, 11, 1)
    private var attempts = 0

    private fun day(offset: Int) = day1.plus(offset, DateTimeUnit.DAY)

    private fun passage(vararg skills: String): Passage = passageOf(setOf(MidiNote(60)), skills = skills.toSet())

    /** A run of [band]: by default an unaided perform run at 60 BPM, so a clean one rates Good. */
    private fun attempt(
        band: Band,
        unaided: Boolean = true,
        timed: Boolean = true,
    ) = Attempt(
        id = "attempt-${++attempts}",
        passageId = "test:passage",
        deviceId = "usb-1",
        startedAt = Instant.parse("2026-11-01T10:00:00Z"),
        durationNanos = 1,
        stepCount = 1,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = band,
        events = emptyList(),
        tempoBpm = if (timed) 60 else null,
        playedNotes = emptyList(),
        hintShown = !unaided,
        mode = if (unaided) RunMode.PERFORM else RunMode.PRACTICE,
    )

    private suspend fun check(
        skill: String,
        offset: Int,
        band: Band = Band.CLEAN,
        unaided: Boolean = true,
    ) = model.record(passage(skill), attempt(band, unaided), day(offset))

    private suspend fun state(skill: String) = repository.states().getValue(skill)

    @Test
    fun `spot attempts are not cold checks`() =
        runTest {
            val spot = attempt(Band.CLEAN).copy(spot = BarRange(1, 2))
            assertEquals(emptyList(), model.record(passage("a"), spot, day(0)))
            assertTrue(repository.states().isEmpty())
            // The day's cold check is still to come: a full run after the loop counts.
            assertEquals(1, check("a", 0).size)
        }

    @Test
    fun `first run of the day records one review per passage skill`() =
        runTest {
            val reviews = model.record(passage("a", "d"), attempt(Band.CLEAN), day(0))

            assertEquals(listOf("a", "d"), reviews.map { it.skillId })
            assertEquals(listOf("a", "d"), repository.states().keys.sorted())
            assertEquals(1, repository.reviews("a").size)
        }

    @Test
    fun `second run the same day records nothing`() =
        runTest {
            model.record(passage("a"), attempt(Band.KEEP_PRACTISING), day(0))

            val again = model.record(passage("a", "d"), attempt(Band.CLEAN), day(0))

            assertEquals(listOf("d"), again.map { it.skillId })
            assertEquals(Rating.AGAIN, repository.reviews("a").single().rating)
            assertEquals(emptyList(), model.record(passage("a", "d"), attempt(Band.CLEAN), day(0)))
        }

    @Test
    fun `a day before the last review records nothing`() =
        runTest {
            check("a", 3)

            assertEquals(emptyList(), check("a", 1))
            assertEquals(day(3), state("a").lastReviewDay)
        }

    @Test
    fun `first review uses initial, later ones use next with the day gap`() =
        runTest {
            check("a", 0, Band.CLEAN)
            val first = fsrs.initial(Rating.GOOD)
            assertEquals(first, state("a").memory)
            assertEquals(day(0), state("a").introducedDay)

            check("a", 4, Band.GOOD)

            assertEquals(fsrs.next(first, 4, Rating.HARD), state("a").memory)
            assertEquals(day(0), state("a").introducedDay)
            assertEquals(day(4), state("a").lastReviewDay)
        }

    @Test
    fun `due day is day plus interval`() =
        runTest {
            check("a", 2)

            val memory = state("a").memory
            assertEquals(day(2).plus(fsrs.intervalDays(memory), DateTimeUnit.DAY), state("a").dueDay)
        }

    @Test
    fun `twoHardChecksAndNoCleanOneIsNotLearned`() =
        runTest {
            check("a", 0, Band.GOOD)
            check("a", 3, Band.GOOD)

            val s = state("a")
            assertEquals(2, s.recalledColdChecks)
            assertEquals(0, s.cleanColdChecks)
            assertTrue(s.memory.stability >= Tunables.LEARNED_MIN_STABILITY_DAYS)
            assertFalse(s.learned)
        }

    @Test
    fun `hard checks alone count as learned once stability reaches the mature bar`() =
        runTest {
            // The #73 seed-11 stall: recalled every time, never clean. Review whenever due.
            var offset = 0
            check("a", offset, Band.GOOD)
            while (state("a").memory.stability < Tunables.LEARNED_MATURE_STABILITY_DAYS) {
                assertFalse(state("a").learned, "not learned at stability ${state("a").memory.stability}")
                offset += fsrs.intervalDays(state("a").memory)
                check("a", offset, Band.GOOD)
            }

            val s = state("a")
            assertEquals(0, s.cleanColdChecks)
            assertTrue(s.recalledColdChecks >= Tunables.LEARNED_MIN_RECALLED_COLD_CHECKS)
            assertTrue(s.learned)
        }

    @Test
    fun `one hard check with mature stability is not learned`() =
        runTest {
            // Strong learner: a first stability above the mature bar, but only one recall.
            val weights =
                FsrsParameters.DEFAULT.w
                    .toMutableList()
                    .apply { this[1] = 30.0 }
            val strong = LearnerModel(graph, repository, Fsrs(FsrsParameters(weights)))
            strong.record(passage("a"), attempt(Band.GOOD), day(0))

            val s = state("a")
            assertTrue(s.memory.stability >= Tunables.LEARNED_MATURE_STABILITY_DAYS)
            assertEquals(1, s.recalledColdChecks)
            assertFalse(s.learned)
        }

    @Test
    fun `oneCleanAndOneHardCheckIsLearnedOnceStable`() =
        runTest {
            check("a", 0, Band.CLEAN)
            assertFalse(state("a").learned)
            check("a", 3, Band.GOOD)

            val s = state("a")
            assertEquals(2, s.recalledColdChecks)
            assertEquals(1, s.cleanColdChecks)
            assertTrue(s.memory.stability >= Tunables.LEARNED_MIN_STABILITY_DAYS)
            assertTrue(s.learned)
        }

    @Test
    fun `two aided clean checks with stability met are not learned without an unaided check`() =
        runTest {
            check("a", 0, unaided = false)
            check("a", 3, unaided = false)

            val s = state("a")
            assertEquals(2, s.recalledColdChecks)
            assertEquals(0, s.unaidedRecalledChecks)
            assertTrue(s.memory.stability >= Tunables.LEARNED_MIN_STABILITY_DAYS)
            assertFalse(s.learned)
        }

    @Test
    fun `an aided check is rated hard so it is never a clean check`() =
        runTest {
            check("a", 0, unaided = false)

            assertEquals(Rating.HARD, repository.reviews("a").single().rating)
            assertFalse(repository.reviews("a").single().unaided)
            assertEquals(0, state("a").cleanColdChecks)
        }

    @Test
    fun `learned after one unaided recalled check`() =
        runTest {
            check("a", 0, unaided = false)
            check("a", 3, unaided = false)
            assertFalse(state("a").learned)

            check("a", 6)

            val s = state("a")
            assertEquals(1, s.unaidedRecalledChecks)
            assertTrue(s.learned)
        }

    @Test
    fun `the unaided check can be the clean one`() =
        runTest {
            check("a", 0, Band.GOOD, unaided = false)
            check("a", 3)

            val s = state("a")
            assertEquals(1, s.cleanColdChecks)
            assertEquals(1, s.unaidedRecalledChecks)
            assertTrue(s.learned)
        }

    @Test
    fun `an unaided lapse is not an unaided recalled check`() =
        runTest {
            check("a", 0, Band.KEEP_PRACTISING)

            assertEquals(0, state("a").unaidedRecalledChecks)
        }

    @Test
    fun `learned stays learned and replay keeps pre-migration reviews that count as unaided`() =
        runTest {
            check("a", 0)
            check("a", 3)
            model.place(setOf("d"), day(0))
            val before = repository.states()
            assertTrue(before.getValue("a").learned)
            assertTrue(before.getValue("d").learned)

            model.replay()

            assertEquals(before, repository.states())
        }

    @Test
    fun `learnedSkills lists only the learned ones`() =
        runTest {
            check("a", 0, Band.CLEAN)
            check("a", 3, Band.GOOD)
            check("d", 0, Band.GOOD)

            assertEquals(setOf("a"), model.learnedSkills())
        }

    @Test
    fun `theSimulationStallCaseIsLearned`() =
        runTest {
            check("a", 0, Band.GOOD)
            check("a", 1, Band.CLEAN)
            check("a", 6, Band.GOOD)

            val s = state("a")
            assertEquals(3, s.recalledColdChecks)
            assertEquals(1, s.cleanColdChecks)
            assertTrue(s.learned)
        }

    @Test
    fun `againCountsAsNeitherRecalledNorClean`() =
        runTest {
            check("a", 0, Band.CLEAN)
            check("a", 3, Band.KEEP_PRACTISING)

            val s = state("a")
            assertEquals(1, s.recalledColdChecks)
            assertEquals(1, s.cleanColdChecks)
            assertFalse(s.learned)
        }

    @Test
    fun `learned after two clean cold checks on different days with stability above the minimum`() =
        runTest {
            check("a", 0)
            check("a", 3)

            val s = state("a")
            assertEquals(2, s.cleanColdChecks)
            assertTrue(s.memory.stability >= Tunables.LEARNED_MIN_STABILITY_DAYS)
            assertTrue(s.learned)
        }

    @Test
    fun `one clean check is not learned`() =
        runTest {
            check("a", 0)
            assertFalse(state("a").learned)
            assertEquals(1, state("a").cleanColdChecks)
        }

    @Test
    fun `two clean checks with low stability are not learned`() =
        runTest {
            // A slow learner: weights with a small first stability (w2) and a small recall gain (w8).
            val weights =
                FsrsParameters.DEFAULT.w.toMutableList().apply {
                    this[2] = 1.0
                    this[8] = -3.0
                }
            val slow = LearnerModel(graph, repository, Fsrs(FsrsParameters(weights)))
            slow.record(passage("a"), attempt(Band.CLEAN), day(0))
            slow.record(passage("a"), attempt(Band.CLEAN), day(1))

            val s = state("a")
            assertEquals(2, s.cleanColdChecks)
            assertTrue(s.memory.stability < Tunables.LEARNED_MIN_STABILITY_DAYS)
            assertFalse(s.learned)
        }

    @Test
    fun `learned stays learned after a lapse`() =
        runTest {
            check("a", 0)
            check("a", 3)
            assertTrue(state("a").learned)

            check("a", 40, Band.KEEP_PRACTISING)

            val s = state("a")
            assertTrue(s.learned)
            assertEquals(2, s.cleanColdChecks)
            assertEquals(2, s.recalledColdChecks)
            assertTrue(s.dueDay <= day(42))
        }

    @Test
    fun `plan orders due skills by lowest retrievability`() =
        runTest {
            check("d", 0, Band.CLEAN)
            check("e", 0, Band.KEEP_PRACTISING)
            check("a", 0, Band.GOOD)

            val plan = model.plan(day(30))

            val retrievability = listOf("a", "d", "e").associateWith { fsrs.retrievability(state(it).memory, 30) }
            val expected = retrievability.entries.sortedBy { it.value }.map { it.key }
            assertEquals(expected, plan.due.map { it.skillId })
            assertEquals(3, plan.due.size)
        }

    @Test
    fun `plan breaks retrievability ties by id`() =
        runTest {
            check("e", 0)
            check("d", 0)

            assertEquals(listOf("d", "e"), model.plan(day(30)).due.map { it.skillId })
        }

    @Test
    fun `plan skips skills that are not due yet`() =
        runTest {
            check("d", 0)

            assertEquals(emptyList(), model.plan(day(0)).due)
        }

    @Test
    fun `plan caps due at 12`() =
        runTest {
            val many = (1..15).map { "s%02d".format(it) }
            val bigModel = LearnerModel(skillGraphOf(*many.map { it to emptySet<String>() }.toTypedArray()), repository)
            many.forEach { bigModel.record(passage(it), attempt(Band.CLEAN), day(0)) }

            val plan = bigModel.plan(day(60))

            assertEquals(Tunables.MAX_DUE_PER_DAY, plan.due.size)
            assertEquals(emptyList(), plan.new)
        }

    @Test
    fun `plan offers new skills only while due is under the cap`() =
        runTest {
            val many = (1..13).map { "s%02d".format(it) }
            val skills = many.map { it to emptySet<String>() } + ("zz" to emptySet())
            val bigModel = LearnerModel(skillGraphOf(*skills.toTypedArray()), repository)
            many
                .take(
                    Tunables.MAX_DUE_PER_DAY - 1,
                ).forEach { bigModel.record(passage(it), attempt(Band.CLEAN), day(0)) }

            val eleven = bigModel.plan(day(60))
            assertEquals(Tunables.MAX_DUE_PER_DAY - 1, eleven.due.size)
            assertEquals(listOf("s12", "s13"), eleven.new.map { it.id })

            bigModel.record(passage("s12"), attempt(Band.CLEAN), day(0))
            val twelve = bigModel.plan(day(60))
            assertEquals(Tunables.MAX_DUE_PER_DAY, twelve.due.size)
            assertEquals(emptyList(), twelve.new)
        }

    @Test
    fun `plan caps new at 2 per day including those introduced today`() =
        runTest {
            assertEquals(listOf("a", "d"), model.plan(day(0)).new.map { it.id })

            check("d", 0)

            assertEquals(listOf("a"), model.plan(day(0)).new.map { it.id })

            check("a", 0)

            assertEquals(emptyList(), model.plan(day(0)).new)
            // Next day: two more may start, in graph order, never the ones already introduced.
            assertEquals(listOf("e"), model.plan(day(1)).new.map { it.id })
        }

    @Test
    fun `plan never offers a skill before its prerequisites are learned`() =
        runTest {
            check("a", 0)
            assertTrue("b" !in model.plan(day(1)).new.map { it.id })

            check("a", 3)
            assertTrue(state("a").learned)

            assertTrue("b" in model.plan(day(4)).new.map { it.id })
            assertTrue("c" !in model.plan(day(4)).new.map { it.id })
        }

    @Test
    fun `replay rebuilds identical states`() =
        runTest {
            check("a", 0)
            check("a", 3, Band.GOOD)
            check("a", 9, Band.KEEP_PRACTISING)
            check("d", 1)
            val before = repository.states()

            repository.replaceStates(emptyList())
            model.replay()

            assertEquals(before, repository.states())
        }

    @Test
    fun `replay keeps skills that are no longer in the graph`() =
        runTest {
            val old = LearnerModel(skillGraphOf("gone" to emptySet()), repository)
            old.record(passage("gone"), attempt(Band.CLEAN), day(0))
            val before = repository.states()

            model.replay()

            assertEquals(before, repository.states())
        }

    @Test
    fun reviewDaysListsTheDaysOfEarReviewsFromTheGivenDayOn() =
        runTest {
            model.recordEarSet("a", Band.CLEAN, LocalDate(2026, 11, 10), "1")
            model.recordEarSet("b", Band.CLEAN, LocalDate(2026, 11, 13), "2")

            assertEquals(setOf(LocalDate(2026, 11, 13)), model.reviewDays(LocalDate(2026, 11, 12)))
            assertEquals(emptySet(), model.reviewDays(LocalDate(2026, 11, 14)))
        }

    @Test
    fun reviewDaysListsTheDaysOfReviewQuizzes() =
        runTest {
            model.recordQuiz("a", Rating.GOOD, LocalDate(2026, 11, 10), "1")
            model.recordQuiz("b", Rating.GOOD, LocalDate(2026, 11, 13), "2")

            assertEquals(setOf(LocalDate(2026, 11, 13)), model.reviewDays(LocalDate(2026, 11, 12)))
        }

    @Test
    fun reviewDaysListsEarAndQuizDaysTogether() =
        runTest {
            model.recordEarSet("a", Band.CLEAN, LocalDate(2026, 11, 10), "1")
            model.recordQuiz("b", Rating.GOOD, LocalDate(2026, 11, 13), "2")

            assertEquals(
                setOf(LocalDate(2026, 11, 10), LocalDate(2026, 11, 13)),
                model.reviewDays(LocalDate(2026, 11, 1)),
            )
        }

    @Test
    fun reviewDaysIgnoresPassageAndPlacementReviews() =
        runTest {
            val thirteenth = LocalDate(2026, 11, 13)
            model.place(setOf("d"), thirteenth)
            model.record(passage("a"), attempt(Band.CLEAN), thirteenth)

            assertEquals(emptySet(), model.reviewDays(LocalDate(2026, 11, 1)))
        }

    @Test
    fun reviewDaysIsEmptyWithNoReviews() =
        runTest { assertEquals(emptySet(), model.reviewDays(LocalDate(2026, 11, 1))) }
}
