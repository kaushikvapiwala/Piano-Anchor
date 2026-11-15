package pianoanchor.engine.session

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillState
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.DailyPlan
import pianoanchor.engine.matcher.TempoRung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class SessionComposerTest {
    private val day = LocalDate(2026, 11, 15)

    private fun state(
        skill: Skill,
        learned: Boolean = false,
    ) = SkillState(
        skillId = skill.id,
        memory = MemoryState(stability = 2.0, difficulty = 5.0),
        introducedDay = day,
        lastReviewDay = day,
        dueDay = day,
        cleanColdChecks = 0,
        recalledColdChecks = 0,
        learned = learned,
    )

    private fun passage(id: String) = checkNotNull(Curriculum.passage("builtin:$id")) { id }

    private fun compose(
        plan: DailyPlan,
        history: Map<String, PassageHistory> = emptyMap(),
        budget: DailyBudget = DailyBudget.MIN_20,
    ) = SessionComposer.compose(plan, Curriculum, budget, history)

    private fun played(
        epochSecond: Long?,
        vararg tempos: Int,
    ) = PassageHistory(epochSecond?.let(Instant::fromEpochSeconds), tempos.toSet())

    private val everyUnitClean: Map<String, PassageHistory> =
        Curriculum.units
            .flatMap { it.passages }
            .associate { it.id to played(1, TempoRung.P100.bpm(it)!!) }

    @Test
    fun coldOpenIsTheLowestRetrievabilityDueSkill() {
        val plan = DailyPlan(listOf(state(Skills.KB_FIND_C), state(Skills.KB_MIDDLE_C)), emptyList())
        val session = compose(plan)
        val cold = assertIs<Card.ColdOpen>(session.cards.first())
        assertEquals(Skills.KB_FIND_C, cold.skill)
        assertEquals(listOf(state(Skills.KB_MIDDLE_C)), session.moreDue)
    }

    @Test
    fun coldOpenPassageIsTheLeastRecentlyPlayed() {
        val plan = DailyPlan(listOf(state(Skills.KB_MIDDLE_C)), emptyList())
        val march = passage("middle-c-march")
        val which = passage("which-c-is-middle")
        val history = mapOf(march.id to played(100), which.id to played(50))
        val cold = assertIs<Card.ColdOpen>(compose(plan, history).cards.first())
        assertEquals(which, cold.passage)
        assertEquals(Instant.fromEpochSeconds(50), cold.lastPlayed)
        // Never played beats played; a tie goes to curriculum order.
        assertEquals(march, assertIs<Card.ColdOpen>(compose(plan, mapOf(which.id to played(50))).cards.first()).passage)
        assertEquals(march, assertIs<Card.ColdOpen>(compose(plan).cards.first()).passage)
    }

    @Test
    fun coldOpenRungIsTheHighestCleanRung() {
        val plan = DailyPlan(listOf(state(Skills.KB_MIDDLE_C)), emptyList())
        val march = passage("middle-c-march")
        val clean = played(1, TempoRung.P60.bpm(march)!!, TempoRung.P85.bpm(march)!!, 1)
        val cold =
            assertIs<Card.ColdOpen>(
                compose(
                    plan,
                    mapOf(
                        march.id to clean,
                        passage("which-c-is-middle").id to played(2),
                    ),
                ).cards.first(),
            )
        assertEquals(TempoRung.P85, cold.rung)
        assertEquals(TempoRung.WAIT, assertIs<Card.ColdOpen>(compose(plan).cards.first()).rung)
    }

    @Test
    fun lessonNamesItsPrerequisites() {
        val plan = DailyPlan(emptyList(), listOf(Skills.RH_TUNES))
        val lesson = assertIs<Card.Lesson>(compose(plan).cards.first())
        assertEquals(Skills.RH_TUNES, lesson.skill)
        assertEquals(setOf(Skills.RH_SKIPS, Skills.RHYTHM_HALF), lesson.unlockedBy.toSet())
        assertEquals(TempoRung.WAIT, lesson.rung)
        assertEquals(Curriculum.units.flatMap { it.passages }.first { Skills.RH_TUNES.id in it.skills }, lesson.passage)
    }

    @Test
    fun songPicksTheNextRungAboveTheHighestClean() {
        val plan = DailyPlan(listOf(state(Skills.RH_TUNES, learned = true)), emptyList())
        val song = compose(plan).cards.filterIsInstance<Card.Song>().single()
        // A traditional piece of the latest unit with a learned skill, at the first rung with nothing clean.
        assertEquals("builtin:hot-cross-buns", song.passage.id)
        assertEquals(TempoRung.WAIT, song.rung)
        val bpm = TempoRung.P70.bpm(song.passage)!!
        val history = mapOf(song.passage.id to played(1, bpm))
        assertEquals(
            TempoRung.P85,
            compose(plan, history)
                .cards
                .filterIsInstance<Card.Song>()
                .single()
                .rung,
        )
    }

    @Test
    fun songMovesOnWhenTheTraditionalPieceIsCleanAndFallsBackToAnyPiece() {
        val plan = DailyPlan(listOf(state(Skills.RH_TUNES, learned = true)), emptyList())
        val hot = passage("hot-cross-buns")
        val history = mapOf(hot.id to played(1, TempoRung.P100.bpm(hot)!!))
        val song = compose(plan, history).cards.filterIsInstance<Card.Song>().single()
        assertEquals(false, song.passage.id == hot.id)
        // A unit with learned skills whose pieces are all clean hands over to the unit before it.
        val unit1Clean = everyUnitClean.filterKeys { id -> Curriculum.units[1].passages.any { it.id == id } }
        val earlier = compose(plan, unit1Clean).cards.filterIsInstance<Card.Song>().single()
        assertEquals(true, Curriculum.units[0].passages.any { it.id == earlier.passage.id })
    }

    @Test
    fun songSkipsUnitsWithoutLearnedSkillsAndLearnsPrerequisitesOfNewSkills() {
        // Nothing due and a new skill: its learned prerequisites reach back to unit 0.
        val plan = DailyPlan(emptyList(), listOf(Skills.RH_C_POSITION))
        val song = assertIs<Card.Song>(compose(plan).cards.last())
        assertEquals(true, Curriculum.units[0].passages.any { it.id == song.passage.id })
    }

    @Test
    fun minutesSumToTheBudget() {
        val plan =
            DailyPlan(
                listOf(state(Skills.KB_MIDDLE_C), state(Skills.RH_TUNES, learned = true)),
                listOf(Skills.KB_FIND_C),
            )
        val twenty = compose(plan)
        assertEquals(listOf(5, 9, 6), listOf(twenty.reviewMinutes, twenty.lessonMinutes, twenty.songMinutes))
        assertEquals(listOf(5, 9, 6), twenty.cards.map { it.minutes })
        for (budget in DailyBudget.entries) {
            val s = compose(plan, budget = budget)
            assertEquals(budget.minutes, s.reviewMinutes + s.lessonMinutes + s.songMinutes)
        }
        val fifteen = compose(plan, budget = DailyBudget.MIN_15)
        assertEquals(listOf(4, 7, 4), listOf(fifteen.reviewMinutes, fifteen.lessonMinutes, fifteen.songMinutes))
    }

    @Test
    fun missingCardSharesItsMinutes() {
        val plan = DailyPlan(listOf(state(Skills.KB_MIDDLE_C)), emptyList())
        val session = compose(plan, everyUnitClean)
        assertEquals(1, session.cards.size)
        assertEquals(DailyBudget.MIN_20.minutes, session.reviewMinutes)
        assertEquals(0, session.lessonMinutes)
        assertEquals(0, session.songMinutes)
        val noSong = compose(DailyPlan(listOf(state(Skills.KB_MIDDLE_C)), listOf(Skills.KB_FIND_C)), everyUnitClean)
        // Review 0.25 : lesson 0.45 of 20 minutes.
        assertEquals(listOf(7, 13), listOf(noSong.reviewMinutes, noSong.lessonMinutes))
    }

    @Test
    fun nothingDueGivesNoColdOpen() {
        val session = compose(DailyPlan(emptyList(), listOf(Skills.KB_BLACK_GROUPS)))
        assertEquals(1, session.cards.count { it is Card.Lesson })
        assertEquals(0, session.cards.count { it is Card.ColdOpen })
        assertEquals(0, session.reviewMinutes)
    }

    @Test
    fun everythingCleanGivesNoSong() {
        val session = compose(DailyPlan(listOf(state(Skills.RH_TUNES, learned = true)), emptyList()), everyUnitClean)
        assertEquals(0, session.cards.count { it is Card.Song })
    }

    @Test
    fun anEmptyPlanGivesNoCards() {
        val session = compose(DailyPlan(emptyList(), emptyList()))
        assertEquals(emptyList(), session.cards)
        assertEquals(0, session.reviewMinutes + session.lessonMinutes + session.songMinutes)
        assertNull(session.cards.firstOrNull())
    }

    @Test
    fun skillsWithoutPassagesOrUnknownToTheCurriculumAreSkipped() {
        val orphan = Skill("orphan", "Orphan", emptySet())
        val unknownLearned = state(orphan, learned = true)
        val plan = DailyPlan(listOf(unknownLearned, state(Skills.HANDS_HELD_BASS)), emptyList())
        val cold = assertIs<Card.ColdOpen>(compose(plan).cards.first())
        assertEquals(Skills.HANDS_HELD_BASS, cold.skill)
        assertEquals(listOf(unknownLearned), compose(plan).moreDue)
    }

    @Test
    fun aNewSkillWithoutPassagesGivesNoLessonAndAnUnplayableDueSkillGivesNoColdOpen() {
        val orphan = Skill("orphan", "Orphan", emptySet())
        val session = compose(DailyPlan(listOf(state(orphan)), listOf(orphan)))
        assertEquals(emptyList(), session.cards)
        assertEquals(listOf(state(orphan)), session.moreDue)
    }
}
