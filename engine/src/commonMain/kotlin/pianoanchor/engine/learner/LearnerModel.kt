package pianoanchor.engine.learner

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rating
import pianoanchor.core.model.Review
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.Track
import pianoanchor.core.model.unaided
import pianoanchor.core.ports.LearnerRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.scheduler.Fsrs

/** What to practise on one learner day: the skills [due] for review, then [new] ones to introduce. */
public data class DailyPlan(
    val due: List<SkillState>,
    val new: List<Skill>,
)

/** The attempt id of a placement review: the skill was placed as already learned, not played in a run. */
public const val PLACEMENT_ATTEMPT_ID: String = "placement"

/** The prefix of the attempt id of a review recorded from an ear set. */
internal const val EAR_ATTEMPT_ID_PREFIX: String = "ear:"

/** The prefix of the attempt id of a review recorded from a review quiz. */
internal const val QUIZ_ATTEMPT_ID_PREFIX: String = "quiz:"

/** The rating of a cold check from the band of the run: never EASY (`docs/algorithms/learner-model.md`). */
public fun Band.toRating(): Rating =
    when (this) {
        Band.KEEP_PRACTISING -> Rating.AGAIN
        Band.GOOD -> Rating.HARD
        Band.CLEAN -> Rating.GOOD
    }

/**
 * The cold-check rating of [attempt]: the band's rating, capped at HARD when the run was aided (a hint was shown) or in
 * wait mode (no tempo), so a Good rating needs a timed run without help (`docs/algorithms/learner-model.md`).
 */
public fun Attempt.rating(): Rating {
    val rating = band.toRating()
    return if (rating == Rating.GOOD && (hintShown || tempoBpm == null)) Rating.HARD else rating
}

/**
 * The learner model: turns saved runs into FSRS reviews of the passage's skills, keeps each skill's state, and
 * plans each day's due and new skills. Reads and writes through [repository]; rules in
 * `docs/algorithms/learner-model.md`.
 */
