package pianoanchor.engine.session

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Passage
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.Track
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.DailyPlan
import pianoanchor.engine.lesson.CourseProgress
import pianoanchor.engine.lesson.Quiz
import pianoanchor.engine.lesson.QuizMaker
import pianoanchor.engine.matcher.TempoRung
import kotlin.random.Random
import kotlin.time.Instant

/** One card of Today, for about [minutes]. */
public sealed interface Card {
    public val minutes: Int

    /** A card that opens Practice on a [passage] at [rung]. */
    public sealed interface PassageCard : Card {
        public val passage: Passage
        public val rung: TempoRung

        /** How the passage is run: PRACTICE, except a cold open performs once a timed run was clean. */
        public val mode: RunMode
    }

    /** Ear skills for today: due ones first (plan order), then new ones. Opened by the ear screen. */
    public data class Ear(
        val skills: List<Skill>,
        override val minutes: Int,
    ) : Card

    /**
     * Quick piano-checked quizzes of the most overdue due skills, opened by the quiz screen. They count as those
     * skills' review of the day, so the cold open skips them.
     */
    public data class Quizzes(
        val quizzes: List<Quiz>,
        override val minutes: Int,
    ) : Card

    /**
     * The most overdue due skill's passage, played cold (no hints): a review. [mode] is PERFORM unless the rung is
     * WAIT (no clean timed run yet), which has no perform mode.
     */
    public data class ColdOpen(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
        val skill: Skill,
        val lastPlayed: Instant?,
        override val mode: RunMode,
    ) : PassageCard

    /**
     * The first unticked course concept (`CourseProgress.next`), opened as a five-step lesson; [passage] is the
     * concept's first passage (the lesson's Use step) and [unlockedBy] names its prerequisites ("why now").
     */
    public data class Lesson(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
        val skill: Skill,
        val unlockedBy: List<Skill>,
    ) : PassageCard {
        override val mode: RunMode = RunMode.PRACTICE
    }

    /** The piece in progress at its next tempo rung. */
    public data class Song(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
    ) : PassageCard {
        override val mode: RunMode = RunMode.PRACTICE
    }
}

/**
 * Today's session: the [cards] in order, the minutes of the Review, Lesson, Song and Ear segments (0 for a missing
 * card; they sum to the budget unless there are no cards) and the due skills beyond the cold open, [moreDue], practised
 * from the Review segment in plan order.
 */
public data class Session(
    val cards: List<Card>,
    val reviewMinutes: Int,
    val lessonMinutes: Int,
    val songMinutes: Int,
    val earMinutes: Int,
    val moreDue: List<SkillState>,
)

private const val SEED_PRIME = 31L
private const val SECONDS_PER_MINUTE = 60

/** The index of the Ear segment among the shares. */
private const val EAR_SEGMENT = 3

/** Turns a daily plan into Today's cards and session bar (`docs/algorithms/session-composer.md`). Pure. */
public object SessionComposer {
    /**
     * Composes the session for [plan] over [curriculum], splitting [budget] by the SESSION_SHARE tunables.
     * [history] is keyed by passage id; a passage missing from it was never played. [learned] are the ids of every
     * learned skill (the plan lists only due and new ones); [graph] gives each skill its track. The cold open uses only
     * passages whose skills are all learned, never the lesson's passage. The lesson is the first course concept without
     * a clean wait-mode run in [history]; the plan's new skills play no part. Up to QUIZ_MAX_PER_DAY of the due core
     * skills that have a quiz come first as a Quizzes card (seeded by [day] and the skill id, so a day's quiz is
     * stable); the cold open picks from the other due skills. The quizzes' minutes are part of the Review segment.
     * The song is a piece with no lock: played before new, then public domain before original, then curriculum order.
     */
    public fun compose(
        plan: DailyPlan,
        curriculum: Curriculum,
        budget: DailyBudget,
        history: Map<String, PassageHistory>,
        learned: Set<String>,
        day: LocalDate,
        graph: SkillGraph = curriculum.graph,
    ): Session {
        val (dueEar, dueNotEar) = plan.due.partition { graph.byId(it.skillId)?.track == Track.EAR }
        val taught = CourseProgress.ticked(curriculum, history)
        val quizzes = quizzes(dueNotEar, graph, taught, day)
        val quizzed = quizzes.map { it.skillId }.toSet()
        val dueOther = dueNotEar.filter { it.skillId !in quizzed }
        val lesson = lesson(curriculum, history, graph)
        val cold = coldOpen(dueOther, curriculum, history, learned, lesson?.let { it.passage.id })
        val earSkills = dueEar.mapNotNull { graph.byId(it.skillId) } + plan.new.filter { it.track == Track.EAR }
        val used = listOfNotNull(cold?.passage, lesson?.passage).map { it.id }
        val song = song(curriculum, history, learned, used)
        val shares =
            listOf(
                Tunables.SESSION_SHARE_REVIEW.takeIf { cold != null } ?: 0.0,
                Tunables.SESSION_SHARE_LESSON.takeIf { lesson != null } ?: 0.0,
                Tunables.SESSION_SHARE_SONG.takeIf { song != null } ?: 0.0,
                Tunables.SESSION_SHARE_EAR.takeIf { earSkills.isNotEmpty() } ?: 0.0,
            )
        val quizMinutes = quizMinutes(quizzes.size)
        val minutes = split((budget.minutes - quizMinutes).coerceAtLeast(0), shares)
        val (coldMinutes, lessonMinutes, songMinutes) = minutes
        val review = coldMinutes + quizMinutes
        val earMinutes = minutes[EAR_SEGMENT]
        val ear =
            Card
                .Ear(earSkills.take(maxOf(1, earMinutes / Tunables.EAR_MINUTES_PER_SKILL)), earMinutes)
                .takeIf { earSkills.isNotEmpty() }
        val cards =
            listOfNotNull(
                Card.Quizzes(quizzes, quizMinutes).takeIf { quizzes.isNotEmpty() },
                cold?.copy(minutes = coldMinutes),
                lesson?.copy(minutes = lessonMinutes),
                song?.copy(minutes = songMinutes),
                ear,
            )
        return Session(
            cards,
            review,
            lessonMinutes,
            songMinutes,
            earMinutes,
            dueOther.filter { state -> state.skillId != cold?.let { it.skill.id } },
        )
    }

