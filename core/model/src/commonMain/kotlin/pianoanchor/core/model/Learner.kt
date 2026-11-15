package pianoanchor.core.model

import kotlinx.datetime.LocalDate

/** One FSRS review: a cold check of [skillId] on learner [day] that produced [rating], from [attemptId]. */
public data class Review(
    val skillId: String,
    val day: LocalDate,
    val rating: Rating,
    val attemptId: String,
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
    val learned: Boolean,
)
