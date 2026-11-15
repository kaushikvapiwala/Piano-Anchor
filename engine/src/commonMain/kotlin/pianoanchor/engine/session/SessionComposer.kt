package pianoanchor.engine.session

import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Passage
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillState
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.DailyPlan
import pianoanchor.engine.matcher.TempoRung
import kotlin.time.Instant

/** What the learner has played of one passage: when it was last played and the tempos (BPM) of its CLEAN timed runs. */
public data class PassageHistory(
    val lastPlayed: Instant?,
    val cleanTempos: Set<Int>,
)

/** One card of Today: a [passage] to open Practice on at [rung], for about [minutes]. */
public sealed interface Card {
    public val passage: Passage
    public val rung: TempoRung
    public val minutes: Int

    /** The most overdue due skill's passage, played cold (no hints): a review. */
    public data class ColdOpen(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
        val skill: Skill,
        val lastPlayed: Instant?,
    ) : Card

    /** First passage of a new skill; [unlockedBy] names the prerequisites just learned ("why now"). */
    public data class Lesson(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
        val skill: Skill,
        val unlockedBy: List<Skill>,
    ) : Card

    /** The piece in progress at its next tempo rung. */
    public data class Song(
        override val passage: Passage,
        override val rung: TempoRung,
        override val minutes: Int,
    ) : Card
}

/**
 * Today's session: the [cards] in order, the minutes of the Review, Lesson and Song segments (0 for a missing card;
 * they sum to the budget unless there are no cards) and the due skills beyond the cold open, [moreDue], practised
 * from the Review segment in plan order.
 */
public data class Session(
    val cards: List<Card>,
    val reviewMinutes: Int,
    val lessonMinutes: Int,
    val songMinutes: Int,
    val moreDue: List<SkillState>,
)

/** Turns a daily plan into Today's cards and session bar (`docs/algorithms/session-composer.md`). Pure. */
public object SessionComposer {
    /**
     * Composes the session for [plan] over [curriculum], splitting [budget] by the SESSION_SHARE tunables.
     * [history] is keyed by passage id; a passage missing from it was never played.
     */
    public fun compose(
        plan: DailyPlan,
        curriculum: Curriculum,
        budget: DailyBudget,
        history: Map<String, PassageHistory>,
    ): Session {
        val cold = coldOpen(plan, curriculum, history)
        val lesson = lesson(plan, curriculum)
        val song = song(plan, curriculum, history)
        val shares =
            listOf(
                Tunables.SESSION_SHARE_REVIEW.takeIf { cold != null } ?: 0.0,
                Tunables.SESSION_SHARE_LESSON.takeIf { lesson != null } ?: 0.0,
                Tunables.SESSION_SHARE_SONG.takeIf { song != null } ?: 0.0,
            )
        val (review, lessonMinutes, songMinutes) = split(budget.minutes, shares)
        val cards =
            listOfNotNull(
                cold?.copy(minutes = review),
                lesson?.copy(minutes = lessonMinutes),
                song?.copy(minutes = songMinutes),
            )
        return Session(
            cards,
            review,
            lessonMinutes,
            songMinutes,
            plan.due.filter {
                it.skillId !=
                    cold?.let { it.skill.id }
            },
        )
    }

    private fun coldOpen(
        plan: DailyPlan,
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
    ): Card.ColdOpen? =
        plan.due.firstNotNullOfOrNull { state ->
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
    ): Card.Lesson? {
        val skill = plan.new.firstOrNull() ?: return null
        return passagesOf(curriculum).firstOrNull { skill.id in it.skills }?.let { passage ->
            Card.Lesson(
                passage,
                TempoRung.WAIT,
                0,
                skill,
                curriculum.graph.skills.filter { it.id in skill.prerequisites },
            )
        }
    }

    private fun song(
        plan: DailyPlan,
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
    ): Card.Song? {
        val learned = learnedSkills(plan, curriculum)
        return curriculum.units.asReversed().firstNotNullOfOrNull { unit ->
            val open = unit.passages.filter { highestClean(it, history[it.id]) != TempoRung.P100 }
            val passage = open.firstOrNull { it.rights.status == RightsStatus.PUBLIC_DOMAIN } ?: open.firstOrNull()
            if (passage == null || unit.skills.none { it in learned }) {
                null
            } else {
                val clean = highestClean(passage, history[passage.id])
                Card.Song(passage, if (clean == null) TempoRung.WAIT else TempoRung.entries[clean.ordinal + 1], 0)
            }
        }
    }

    /**
     * The skills known to be learned: the due ones flagged learned, and every prerequisite (transitively) of those and
     * of the new skills (a skill is new only once its prerequisites are learned). The plan lists no other skills.
     */
    private fun learnedSkills(
        plan: DailyPlan,
        curriculum: Curriculum,
    ): Set<String> {
        val learned = HashSet<String>()
        val pending = ArrayDeque<String>()
        plan.due.filter { it.learned }.forEach { pending += it.skillId }
        plan.new.forEach { pending += it.prerequisites }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (learned.add(id)) curriculum.graph.byId(id)?.let { pending += it.prerequisites }
        }
        return learned
    }

    private fun passagesOf(curriculum: Curriculum): List<Passage> = curriculum.units.flatMap { it.passages }

    private fun highestClean(
        passage: Passage,
        history: PassageHistory?,
    ): TempoRung? =
        TempoRung.entries.lastOrNull { rung ->
            val bpm = rung.bpm(passage)
            bpm != null && history != null && bpm in history.cleanTempos
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