@Suppress("TooManyFunctions") // One small function per learner-model operation, with its private helpers.
public class LearnerModel(
    private val graph: SkillGraph,
    private val repository: LearnerRepositoryPort,
    private val fsrs: Fsrs = Fsrs(),
) {
    /**
     * Records [attempt] (of [passage], played on [day]) as a cold check of each of the passage's skills that has no
     * review on [day] yet. Returns the reviews recorded (empty when none). A spot-loop attempt ([Attempt.spot]) is not
     * a cold check: nothing is recorded for it.
     */
    public suspend fun record(
        passage: Passage,
        attempt: Attempt,
        day: LocalDate,
    ): List<Review> {
        if (attempt.spot != null) return emptyList()
        val states = repository.states()
        val rating = attempt.rating()
        val recorded = ArrayList<Review>()
        for (skillId in passage.skills.sorted()) {
            val review = Review(skillId, day, rating, attempt.id, attempt.unaided)
            recordColdCheck(states[skillId], review)?.let { recorded += it }
        }
        return recorded
    }

    /**
     * Records a cold check of [skillId] rated by [band] (an ear set, not a played passage), with the same
     * one-review-per-day rules as [record]. The review's attempt id is `ear:` plus [id].
     * Returns the review, or null when the skill already has one on [day].
     * Throws [IllegalArgumentException] for a skill that is not in the graph.
     */
    public suspend fun recordEarSet(
        skillId: String,
        band: Band,
        day: LocalDate,
        id: String,
    ): Review? {
        require(graph.byId(skillId) != null) { "unknown skill to record: $skillId" }
        return recordColdCheck(
            repository.states()[skillId],
            Review(skillId, day, band.toRating(), "$EAR_ATTEMPT_ID_PREFIX$id", unaided = true),
        )
    }

    /**
     * Records a cold check of [skillId] rated by [rating] (a review quiz), with the same one-review-per-day rules as
     * [record]. The review's attempt id is `quiz:` plus [id]. Returns the review, or null when the skill already has
     * one on [day]. Throws [IllegalArgumentException] for a skill that is not in the graph.
     */
    public suspend fun recordQuiz(
        skillId: String,
        rating: Rating,
        day: LocalDate,
        id: String,
    ): Review? {
        require(graph.byId(skillId) != null) { "unknown skill to record: $skillId" }
        return recordColdCheck(
            repository.states()[skillId],
            Review(skillId, day, rating, "$QUIZ_ATTEMPT_ID_PREFIX$id", unaided = true),
        )
    }

    /**
     * The learner days from [from] on (inclusive) with at least one ear-set review (attempt id starting with
     * [EAR_ATTEMPT_ID_PREFIX]); empty when none. Throws StorageUnavailableException like the repository.
     */
    public suspend fun earDays(from: LocalDate): Set<LocalDate> =
        repository
            .states()
            .keys
            .flatMap { repository.reviews(it) }
            .filter { it.attemptId.startsWith(EAR_ATTEMPT_ID_PREFIX) && it.day >= from }
            .mapTo(HashSet()) { it.day }

    /**
     * How many review quizzes were recorded on [day] (attempt id starting with [QUIZ_ATTEMPT_ID_PREFIX]). Throws
     * StorageUnavailableException like the repository.
     */
    public suspend fun quizzesOn(day: LocalDate): Int =
        repository
            .states()
            .keys
            .flatMap { repository.reviews(it) }
            .count { it.attemptId.startsWith(QUIZ_ATTEMPT_ID_PREFIX) && it.day == day }

    /** Stores [review] unless the skill ([before] is its state) has a review that day already: the first one counts. */
    private suspend fun recordColdCheck(
        before: SkillState?,
        review: Review,
    ): Review? {
        // A review on this day (or, if the clock went back, a later one) is already the cold check.
        if (before != null && before.lastReviewDay >= review.day) return null
        repository.record(review, review.applyTo(before))
        return review
    }

    /**
     * Records [skills] as placed on [day]: each gets a placement review (rating GOOD, attemptId =
     * [PLACEMENT_ATTEMPT_ID]) and a state with learned = true. Skills already reviewed are left alone. Throws
     * [IllegalArgumentException] for a skill that is not in the graph.
     */
    public suspend fun place(
        skills: Set<String>,
        day: LocalDate,
    ) {
        val unknown = skills.filter { graph.byId(it) == null }
        require(unknown.isEmpty()) { "unknown skills to place: ${unknown.sorted()}" }
        val states = repository.states()
        for (skillId in skills.sorted()) {
            if (skillId in states) continue
            val review = Review(skillId, day, Rating.GOOD, PLACEMENT_ATTEMPT_ID, unaided = true)
            repository.record(review, review.applyTo(null))
        }
    }

    /**
     * Skills due on or before [day] (lowest retrievability first, at most MAX_DUE_PER_DAY), then new skills
     * (unlocked, never introduced; at most MAX_NEW_PER_DAY minus those taught on [day], placed ones excluded;
     * offered only while due.size < MAX_DUE_PER_DAY), in graph order.
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
        // Placed skills were not taught today: they don't use up the day's new-skill slots.
        val introducedToday = states.values.count { it.introducedDay == day && !it.placed }
        val slots = (Tunables.MAX_NEW_PER_DAY - introducedToday).coerceAtLeast(0)
        return DailyPlan(due, rotateNew(states, learned, slots, day))
    }

    /**
     * Fills [slots] one skill at a time from the track introduced least recently (a track never introduced counts as
     * oldest; ties in [Track] order), taking that track's first candidate in graph order.
     */
    private fun rotateNew(
        states: Map<String, SkillState>,
        learned: Set<String>,
        slots: Int,
        day: LocalDate,
    ): List<Skill> {
        val candidates = graph.unlocked(learned).filter { it.id !in states }.toMutableList()
        // Sorted by day, so a later skill of a track overwrites an earlier one: the map holds the latest day.
        val lastIntroduced =
            graph.skills
                .filter { it.id in states }
                .sortedBy { states.getValue(it.id).introducedDay }
                .associateTo(HashMap()) { it.track to states.getValue(it.id).introducedDay }
        val picked = ArrayList<Skill>()
        while (picked.size < slots && candidates.isNotEmpty()) {
            val track =
                candidates
                    .map { it.track }
                    .distinct()
                    .minWith(compareBy<Track, LocalDate?>(nullsFirst()) { lastIntroduced[it] }.thenBy { it.ordinal })
            val skill = candidates.first { it.track == track }
            candidates.remove(skill)
            picked += skill
            lastIntroduced[track] = day
        }
        return picked
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

    /**
     * True for a state that only a placement made: learned without a single recalled cold check. Relies on
     * `LEARNED_MIN_RECALLED_COLD_CHECKS` being above zero, so a skill that earned learned has recalled checks.
     */
    private val SkillState.placed: Boolean get() = learned && recalledColdChecks == 0

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
        // A placement is a claim, not a check: it sets learned but is neither a clean nor a recalled cold check.
        val placement = attemptId == PLACEMENT_ATTEMPT_ID
        val clean = (before?.cleanColdChecks ?: 0) + (rating == Rating.GOOD && !placement).toInt()
        val recalled = (before?.recalledColdChecks ?: 0) + (rating != Rating.AGAIN && !placement).toInt()
        val unaided =
            (before?.unaidedRecalledChecks ?: 0) + (this.unaided && rating != Rating.AGAIN && !placement).toInt()
        val earned = earnsLearned(memory, clean, recalled, unaided)
        val learned = before?.learned == true || placement || earned
        return SkillState(
            skillId = skillId,
            memory = memory,
            introducedDay = if (before == null) day else before.introducedDay,
            lastReviewDay = day,
            dueDay = day.plus(fsrs.intervalDays(memory), DateTimeUnit.DAY),
            cleanColdChecks = clean,
            recalledColdChecks = recalled,
            unaidedRecalledChecks = unaided,
            learned = learned,
        )
    }

    /** Whether these counts and [memory] earn learned (`docs/algorithms/learner-model.md`). */
    private fun earnsLearned(
        memory: MemoryState,
        clean: Int,
        recalled: Int,
        unaided: Int,
    ): Boolean {
        val stableAndClean =
            clean >= Tunables.LEARNED_MIN_CLEAN_COLD_CHECKS && memory.stability >= Tunables.LEARNED_MIN_STABILITY_DAYS
        val mature = memory.stability >= Tunables.LEARNED_MATURE_STABILITY_DAYS
        return recalled >= Tunables.LEARNED_MIN_RECALLED_COLD_CHECKS &&
            unaided >= Tunables.LEARNED_MIN_UNAIDED_CHECKS &&
            (stableAndClean || mature)
    }
}

private fun Boolean.toInt(): Int = if (this) 1 else 0
