package pianoanchor.engine.scheduler

import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.Rating
import pianoanchor.engine.Tunables
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.round

/** Smallest stability FSRS keeps, in days. */
private const val MIN_STABILITY = 0.001

/** One initial stability per rating (w0..w3). */
private const val INITIAL_STABILITY_COUNT = 4
private const val MIN_DIFFICULTY = 1.0
private const val MAX_DIFFICULTY = 10.0

/** Retrievability that the forgetting curve's scale factor is defined to give at one stability of elapsed time. */
private const val CURVE_ANCHOR_RETENTION = 0.9

/** The difficulty update shrinks a step by (10 - difficulty) / 9, so it fades towards the maximum. */
private const val DAMPING_RANGE = 9.0

/** Recall stability gain is scaled by (11 - difficulty). */
private const val DIFFICULTY_CEILING = 11.0

/** Rating numbers 1..4 as the FSRS formulas use them; GOOD is 3. */
private const val GOOD_RATING = 3

/**
 * The FSRS-6 memory model (long-term terms only: no same-day reviews, steps or fuzzing).
 * Formulas in `docs/algorithms/scheduler.md`.
 */
public class Fsrs(
    parameters: FsrsParameters = FsrsParameters.DEFAULT,
) {
    private val w = Weights(parameters.w)
    private val decay = -w.decay
    private val factor = CURVE_ANCHOR_RETENTION.pow(1.0 / decay) - 1.0

    /** State after the first review of an item. */
    public fun initial(rating: Rating): MemoryState =
        MemoryState(clampStability(w.initialStability[rating.ordinal]), clampDifficulty(initialDifficulty(rating)))

    /** State after a review [elapsedDays] (>= 1) after the previous one. */
    public fun next(
        state: MemoryState,
        elapsedDays: Int,
        rating: Rating,
    ): MemoryState {
        require(elapsedDays >= 1) { "elapsedDays must be >= 1, was $elapsedDays" }
        val recall = retrievability(state, elapsedDays)
        val stability =
            if (rating == Rating.AGAIN) {
                lapseStability(state, recall)
            } else {
                recallStability(state, recall, rating)
            }
        return MemoryState(clampStability(stability), clampDifficulty(nextDifficulty(state.difficulty, rating)))
    }

    /** Probability of recall [elapsedDays] (>= 0) after the last review. */
    public fun retrievability(
        state: MemoryState,
        elapsedDays: Int,
    ): Double {
        require(elapsedDays >= 0) { "elapsedDays must be >= 0, was $elapsedDays" }
        return (1.0 + factor * elapsedDays / state.stability).pow(decay)
    }

    /**
     * Days until retrievability falls to [desiredRetention], rounded to the nearest day, clamped to
     * 1..MAX_INTERVAL_DAYS.
     */
    public fun intervalDays(
        state: MemoryState,
        desiredRetention: Double = Tunables.DESIRED_RETENTION,
    ): Int {
        require(desiredRetention > 0.0 && desiredRetention < 1.0) {
            "desiredRetention must be in (0, 1), was $desiredRetention"
        }
        val days = state.stability / factor * (desiredRetention.pow(1.0 / decay) - 1.0)
        return round(days).coerceIn(1.0, Tunables.MAX_INTERVAL_DAYS.toDouble()).toInt()
    }

    private fun ratingNumber(rating: Rating) = rating.ordinal + 1

    private fun initialDifficulty(rating: Rating): Double =
        w.initialDifficulty - exp(w.difficultyGrowth * (ratingNumber(rating) - 1)) + 1.0

    private fun nextDifficulty(
        difficulty: Double,
        rating: Rating,
    ): Double {
        val change = -w.difficultyChange * (ratingNumber(rating) - GOOD_RATING)
        val damped = difficulty + (MAX_DIFFICULTY - difficulty) * change / DAMPING_RANGE
        // Mean reversion towards the (unclamped) initial difficulty of an EASY first review.
        val target = initialDifficulty(Rating.EASY)
        return w.meanReversion * target + (1.0 - w.meanReversion) * damped
    }

    private fun recallStability(
        state: MemoryState,
        recall: Double,
        rating: Rating,
    ): Double {
        val hardPenalty = if (rating == Rating.HARD) w.hardPenalty else 1.0
        val easyBonus = if (rating == Rating.EASY) w.easyBonus else 1.0
        val gain =
            exp(w.recallGain) * (DIFFICULTY_CEILING - state.difficulty) *
                state.stability.pow(-w.recallStabilityPower) * (exp((1.0 - recall) * w.recallRetrievability) - 1.0)
        return state.stability * (1.0 + gain * hardPenalty * easyBonus)
    }

    private fun lapseStability(
        state: MemoryState,
        recall: Double,
    ): Double {
        val longTerm =
            w.lapseScale * state.difficulty.pow(-w.lapseDifficultyPower) *
                ((state.stability + 1.0).pow(w.lapseStabilityPower) - 1.0) *
                exp((1.0 - recall) * w.lapseRetrievability)
        // The short-term cap keeps a lapse from raising stability above its pre-review value.
        val shortTerm = state.stability / exp(w.shortTermRate * w.shortTermOffset)
        return minOf(longTerm, shortTerm)
    }

    private fun clampStability(stability: Double) = maxOf(stability, MIN_STABILITY)

    private fun clampDifficulty(difficulty: Double) = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    /** The weights under the names the formulas use; read in order so no index is a magic number. */
    private class Weights(
        list: List<Double>,
    ) {
        private val values = list.iterator()
        val initialStability = List(INITIAL_STABILITY_COUNT) { values.next() }
        val initialDifficulty = values.next()
        val difficultyGrowth = values.next()
        val difficultyChange = values.next()
        val meanReversion = values.next()
        val recallGain = values.next()
        val recallStabilityPower = values.next()
        val recallRetrievability = values.next()
        val lapseScale = values.next()
        val lapseDifficultyPower = values.next()
        val lapseStabilityPower = values.next()
        val lapseRetrievability = values.next()
        val hardPenalty = values.next()
        val easyBonus = values.next()
        val shortTermRate = values.next()
        val shortTermOffset = values.next()
        val shortTermStabilityPower = values.next()
        val decay = values.next()
    }
}
