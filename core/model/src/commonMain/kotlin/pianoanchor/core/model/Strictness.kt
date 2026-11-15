package pianoanchor.core.model

/** How strictly a run is graded: the learner's choice in Settings (the engine maps each to a [StrictnessProfile]). */
public enum class Strictness { GENTLE, STANDARD, STRICT }

/**
 * What a [Strictness] sets: the early/late band is max([timingToleranceMs], [timingToleranceBeatFraction] of a beat),
 * and an accuracy of at least [cleanMinAccuracy] is CLEAN, of at least [goodMinAccuracy] GOOD.
 */
public data class StrictnessProfile(
    val timingToleranceMs: Long,
    val timingToleranceBeatFraction: Double,
    val cleanMinAccuracy: Double,
    val goodMinAccuracy: Double,
)
