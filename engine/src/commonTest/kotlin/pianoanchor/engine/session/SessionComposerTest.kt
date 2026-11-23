package pianoanchor.engine.session

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.Track
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.DailyPlan
import pianoanchor.engine.lesson.CourseProgress
import pianoanchor.engine.matcher.TempoRung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
        unaidedRecalledChecks = 0,
        learned = learned,
    )

    private fun passage(id: String) = checkNotNull(Curriculum.passage("builtin:$id")) { id }

    private fun compose(
        plan: DailyPlan,
        history: Map<String, PassageHistory> = emptyMap(),
        budget: DailyBudget = DailyBudget.MIN_20,
        learned: Set<String> = learnedWithPrerequisites(plan),
    ) = SessionComposer.compose(plan, Curriculum, budget, history, learned)

    /** The learned skills of [plan]'s due list and everything they build on. */
    private fun learnedWithPrerequisites(plan: DailyPlan): Set<String> {
        val result = HashSet<String>()

        fun add(id: String) {
            if (result.add(id)) {
                Curriculum.graph
                    .byId(id)
                    ?.prerequisites
                    ?.forEach(::add)
            }
        }
        plan.due.filter { it.learned }.forEach { add(it.skillId) }
        return result
    }

    /** One learned skill, enough for a song: pieces are open whatever is learned. */
    private val oneLearnedSkill = setOf(Skills.RH_C_POSITION.id)

    private fun songOf(session: Session) = session.cards.filterIsInstance<Card.Song>().singleOrNull()

    private fun played(
        epochSecond: Long?,
        vararg tempos: Int,
    ) = PassageHistory(epochSecond?.let(Instant::fromEpochSeconds), tempos.toSet())

    /** Every passage played clean at every rung, wait mode included: the whole course is ticked. */
    private val everyUnitClean: Map<String, PassageHistory> =
        Curriculum.units
            .flatMap { it.passages }
            .associate { it.id to PassageHistory(Instant.fromEpochSeconds(1), setOf(TempoRung.P100.bpm(it)!!), true) }

    /** A clean wait-mode run of the first passage of every course skill before [skill]: the lesson is [skill]. */
    private fun courseTickedBefore(skill: Skill): Map<String, PassageHistory> =
        CourseProgress
            .skills(Curriculum)
            .takeWhile { it != skill }
            .associate { done ->
                Curriculum.passages.first { done.id in it.skills }.id to
                    PassageHistory(null, emptySet(), cleanWait = true)
            }

    @Test
    fun coldOpenIsTheLowestRetrievabilityDueSkill() {
        val plan = DailyPlan(listOf(state(Skills.HIGH_AND_LOW), state(Skills.MIDDLE_C)), emptyList())
        val session = compose(plan)
        val cold = assertIs<Card.ColdOpen>(session.cards.first())
        assertEquals(Skills.HIGH_AND_LOW, cold.skill)
        assertEquals(listOf(state(Skills.MIDDLE_C)), session.moreDue)
    }

    @Test
    fun coldOpenPassageIsTheLeastRecentlyPlayed() {
        val plan = DailyPlan(listOf(state(Skills.BLACK_KEY_GROUPS)), emptyList())
        val up = passage("twos-and-threes")
        val down = passage("twos-and-threes-down")
        val which = passage("which-c-is-middle")
        val history = mapOf(up.id to played(100), down.id to played(75), which.id to played(50))
        val cold = assertIs<Card.ColdOpen>(compose(plan, history).cards.first())
        assertEquals(which, cold.passage)
        assertEquals(Instant.fromEpochSeconds(50), cold.lastPlayed)
        // Never played beats played; a tie goes to curriculum order.
        val someUnplayed = mapOf(down.id to played(75), which.id to played(50))
        assertEquals(up, assertIs<Card.ColdOpen>(compose(plan, someUnplayed).cards.first()).passage)
        assertEquals(RunMode.PRACTICE, assertIs<Card.ColdOpen>(compose(plan).cards.first()).mode)
    }

    @Test
    fun coldOpenRungIsTheHighestCleanRung() {
        val plan = DailyPlan(listOf(state(Skills.MIDDLE_C)), emptyList())
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
        val plan = DailyPlan(listOf(state(Skills.MIDDLE_C)), emptyList())
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
    fun coldOpenStaysWithPassagesWhoseSkillsAreAllLearned() {
        val half = passage("half-and-quarter")
        val plan = DailyPlan(listOf(state(Skills.QUARTER_AND_HALF, learned = true)), emptyList())
        // hot-cross-buns also lists quarter-and-half and was never played, but it needs unit 1 skills.
        val cold = assertIs<Card.ColdOpen>(compose(plan, mapOf(half.id to played(1))).cards.first())
        assertEquals(half, cold.passage)

        val melody = DailyPlan(listOf(state(Skills.MELODY_AND_CHORDS, learned = true)), emptyList())
        val seen = listOf("hold-and-sing", "lightly-row-with-chords", "ode-to-joy-with-chords")
        val history = seen.mapIndexed { i, id -> passage(id).id to played(10L + i) }.toMap()
        val chosen = assertIs<Card.ColdOpen>(compose(melody, history).cards.first()).passage
        // The Saints (needs more-inversions) was never played, yet the least recent learned-only passage wins.
        assertEquals(passage("hold-and-sing"), chosen)
    }

    @Test
    fun lessonNamesItsPrerequisites() {
        val plan = DailyPlan(emptyList(), emptyList())
        val lesson =
            assertIs<Card.Lesson>(compose(plan, courseTickedBefore(Skills.FINGER_NUMBER_TUNES)).cards.first())
        assertEquals(Skills.FINGER_NUMBER_TUNES, lesson.skill)
        assertEquals(setOf(Skills.STEPS_AND_SKIPS), lesson.unlockedBy.toSet())
        assertEquals(TempoRung.WAIT, lesson.rung)
        assertEquals(RunMode.PRACTICE, lesson.mode)
        assertEquals(
            Curriculum.units
                .flatMap {
                    it.passages
                }.first { Skills.FINGER_NUMBER_TUNES.id in it.skills },
            lesson.passage,
        )
    }

    @Test
    fun theLessonIsTheFirstUntickedCourseSkillWhateverThePlanSays() {
        val plan = DailyPlan(emptyList(), listOf(Skills.HIGH_AND_LOW))
        assertEquals(Skills.HAND_SHAPE, assertIs<Card.Lesson>(compose(plan).cards.first()).skill)
        // A clean wait run of a later concept doesn't skip the first unticked one (map order).
        val middleC = Curriculum.passages.first { Skills.MIDDLE_C.id in it.skills }
        val later = mapOf(middleC.id to PassageHistory(null, emptySet(), cleanWait = true))
        assertEquals(Skills.HAND_SHAPE, assertIs<Card.Lesson>(compose(plan, later).cards.first()).skill)
        // A clean timed run alone doesn't tick.
        val handShape = Curriculum.passages.first { Skills.HAND_SHAPE.id in it.skills }
        val timed = mapOf(handShape.id to PassageHistory(null, setOf(TempoRung.P100.bpm(handShape)!!)))
        assertEquals(Skills.HAND_SHAPE, assertIs<Card.Lesson>(compose(plan, timed).cards.first()).skill)
    }

    @Test
    fun noLessonCardWhenEveryCourseSkillIsTicked() {
        val plan = DailyPlan(emptyList(), listOf(Skills.HIGH_AND_LOW))
        val session = compose(plan, everyUnitClean)
        assertEquals(0, session.cards.count { it is Card.Lesson })
        assertEquals(0, session.lessonMinutes)
    }

    @Test
    fun songPicksTheNextRungAboveTheHighestClean() {
        val plan = DailyPlan(listOf(state(Skills.RH_C_POSITION, learned = true)), emptyList())
        val song = compose(plan).cards.filterIsInstance<Card.Song>().single()
        // The first public domain piece in curriculum order, at the first rung with nothing clean.
        assertEquals("builtin:hot-cross-buns", song.passage.id)
        assertEquals(RunMode.PRACTICE, song.mode)
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
        val plan = DailyPlan(listOf(state(Skills.FINGER_NUMBER_TUNES, learned = true)), emptyList())
        val hot = passage("hot-cross-buns")
        val history = mapOf(hot.id to played(1, TempoRung.P100.bpm(hot)!!))
        val song = compose(plan, history).cards.filterIsInstance<Card.Song>().single()
        assertEquals(false, song.passage.id == hot.id)
    }

    @Test
    fun songComesFromTheLearnedSkillsEvenWhenNothingIsDueOrNew() {
        val plan = DailyPlan(emptyList(), emptyList())
        val session = compose(plan, courseTickedBefore(Skills.SHARPS_AND_FLATS), learned = oneLearnedSkill)
        val song = session.cards.filterIsInstance<Card.Song>().single()
        assertEquals("builtin:hot-cross-buns", song.passage.id)
        assertEquals(session.cards.sumOf { it.minutes }, DailyBudget.MIN_20.minutes)
    }

    @Test
    fun songSkipsThePassagesOfTheColdOpenAndTheLesson() {
        val hot = passage("hot-cross-buns")
        val mary = passage("mary-had-a-little-lamb")
        // The first passage of finger-number-tunes is hot-cross-buns, the first piece: the lesson uses it.
        val lessonPlan = DailyPlan(emptyList(), emptyList())
        val lessonSession =
            compose(lessonPlan, courseTickedBefore(Skills.FINGER_NUMBER_TUNES), learned = oneLearnedSkill)
        assertEquals(hot, assertIs<Card.Lesson>(lessonSession.cards.first()).passage)
        assertEquals(mary, songOf(lessonSession)?.passage)
        // Every other finger-number-tunes passage was played, so the cold open is the never-played hot-cross-buns.
        val coldPlan = DailyPlan(listOf(state(Skills.FINGER_NUMBER_TUNES, learned = true)), emptyList())
        val history =
            Curriculum.units
                .flatMap { it.passages }
                .filter { Skills.FINGER_NUMBER_TUNES.id in it.skills && it != hot }
                .associate { it.id to played(1) }
        val coldSession = compose(coldPlan, history)
        assertEquals(hot, assertIs<Card.ColdOpen>(coldSession.cards.first()).passage)
        assertEquals(mary, songOf(coldSession)?.passage)
    }

    @Test
    fun theSongIsAlwaysAPiece() {
        val everyPieceButOneClean =
            Curriculum.passages
                .filter { it.piece && it.id != "builtin:four-chord-walk" }
                .associate { it.id to played(1, TempoRung.P100.bpm(it)!!) }
        val song = songOf(compose(DailyPlan(emptyList(), emptyList()), learned = oneLearnedSkill))
        assertEquals(true, song?.passage?.piece)
        // The exercises of unit 3 are open too, but are never offered.
        val last =
            songOf(compose(DailyPlan(emptyList(), emptyList()), everyPieceButOneClean, learned = oneLearnedSkill))
        assertEquals("builtin:four-chord-walk", last?.passage?.id)
    }

    @Test
    fun noPieceIsLockedBySkillsNotYetLearned() {
        // Only the first skill is learned, yet a piece from the last unit is offered once the others are clean.
        val history =
            Curriculum.passages
                .filter { it.piece && it.id != "builtin:when-the-saints-go-marching-in" }
                .associate { it.id to played(1, TempoRung.P100.bpm(it)!!) }
        val song = songOf(compose(DailyPlan(emptyList(), emptyList()), history, learned = oneLearnedSkill))
        assertEquals("builtin:when-the-saints-go-marching-in", song?.passage?.id)
    }

    @Test
    fun anInProgressPieceComesBeforeANewOne() {
        val plan = DailyPlan(emptyList(), emptyList())
        val everySkill = Curriculum.graph.skills.mapTo(HashSet()) { it.id }
        val walk = passage("four-chord-walk")
        assertEquals(RightsStatus.ORIGINAL, walk.rights.status)
        val history = mapOf(walk.id to played(1))
        assertEquals(walk, songOf(compose(plan, history, learned = everySkill))?.passage)
    }

    @Test
    fun publicDomainComesBeforeOriginalAndEqualsGoInCurriculumOrder() {
        val plan = DailyPlan(emptyList(), emptyList())
        val everySkill = Curriculum.graph.skills.mapTo(HashSet()) { it.id }
        assertEquals("builtin:hot-cross-buns", songOf(compose(plan, learned = everySkill))?.passage?.id)
        val onlyOriginalAndLater =
            Curriculum.units
                .flatMap { it.passages }
                .filter { it.piece && it.id != "builtin:four-chord-walk" && it.id != "builtin:ode-to-joy" }
                .associate { it.id to played(1, TempoRung.P100.bpm(it)!!) }
        assertEquals(
            "builtin:ode-to-joy",
            songOf(compose(plan, onlyOriginalAndLater, learned = everySkill))?.passage?.id,
        )
        val inProgress = listOf("lightly-row", "ode-to-joy").associate { passage(it).id to played(1) }
        assertEquals("builtin:lightly-row", songOf(compose(plan, inProgress, learned = everySkill))?.passage?.id)
    }

    @Test
    fun aPieceCleanAtFullTempoIsNotOffered() {
        val hot = passage("hot-cross-buns")
        val history = mapOf(hot.id to played(1, TempoRung.P100.bpm(hot)!!))
        val song = songOf(compose(DailyPlan(emptyList(), emptyList()), history, learned = oneLearnedSkill))
        assertNotEquals(hot, song?.passage)
    }

    @Test
    fun noLearnedSkillMeansNoSong() {
        val session = compose(DailyPlan(emptyList(), listOf(Skills.BLACK_KEY_GROUPS)), learned = emptySet())
        assertNull(songOf(session))
        assertEquals(1, session.cards.size)
    }

    @Test
    fun aCleanWaitRunMovesTheSongToTheFirstTimedRung() {
        val plan = DailyPlan(emptyList(), emptyList())
        val learned =
            learnedWithPrerequisites(DailyPlan(listOf(state(Skills.FINGER_NUMBER_TUNES, learned = true)), emptyList()))
        val ticked = courseTickedBefore(Skills.SHARPS_AND_FLATS)
        val first = songOf(compose(plan, ticked, learned = learned))!!
        val history = ticked + (first.passage.id to PassageHistory(null, emptySet(), cleanWait = true))
        assertEquals(TempoRung.P60, songOf(compose(plan, history, learned = learned))?.rung)
    }

    @Test
    fun minutesSumToTheBudget() {
        val plan =
            DailyPlan(
                listOf(state(Skills.MIDDLE_C), state(Skills.FINGER_NUMBER_TUNES, learned = true)),
                listOf(Skills.HIGH_AND_LOW),
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
        val plan = DailyPlan(listOf(state(Skills.MIDDLE_C)), emptyList())
        val session = compose(plan, everyUnitClean)
        assertEquals(1, session.cards.size)
        assertEquals(DailyBudget.MIN_20.minutes, session.reviewMinutes)
        assertEquals(0, session.lessonMinutes)
        assertEquals(0, session.songMinutes)
        val noSong = compose(DailyPlan(listOf(state(Skills.MIDDLE_C)), listOf(Skills.HIGH_AND_LOW)))
        // Review 0.20 : lesson 0.35 of 20 minutes.
        assertEquals(listOf(7, 13), listOf(noSong.reviewMinutes, noSong.lessonMinutes))
    }

    @Test
    fun nothingDueGivesNoColdOpen() {
        val session = compose(DailyPlan(emptyList(), listOf(Skills.BLACK_KEY_GROUPS)))
        assertEquals(1, session.cards.count { it is Card.Lesson })
        assertEquals(0, session.cards.count { it is Card.ColdOpen })
        assertEquals(0, session.reviewMinutes)
    }

    @Test
    fun everythingCleanGivesNoSong() {
        val session =
            compose(DailyPlan(listOf(state(Skills.FINGER_NUMBER_TUNES, learned = true)), emptyList()), everyUnitClean)
        assertEquals(0, session.cards.count { it is Card.Song })
    }

    @Test
    fun anEmptyPlanGivesNoCards() {
        val session = compose(DailyPlan(emptyList(), emptyList()), everyUnitClean)
        assertEquals(emptyList(), session.cards)
        assertEquals(0, session.reviewMinutes + session.lessonMinutes + session.songMinutes)
        assertNull(session.cards.firstOrNull())
    }

    @Test
    fun skillsWithoutPassagesOrUnknownToTheCurriculumAreSkipped() {
        val orphan = Skill("orphan", "Orphan", emptySet())
        val unknownLearned = state(orphan, learned = true)
        val plan = DailyPlan(listOf(unknownLearned, state(Skills.HANDS_TOGETHER_HELD, learned = true)), emptyList())
        val cold = assertIs<Card.ColdOpen>(compose(plan).cards.first())
        assertEquals(Skills.HANDS_TOGETHER_HELD, cold.skill)
        assertEquals(listOf(unknownLearned), compose(plan).moreDue)
    }

    @Test
    fun aNewSkillWithoutPassagesGivesNoLessonAndAnUnplayableDueSkillGivesNoColdOpen() {
        val orphan = Skill("orphan", "Orphan", emptySet())
        val session = compose(DailyPlan(listOf(state(orphan)), listOf(orphan)), everyUnitClean)
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
        learnedWithPrerequisites(plan),
        withEar,
    )

    @Test
    fun `ear skills never become the cold open or lesson`() {
        val plan = DailyPlan(listOf(state(earA), state(Skills.MIDDLE_C)), listOf(earB, Skills.HIGH_AND_LOW))
        val session = composeEar(plan)
        assertEquals(Skills.MIDDLE_C, assertIs<Card.ColdOpen>(session.cards.first()).skill)
        assertEquals(
            Skills.HAND_SHAPE,
            session.cards
                .filterIsInstance<Card.Lesson>()
                .single()
                .skill,
        )
        assertEquals(emptyList(), session.moreDue)
        val onlyEar = composeEar(DailyPlan(listOf(state(earA)), listOf(earB)), history = everyUnitClean)
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
        // 20 minutes with only the ear card (the course is done): it takes everything, so all three fit.
        assertEquals(3, assertIs<Card.Ear>(composeEar(plan, history = everyUnitClean).cards.single()).skills.size)
        // With a review and a lesson too, 15% of 20 minutes is 3 minutes at 1 per skill.
        val busy = DailyPlan(listOf(state(Skills.MIDDLE_C), state(earA)), listOf(Skills.HIGH_AND_LOW, earB, earC))
        val session = composeEar(busy, DailyBudget.MIN_15)
        val ear = session.cards.filterIsInstance<Card.Ear>().single()
        assertEquals(session.earMinutes, ear.minutes)
        assertEquals(maxOf(1, ear.minutes / Tunables.EAR_MINUTES_PER_SKILL), ear.skills.size)
        // Never fewer than one skill, even when its minutes round to zero.
        val tiny = composeEar(busy, DailyBudget.entries.minBy { it.minutes })
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
        val plan = DailyPlan(listOf(state(Skills.MIDDLE_C)), listOf(Skills.HIGH_AND_LOW))
        val session = compose(plan, everyUnitClean)
        assertEquals(0, session.cards.count { it is Card.Ear })
        assertEquals(0, session.earMinutes)
        assertEquals(DailyBudget.MIN_20.minutes, session.reviewMinutes + session.lessonMinutes)
    }

    @Test
    fun `minutes sum to the budget with four shares`() {
        val plan =
            DailyPlan(
                listOf(state(Skills.MIDDLE_C), state(Skills.FINGER_NUMBER_TUNES, learned = true), state(earA)),
                listOf(Skills.HIGH_AND_LOW),
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
