package pianoanchor.testing

import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.nextInt

/** Standard deviation of a clean take's onset jitter per step: a steady amateur's spread. */
const val ONSET_JITTER_SIGMA_NANOS: Double = 15_000_000.0

/** The notes of a chord start up to this long after the step's (jittered) onset. */
const val CHORD_SPREAD_NANOS: Long = 10_000_000

/** Onset velocities of a clean take: a comfortable mezzo range. */
val CLEAN_VELOCITY_RANGE: IntRange = 60..90

/** A clean note lasts this fraction of its written length (players release a little early). */
val CLEAN_LENGTH_FRACTION: ClosedFloatingPointRange<Double> = 0.80..0.95

/** A key comes up at least this long before it is struck again. */
const val REPEAT_GAP_NANOS: Long = 5_000_000

/**
 * Renders clean takes of a passage at a tempo: onset jitter (Gaussian, σ = [ONSET_JITTER_SIGMA_NANOS]) per step,
 * chord notes spread over [CHORD_SPREAD_NANOS], velocity in [CLEAN_VELOCITY_RANGE], length in
 * [CLEAN_LENGTH_FRACTION] of written, no pedal. A note is cut short so it comes up [REPEAT_GAP_NANOS] before its
 * pitch is struck again. Same seed, same takes.
 */
class TakeGenerator(
    seed: Long,
) {
    private val random = Random(seed)

    /** A clean take of [passage] at [tempoBpm], its notes in onset order (ties by pitch). */
    fun clean(
        passage: Passage,
        tempoBpm: Int,
    ): LabelledTake {
        val timing = LabelledTake(passage, tempoBpm, emptyList(), emptyList())
        val notes =
            passage.steps.withIndex().flatMap { (i, step) ->
                val onset =
                    timing.writtenOnsetNanos(i) + (random.nextGaussian() * ONSET_JITTER_SIGMA_NANOS).roundToLong()
                step.notes.map { note ->
                    val down = onset + random.nextLong(CHORD_SPREAD_NANOS + 1)
                    val length =
                        (
                            timing.writtenLengthNanos(
                                note,
                            ) * random.nextDouble(CLEAN_LENGTH_FRACTION)
                        ).roundToLong()
                    PlayedNote(note.pitch, random.nextInt(CLEAN_VELOCITY_RANGE), down, down + length, down + length)
                }
            }
        return timing.copy(notes = cutBeforeRepeats(notes).sortedInTakeOrder())
    }

    private fun cutBeforeRepeats(notes: List<PlayedNote>): List<PlayedNote> =
        notes.groupBy { it.pitch }.values.flatMap { samePitch ->
            val byOnset = samePitch.sortedBy { it.downNanos }
            byOnset.mapIndexed { i, note ->
                val next = byOnset.getOrNull(i + 1) ?: return@mapIndexed note
                val up = max(note.downNanos, min(note.upNanos, next.downNanos - REPEAT_GAP_NANOS))
                note.copy(upNanos = up, soundingEndNanos = up)
            }
        }
}

/** Played notes in onset order, ties by pitch: the order `PlayedNotes.from` returns. */
internal fun List<PlayedNote>.sortedInTakeOrder(): List<PlayedNote> =
    sortedWith(compareBy<PlayedNote> { it.downNanos }.thenBy { it.pitch.number })

/** A uniform draw from [range]. */
internal fun Random.nextDouble(range: ClosedFloatingPointRange<Double>): Double =
    nextDouble(range.start, range.endInclusive)

/** A standard normal draw (Box–Muller). */
internal fun Random.nextGaussian(): Double = sqrt(2.0 * -ln(1.0 - nextDouble())) * cos(2.0 * PI * nextDouble())
