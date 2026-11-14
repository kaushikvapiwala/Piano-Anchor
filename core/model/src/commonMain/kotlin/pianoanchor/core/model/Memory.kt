package pianoanchor.core.model

/** How well a review went, as the FSRS memory model expects it: forgot, hard, good or easy. */
public enum class Rating { AGAIN, HARD, GOOD, EASY }

private const val MIN_DIFFICULTY = 1.0
private const val MAX_DIFFICULTY = 10.0

/** FSRS memory state: [stability] in days (> 0) and [difficulty] (1..10). */
public data class MemoryState(
    val stability: Double,
    val difficulty: Double,
) {
    init {
        require(stability > 0.0) { "stability must be > 0, was $stability" }
        require(difficulty in MIN_DIFFICULTY..MAX_DIFFICULTY) { "difficulty must be in 1..10, was $difficulty" }
    }
}
