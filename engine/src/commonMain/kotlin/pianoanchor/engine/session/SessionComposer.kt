package pianoanchor.engine.session

import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Passage
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.Track
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.DailyPlan
import pianoanchor.engine.matcher.TempoRung
import kotlin.time.Instant

/**
 * What the learner has played of one passage: when it was last played, the tempos (BPM) of its CLEAN timed runs and
 * whether a wait-mode run was CLEAN ([cleanWait]).
 */
public data class PassageHistory(
    val lastPlayed: Instant?,
    val cleanTempos: Set<Int>,
    val cleanWait: Boolean = false,
)

/** Whether [history] has a CLEAN run of [passage] at [rung] (a CLEAN wait-mode run for [TempoRung.WAIT]). */
internal fun isClean(
    passage: Passage,
    history: PassageHistory?,
    rung: TempoRung,
): Boolean {
    val bpm = rung.bpm(passage)
    return history != null && if (bpm == null) history.cleanWait else bpm in history.cleanTempos
}

/** The highest rung of the tempo ladder with a CLEAN run of [passage] in [history], null when none. */
internal fun highestClean(
    passage: Passage,
    history: PassageHistory?,
): TempoRung? = TempoRung.entries.lastOrNull { isClean(passage, history, it) }

/** The rung to play next above [highestClean]: WAIT when none is clean, null when the top rung is. */
internal fun nextRung(highestClean: TempoRung?): TempoRung? =
    if (highestClean == null) TempoRung.WAIT else TempoRung.entries.getOrNull(highestClean.ordinal + 1)

/** One card of Today, for about [minutes]. */
public sealed interface Card {
    public val minutes: Int

    /** A card that opens Practice on a [passage] at [rung]. */
    public sealed interface PassageCard : Card {
        public val passage: Passage
        public val rung: TempoRung
    }

    /** Ear skills for today: due ones first (plan order), then new ones. Opened by the ear screen. */
    public data class Ear(
        val skills: List<Skill>,
        override val minutes: Int,
    ) : Card

    /** The most overdue due skill's passage, played cold (no hints): a review. */
    public data class ColdOpen(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
        val skill: Skill,
        val lastPlayed: Instant?,
    ) : PassageCard

    /** First passage of a new skill; [unlockedBy] names the prerequisites just learned ("why now"). */
    public data class Lesson(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
        val skill: Skill,
        val unlockedBy: List<Skill>,
    ) : PassageCard

    /** The piece in progress at its next tempo rung. */
    public data class Song(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
    ) : PassageCard
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

/** The index of the Ear segment among the shares. */
private const val EAR_SEGMENT = 3

/** Turns a daily plan into Today's cards and session bar (`docs/algorithms/session-composer.md`). Pure. */
public object SessionComposer {
    /**
     * Composes the session for [plan] over [curriculum], splitting [budget] by the SESSION_SHARE tunables.
     * [history] is keyed by passage id; a passage missing from it was never played. [learned] are the ids of every
     * learned skill (the plan lists only due and new ones); [graph] gives each skill its track; the song comes from
     * the latest unit with one.
     */
    public fun compose(
        plan: DailyPlan,
        curriculum: Curriculum,
        budget: DailyBudget,
        history: Map<String, PassageHistory>,
        learned: Set<String>,
        graph: SkillGraph = curriculum.graph,
    ): Session {
        val (dueEar, dueOther) = plan.due.partition { graph.byId(it.skillId)?.track == Track.EAR }
        val cold = coldOpen(dueOther, curriculum, history)
        val lesson = lesson(plan, curriculum, graph)
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
        val minutes = split(budget.minutes, shares)
        val (review, lessonMinutes, songMinutes) = minutes
        val earMinutes = minutes[EAR_SEGMENT]
        val ear =
            Card
                .Ear(earSkills.take(maxOf(1, earMinutes / Tunables.EAR_MINUTES_PER_SKILL)), earMinutes)
                .takeIf { earSkills.isNotEmpty() }
        val cards =
            listOfNotNull(
                cold?.copy(minutes = review),
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

    private fun coldOpen(
        due: List<SkillState>,
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
    ): Card.ColdOpen? =
        due.firstNotNullOfOrNull { state ->
            passagesOf(curriculum)
                .filter { state.skillId in it.skills }
                .minWithOrNull(compareBy(nullsFirst()) { history[it.id]?.lastPlayed })
                ?.let { passage ->
                    // A passage lists only skills of the graph (checked by the content tests).
                    val skill = curriculum.graph.skills.first { it.id == state.skillId }
                    val played = history[passage.id]
                    Card.ColdOpen(
                        passage,
                        highestClean(passage, played) ?: TempoRung.WAIT,
                        0,
                        skill,
                        played?.lastPlayed,
                    )
                }
        }

    private fun lesson(
        plan: DailyPlan,
        curriculum: Curriculum,
        graph: SkillGraph,
    ): Card.Lesson? {
        val skill = plan.new.firstOrNull { it.track != Track.EAR } ?: return null
        return passagesOf(curriculum).firstOrNull { skill.id in it.skills }?.let { passage ->
            Card.Lesson(
                passage,
                TempoRung.WAIT,
                0,
                skill,
                graph.skills.filter { it.id in skill.prerequisites },
            )
        }
    }

    private fun song(
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
        learned: Set<String>,
        used: List<String>,
    ): Card.Song? =
        curriculum.units.asReversed().firstNotNullOfOrNull { unit ->
            val open =
                unit.passages
                    .filter { it.id !in used }
                    .mapNotNull { passage ->
                        nextRung(highestClean(passage, history[passage.id]))?.let { passage to it }
                    }
            val pick = open.firstOrNull { it.first.rights.status == RightsStatus.PUBLIC_DOMAIN } ?: open.firstOrNull()
            if (pick == null || unit.skills.none { it in learned }) null else Card.Song(pick.first, pick.second, 0)
        }

    private fun passagesOf(curriculum: Curriculum): List<Passage> = curriculum.units.flatMap { it.passages }

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
