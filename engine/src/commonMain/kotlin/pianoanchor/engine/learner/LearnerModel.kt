package pianoanchor.engine.learner

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rating
import pianoanchor.core.model.Review
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.SkillState
import pianoanchor.core.ports.LearnerRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.scheduler.Fsrs

/** What to practise on one learner day: the skills [due] for review, then [new] ones to introduce. */
public data class DailyPlan(
    val due: List<SkillState>,
    val new: List<Skill>,
)

/** The rating of a cold check from the band of the run: never EASY (`docs/algorithms/learner-model.md`). */
public fun Band.toRating(): Rating =
    when (this) {
        Band.KEEP_PRACTISING -> Rating.AGAIN
        Band.GOOD -> Rating.HARD
        Band.CLEAN -> Rating.GOOD
    }

/**
 * The learner model: turns saved runs into FSRS reviews of the passage's skills, keeps each skill's state, and
 * plans each day's due and new skills. Reads and writes through [repository]; rules in
 * `docs/algorithms/learner-model.md`.
 */
public class LearnerModel(
    private val graph: SkillGraph,
    private val repository: LearnerRepositoryPort,
    private val fsrs: Fsrs = Fsrs(),
) {
    /**
     * Records [attempt] (of [passage], played on [day]) as a cold check of each of the passage's skills that has no
     * review on [day] yet. Returns the reviews recorded (empty when none).
     */
    public suspend fun record(
        passage: Passage,
        attempt: Attempt,
        day: LocalDate,
    ): List<Review> {
        val states = repository.states()
        val rating = attempt.band.toRating()
        val recorded = ArrayList<Review>()
        for (skillId in passage.skills.sorted()) {
            val before = states[skillId]
            // A review on this day (or, if the clock went back, a later one) is already the cold check.
            if (before != null && before.lastReviewDay >= day) continue
            val review = Review(skillId, day, rating, attempt.id)
            repository.record(review, review.applyTo(before))
            recorded += review
        }
        return recorded
    }

    /**
     * Skills due on or before [day] (lowest retrievability first, at most MAX_DUE_PER_DAY), then new skills
     * (unlocked, never introduced; at most MAX_NEW_PER_DAY minus those already introduced on [day]; offered only
     * while due.size < MAX_DUE_PER_DAY), in graph order.
     */
    public suspend fun plan(day: LocalDate): DailyPlan {
        val states = repository.states()
        val due =
            states.values
                .filter { it.dueDay <= day }
                .sortedWith(compareBy({ retrievability(it, day) }, { it.skillId }))
                .take(Tunables.MAX_DUE_PER_DAY)
        if (due.size >= Tunables.MAX_DUE_PER_DAY) return DailyPlan(due, emptyList())
        val learned = states.values.filter { it.learned }.mapTo(HashSet()) { it.skillId }
        val introducedToday = states.values.count { it.introducedDay == day }
        val slots = (Tunables.MAX_NEW_PER_DAY - introducedToday).coerceAtLeast(0)
        val new = graph.unlocked(learned).filter { it.id !in states }.take(slots)
        return DailyPlan(due, new)
    }

    /** The ids of every learned skill (the plan lists only the due and new ones). */
    public suspend fun learnedSkills(): Set<String> =
        repository
            .states()
            .values
            .filter { it.learned }
            .mapTo(HashSet()) { it.skillId }

    /** Rebuilds every cached state by replaying the review log through FSRS. */
    public suspend fun replay() {
        val ids = (graph.skills.map { it.id } + repository.states().keys).distinct()
        val rebuilt =
            ids.mapNotNull { id ->
                repository.reviews(id).fold(null as SkillState?) { state, review -> review.applyTo(state) }
            }
        repository.replaceStates(rebuilt)
    }

    private fun retrievability(
        state: SkillState,
        day: LocalDate,
    ): Double = fsrs.retrievability(state.memory, state.lastReviewDay.daysUntil(day).coerceAtLeast(0))

    /** The state after this review: the first review starts the skill, later ones update [before]. */
    private fun Review.applyTo(before: SkillState?): SkillState {
        val memory =
            if (before == null) {
                fsrs.initial(rating)
            } else {
                fsrs.next(before.memory, before.lastReviewDay.daysUntil(day), rating)
            }
        val clean = (before?.cleanColdChecks ?: 0) + if (rating == Rating.GOOD) 1 else 0
        val recalled = (before?.recalledColdChecks ?: 0) + if (rating == Rating.AGAIN) 0 else 1
        val stableAndClean =
            clean >= Tunables.LEARNED_MIN_CLEAN_COLD_CHECKS && memory.stability >= Tunables.LEARNED_MIN_STABILITY_DAYS
        val mature = memory.stability >= Tunables.LEARNED_MATURE_STABILITY_DAYS
        val earned = recalled >= Tunables.LEARNED_MIN_RECALLED_COLD_CHECKS && (stableAndClean || mature)
        val learned = before?.learned == true || earned
        return SkillState(
            skillId = skillId,
            memory = memory,
            introducedDay = if (before == null) day else before.introducedDay,
            lastReviewDay = day,
            dueDay = day.plus(fsrs.intervalDays(memory), DateTimeUnit.DAY),
            cleanColdChecks = clean,
            recalledColdChecks = recalled,
            learned = learned,
        )
    }
}
