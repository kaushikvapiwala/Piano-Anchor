package pianoanchor.testing

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import pianoanchor.core.model.Band
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.SkillGraph
import pianoanchor.engine.learner.toRating
import pianoanchor.engine.scheduler.Fsrs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Probability that a recalled cold check is CLEAN rather than GOOD. */
private const val CLEAN_SHARE = 0.7

/** Standard deviation of the log of each skill's ability factor. */
private const val ABILITY_SIGMA = 0.3

/** What one practice run on a new skill multiplies the true stability by. */
private const val PRACTICE_STABILITY_BOOST = 1.15

/** Box-Muller: a standard normal is sqrt(-2 ln u) cos(2 pi v) for uniform u and v. */
private const val BOX_MULLER_FACTOR = 2.0

/** The true memory of one skill: its FSRS state as of [day]. */
private class TrueMemory(
    var state: MemoryState,
    var day: LocalDate,
)

/**
 * A seeded learner whose true memory of each skill follows the FSRS forgetting curve. Its "true" state starts from
 * the model's own initial state times a per-skill ability factor (log-normal, σ = 0.3), so the model is not given the
 * answer. Recall on a cold check is a Bernoulli draw with the true retrievability. A recalled check is CLEAN with
 * probability 0.7 and GOOD otherwise; a forgotten one is KEEP_PRACTISING.
 */
class SimulatedLearner(
    seed: Long,
    graph: SkillGraph,
) {
    private val random = Random(seed)
    private val fsrs = Fsrs()
    private val ability: Map<String, Double> =
        graph.skills.associate { it.id to exp(ABILITY_SIGMA * gaussian()) }
    private val memories = HashMap<String, TrueMemory>()

    /**
     * The band of a cold check of [skillId] on [day], and the true memory after it. The first check of a skill
     * is always recalled (nothing has been forgotten yet); later ones draw recall from the retrievability after the
     * days since the previous check, which must be at least one.
     */
    fun coldCheck(
        skillId: String,
        day: LocalDate,
    ): Band {
        val known = memories[skillId]
        val retrievability = known?.let { fsrs.retrievability(it.state, it.day.daysUntil(day)) } ?: 1.0
        val band =
            when {
                random.nextDouble() >= retrievability -> Band.KEEP_PRACTISING
                random.nextDouble() < CLEAN_SHARE -> Band.CLEAN
                else -> Band.GOOD
            }
        val rating = band.toRating()
        val next =
            if (known == null) {
                val initial = fsrs.initial(rating)
                MemoryState(initial.stability * ability.getValue(skillId), initial.difficulty)
            } else {
                fsrs.next(known.state, known.day.daysUntil(day), rating)
            }
        memories[skillId] = TrueMemory(next, day)
        return band
    }

    /** One practice run on a new skill: multiplies the true stability by 1.15 (not a review). */
    fun practice(skillId: String) {
        val memory = checkNotNull(memories[skillId]) { "$skillId has not been checked yet" }
        memory.state = memory.state.copy(stability = memory.state.stability * PRACTICE_STABILITY_BOOST)
    }

    /** A standard normal draw (Box-Muller). */
    private fun gaussian(): Double =
        sqrt(-BOX_MULLER_FACTOR * ln(1.0 - random.nextDouble())) *
            cos(BOX_MULLER_FACTOR * PI * random.nextDouble())
}
