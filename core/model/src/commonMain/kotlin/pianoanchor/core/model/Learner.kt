package pianoanchor.core.model

import kotlinx.datetime.LocalDate

/**
 * One FSRS review: a cold check of [skillId] on learner [day] that produced [rating], from [attemptId]. [unaided] is
 * true when the check was played without help (a perform run with no hint, an ear set or a placement); reviews stored
 * before hints faded count as unaided.
 */
public data class Review(
    val skillId: String,
    val day: LocalDate,
    val rating: Rating,
    val attemptId: String,
    val unaided: Boolean,
)

/** Cached learner state of one skill, derived from its reviews. */
public data class SkillState(
    val skillId: String,
    val memory: MemoryState,
    val introducedDay: LocalDate,
    val lastReviewDay: LocalDate,
    val dueDay: LocalDate,
    /** CLEAN cold checks on distinct days. */
    val cleanColdChecks: Int,
    /** Cold checks rated Hard or Good (recalled), counted like cleanColdChecks; never below it. */
    val recalledColdChecks: Int,
    /** Unaided cold checks rated Hard or Good, counted like cleanColdChecks; never above recalledColdChecks. */
    val unaidedRecalledChecks: Int,
    val learned: Boolean,
) {
    init {
        require(cleanColdChecks >= 0) { "$skillId: cleanColdChecks $cleanColdChecks is negative" }
        require(recalledColdChecks >= cleanColdChecks) {
            "$skillId: recalledColdChecks $recalledColdChecks is below cleanColdChecks $cleanColdChecks"
        }
        require(unaidedRecalledChecks in 0..recalledColdChecks) {
            "$skillId: unaidedRecalledChecks $unaidedRecalledChecks is outside 0..$recalledColdChecks"
        }
    }
}