    /** The quizzes of the most overdue due core skills that have one, at most QUIZ_MAX_PER_DAY. */
    private fun quizzes(
        due: List<SkillState>,
        graph: SkillGraph,
        taught: Set<String>,
        day: LocalDate,
    ): List<Quiz> =
        due
            .filter { graph.byId(it.skillId)?.track == Track.CORE }
            .mapNotNull { QuizMaker.make(it.skillId, taught, Random(seed(day, it.skillId))) }
            .take(Tunables.QUIZ_MAX_PER_DAY)

    private fun seed(
        day: LocalDate,
        skillId: String,
    ): Long = day.toEpochDays() * SEED_PRIME + skillId.hashCode()

    /** Whole minutes for [count] quizzes, rounded up. */
    private fun quizMinutes(count: Int): Int =
        (count * Tunables.QUIZ_SECONDS + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE

    private fun coldOpen(
        due: List<SkillState>,
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
        learned: Set<String>,
        lessonPassageId: String?,
    ): Card.ColdOpen? =
        due.firstNotNullOfOrNull { state ->
            curriculum.passages
                .filter { passage ->
                    passage.id != lessonPassageId &&
                        state.skillId in passage.skills &&
                        passage.skills.all { it in learned || it == state.skillId }
                }.minWithOrNull(compareBy(nullsFirst()) { history[it.id]?.lastPlayed })
                ?.let { passage ->
                    // A passage lists only skills of the graph (checked by the content tests).
                    val skill = curriculum.graph.skills.first { it.id == state.skillId }
                    val played = history[passage.id]
                    val rung = highestClean(passage, played) ?: TempoRung.WAIT
                    val mode = if (rung.canPerform) RunMode.PERFORM else RunMode.PRACTICE
                    Card.ColdOpen(passage, rung, 0, skill, played?.lastPlayed, mode)
                }
        }

    private fun lesson(
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
        graph: SkillGraph,
    ): Card.Lesson? {
        val skill = CourseProgress.next(curriculum, CourseProgress.ticked(curriculum, history)) ?: return null
        // Every course concept has a passage (checked by the content tests).
        val passage = curriculum.passages.first { skill.id in it.skills }
        return Card.Lesson(passage, TempoRung.WAIT, 0, skill, graph.skills.filter { it.id in skill.prerequisites })
    }

    private fun song(
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
        learned: Set<String>,
        used: List<String>,
    ): Card.Song? {
        if (learned.isEmpty()) return null
        return curriculum.passages
            .filter { it.piece && it.id !in used }
            .mapNotNull { passage -> nextRung(highestClean(passage, history[passage.id]))?.let { passage to it } }
            .sortedWith(
                compareBy(
                    { history[it.first.id]?.lastPlayed == null },
                    { it.first.rights.status != RightsStatus.PUBLIC_DOMAIN },
                ),
            ).firstOrNull()
            ?.let { (passage, rung) -> Card.Song(passage, rung, 0) }
    }

    /** [total] split in proportion to [shares] in whole numbers (largest remainders first, ties in order). */
    private fun split(
        total: Int,
        shares: List<Double>,
    ): List<Int> {
        val sum = shares.sum()
        if (sum == 0.0) return shares.map { 0 }
        val exact = shares.map { it / sum * total }
        val result = exact.mapTo(ArrayList()) { it.toInt() }
        val byRemainder = exact.indices.sortedByDescending { exact[it] - result[it] }
        byRemainder.take(total - result.sum()).forEach { result[it]++ }
        return result
    }
}
