package pianoanchor.engine.session

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.Track
import pianoanchor.engine.Tunables
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
        learned: Set<String> = plan.due.filter { it.learned }.mapTo(HashSet()) { it.skillId },
    ) = SessionComposer.compose(plan, Curriculum, budget, history, learned)

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
    fun coldOpenIsPerformAtATimedRungAndPracticeAtWait() {
        val plan = DailyPlan(listOf(state(Skills.KB_MIDDLE_C)), emptyList())
        val march = passage("middle-c-march")
        val history =
            mapOf(
                march.id to played(1, TempoRung.P60.bpm(march)!!),
                passage("which-c-is-middle").id to played(2),
            )
        val timed = assertIs<Card.ColdOpen>(compose(plan, history).cards.first())
        assertEquals(march, timed.passage)
        assertEquals(RunMode.PERFORM, timed.mode)
        assertEquals(RunMode.PRACTICE, assertIs<Card.ColdOpen>(compose(plan).cards.first()).mode)
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
        val earlier =
            compose(
                plan,
                unit1Clean,
                learned = setOf(Skills.RH_TUNES.id, Skills.KB_MIDDLE_C.id),
            ).cards.filterIsInstance<Card.Song>().single()
        assertEquals(true, Curriculum.units[0].passages.any { it.id == earlier.passage.id })
    }

    @Test
    fun songComesFromTheLearnedSkillsEvenWhenNothingIsDueOrNew() {
        val plan = DailyPlan(emptyList(), emptyList())
        val learned = setOf(Skills.KB_MIDDLE_C.id)
        val session = compose(plan, learned = learned)
        val song = session.cards.single()
        assertIs<Card.Song>(song)
        assertEquals(true, Curriculum.units[0].passages.any { it.id == song.passage.id })
        assertEquals(DailyBudget.MIN_20.minutes, session.songMinutes)
        assertEquals(DailyBudget.MIN_20.minutes, song.minutes)
    }

    @Test
    fun songSkipsThePassagesOfTheColdOpenAndTheLesson() {
        val plan = DailyPlan(listOf(state(Skills.KB_MIDDLE_C, learned = true)), listOf(Skills.KB_FIND_C))
        val session = compose(plan)
        val used =
            session.cards
                .filterIsInstance<Card.PassageCard>()
                .filter { it !is Card.Song }
                .map { it.passage.id }
                .toSet()
        assertEquals(2, used.size)
        val song = session.cards.filterIsInstance<Card.Song>().single()
        assertEquals(false, song.passage.id in used)
        // With every other unit-0 passage clean, the only open ones are the two in use: no song at all.
        val others = Curriculum.units[0].passages.filter { it.id !in used }
        val history = others.associate { it.id to played(1, TempoRung.P100.bpm(it)!!) }
        assertEquals(0, compose(plan, history).cards.count { it is Card.Song })
    }

    @Test
    fun aCleanWaitRunMovesTheSongToTheFirstTimedRung() {
        val plan = DailyPlan(emptyList(), emptyList())
        val learned = setOf(Skills.RH_TUNES.id)
        val first = assertIs<Card.PassageCard>(compose(plan, learned = learned).cards.single())
        val history = mapOf(first.passage.id to PassageHistory(null, emptySet(), cleanWait = true))
        assertEquals(
            TempoRung.P60,
            assertIs<Card.PassageCard>(compose(plan, history, learned = learned).cards.single()).rung,
        )
    }

    @Test
    fun minutesSumToTheBudget() {
        val plan =
            DailyPlan(
                listOf(state(Skills.KB_MIDDLE_C), state(Skills.RH_TUNES, learned = true)),
                listOf(Skills.KB_FIND_C),
            )
        val twenty = compose(plan)
        assertEquals(listOf(5, 8, 7), listOf(twenty.reviewMinutes, twenty.lessonMinutes, twenty.songMinutes))
        assertEquals(listOf(5, 8, 7), twenty.cards.map { it.minutes })
        for (budget in DailyBudget.entries) {
            val s = compose(plan, budget = budget)
            assertEquals(budget.minutes, s.reviewMinutes + s.lessonMinutes + s.songMinutes + s.earMinutes)
        }
        val fifteen = compose(plan, budget = DailyBudget.MIN_15)
        assertEquals(listOf(4, 6, 5), listOf(fifteen.reviewMinutes, fifteen.lessonMinutes, fifteen.songMinutes))
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
        // Review 0.20 : lesson 0.35 of 20 minutes.
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

    private val earA = Skill("ear-a", "Ear A", emptySet(), Track.EAR)
    private val earB = Skill("ear-b", "Ear B", emptySet(), Track.EAR)
    private val earC = Skill("ear-c", "Ear C", emptySet(), Track.EAR)
    private val withEar = SkillGraph(Curriculum.graph.skills + listOf(earA, earB, earC))

    private fun composeEar(
        plan: DailyPlan,
        budget: DailyBudget = DailyBudget.MIN_20,
        history: Map<String, PassageHistory> = emptyMap(),
    ) = SessionComposer.compose(
        plan,
        Curriculum,
        budget,
        history,
        plan.due
            .filter {
                it.learned
            }.mapTo(HashSet()) { it.skillId },
        withEar,
    )

    @Test
    fun `ear skills never become the cold open or lesson`() {
        val plan = DailyPlan(listOf(state(earA), state(Skills.KB_MIDDLE_C)), listOf(earB, Skills.KB_FIND_C))
        val session = composeEar(plan)
        assertEquals(Skills.KB_MIDDLE_C, assertIs<Card.ColdOpen>(session.cards.first()).skill)
        assertEquals(
            Skills.KB_FIND_C,
            session.cards
                .filterIsInstance<Card.Lesson>()
                .single()
                .skill,
        )
        assertEquals(emptyList(), session.moreDue)
        val onlyEar = composeEar(DailyPlan(listOf(state(earA)), listOf(earB)))
        assertEquals(listOf<Class<*>>(Card.Ear::class.java), onlyEar.cards.map { it::class.java })
    }

    @Test
    fun `ear card lists due then new ear skills`() {
        val plan = DailyPlan(listOf(state(earB), state(earA)), listOf(earC))
        val ear = assertIs<Card.Ear>(composeEar(plan).cards.last())
        assertEquals(listOf(earB, earA, earC), ear.skills)
    }

    @Test
    fun `ear card count follows its minutes`() {
        val plan = DailyPlan(listOf(state(earA)), listOf(earB, earC))
        // 20 minutes with only the ear card: it takes everything, so all three fit.
        assertEquals(3, assertIs<Card.Ear>(composeEar(plan).cards.single()).skills.size)
        // With a review and a lesson too, 15% of 20 minutes is 3 minutes at 1 per skill.
        val busy = DailyPlan(listOf(state(Skills.KB_MIDDLE_C), state(earA)), listOf(Skills.KB_FIND_C, earB, earC))
        val session = composeEar(busy, DailyBudget.MIN_15, everyUnitClean)
        val ear = session.cards.filterIsInstance<Card.Ear>().single()
        assertEquals(session.earMinutes, ear.minutes)
        assertEquals(maxOf(1, ear.minutes / Tunables.EAR_MINUTES_PER_SKILL), ear.skills.size)
        // Never fewer than one skill, even when its minutes round to zero.
        val tiny = composeEar(busy, DailyBudget.entries.minBy { it.minutes }, everyUnitClean)
        assertEquals(
            1,
            tiny.cards
                .filterIsInstance<Card.Ear>()
                .single()
                .skills.size
                .coerceAtMost(1),
        )
    }

    @Test
    fun `no ear skills gives no ear card and shares its minutes`() {
        val plan = DailyPlan(listOf(state(Skills.KB_MIDDLE_C)), listOf(Skills.KB_FIND_C))
        val session = compose(plan, everyUnitClean)
        assertEquals(0, session.cards.count { it is Card.Ear })
        assertEquals(0, session.earMinutes)
        assertEquals(DailyBudget.MIN_20.minutes, session.reviewMinutes + session.lessonMinutes)
    }

    @Test
    fun `minutes sum to the budget with four shares`() {
        val plan =
            DailyPlan(
                listOf(state(Skills.KB_MIDDLE_C), state(Skills.RH_TUNES, learned = true), state(earA)),
                listOf(Skills.KB_FIND_C),
            )
        for (budget in DailyBudget.entries) {
            val s = composeEar(plan, budget)
            assertEquals(budget.minutes, s.reviewMinutes + s.lessonMinutes + s.songMinutes + s.earMinutes)
            assertEquals(true, s.earMinutes > 0)
        }
        val twenty = composeEar(plan)
        assertEquals(
            listOf(4, 7, 6, 3),
            listOf(twenty.reviewMinutes, twenty.lessonMinutes, twenty.songMinutes, twenty.earMinutes),
        )
    }
}
