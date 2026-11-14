package pianoanchor.testing

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.analysis.timingToleranceNanos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.random.nextInt

// Injection bands: each safely past the threshold it tests.
private val WRONG_NOTE_SEMITONES = 3..11
private const val OCTAVE_SEMITONES = 12
private const val MIN_EXTRA_SEMITONES = 3
private const val EXTRA_PITCH_REACH = 12
private const val MIN_EXTRA_GAP_NANOS = 120_000_000L
private const val EXTRA_MIN_STEP_GAP_NANOS = 400_000_000L
private const val EXTRA_GAP_FRACTION = 0.4
private const val EXTRA_LENGTH_NANOS = 100_000_000L
private val MISTOUCH_SEMITONES = 1..2
private const val MISTOUCH_MIN_OFFSET_NANOS = 5_000_000L
private const val MISTOUCH_MAX_OFFSET_NANOS = 35_000_000L
private const val MISTOUCH_MIN_LENGTH_NANOS = 30_000_000L
private const val MISTOUCH_MAX_LENGTH_NANOS = 80_000_000L
private val MISTOUCH_VELOCITY_FRACTION = 0.3..0.6
private val TIMING_SHIFT_TOLERANCES = 2.0..3.0
private val HESITATION_GAP_FACTOR = 2.5..4.0
private const val HESITATION_MIN_DELAY_NANOS = 500_000_000L
private val TOO_SHORT_FRACTION = 0.2..0.4
private val TOO_LONG_FRACTION = 1.8..2.5

/**
 * Injects one labelled error into a clean take (`docs/algorithms/performance-analysis.md`), at a random eligible
 * score note. Same seed, same injections.
 */
class ErrorInjector(
    seed: Long,
) {
    private val random = Random(seed)

    /**
     * [take] with one error of [kind] at a random eligible score note, labelled. [take] must be clean (no labels)
     * and its passage must have a note eligible for [kind] (e.g. a chord for [ErrorKind.INCOMPLETE_CHORD]), else
     * [IllegalArgumentException].
     */
    fun inject(
        take: LabelledTake,
        kind: ErrorKind,
    ): LabelledTake {
        require(take.labels.isEmpty()) { "inject into a clean take only, this one has ${take.labels.size} labels" }
        val score = ScoreView(take)
        val candidates =
            when (kind) {
                ErrorKind.WRONG_NOTE -> score.wrongNotes()
                ErrorKind.MISSED_NOTE -> score.missedNotes()
                ErrorKind.EXTRA_NOTE -> score.extraNotes()
                ErrorKind.INCOMPLETE_CHORD -> score.incompleteChords()
                ErrorKind.MISTOUCH -> score.mistouches()
                ErrorKind.OCTAVE -> score.octaves()
                ErrorKind.EARLY -> score.timingShifts(-1)
                ErrorKind.LATE -> score.timingShifts(1)
                ErrorKind.HESITATION -> score.hesitations()
                ErrorKind.TOO_SHORT -> score.lengths(TOO_SHORT_FRACTION)
                ErrorKind.TOO_LONG -> score.lengths(TOO_LONG_FRACTION)
            }.filter(score::fits)
        require(candidates.isNotEmpty()) { "passage \"${take.passage.id}\" has no score note eligible for $kind" }
        val ref = candidates.map { it.ref }.distinct().random(random)
        val change = candidates.filter { it.ref == ref }.random(random)
        return take.copy(
            notes = (take.notes - change.remove.toSet() + change.add).sortedInTakeOrder(),
            labels = listOf(Finding(kind, ref, change.played)),
        )
    }

    /** One way to inject an error at [ref]: drop [remove], add [add]; [played] goes into the label. */
    private class Change(
        val ref: ScoreNoteRef,
        val remove: List<PlayedNote>,
        val add: List<PlayedNote>,
        val played: PlayedNote?,
    )

    private inner class ScoreView(
        private val take: LabelledTake,
    ) {
        private val steps = take.passage.steps
        private val notesOf =
            steps
                .withIndex()
                .flatMap { (i, step) ->
                    step.notes.map { ScoreNoteRef(i, it.pitch) to it }
                }.toMap()
        private val refs = notesOf.keys.toList()
        private val scorePitches = steps.flatMap { it.pitches }.map { it.number }.toSet()

        /** The played note of each score note: same pitch, onset nearest the written onset. */
        private val playedOf =
            refs.associateWith { ref ->
                val written = take.writtenOnsetNanos(ref.stepIndex)
                take.notes.filter { it.pitch == ref.pitch }.minBy { abs(it.downNanos - written) }
            }

        private fun singleNoteRefs() = refs.filter { steps[it.stepIndex].notes.size == 1 }

        private fun ScoreNoteRef.played() = playedOf.getValue(this)

        private fun replace(
            ref: ScoreNoteRef,
            new: PlayedNote,
        ) = Change(ref, listOf(ref.played()), listOf(new), new)

        private fun shiftedPitches(
            ref: ScoreNoteRef,
            semitones: IntRange,
        ) = semitones
            .flatMap { listOf(ref.pitch.number - it, ref.pitch.number + it) }
            .filter { it in MidiNote.RANGE && MidiNote(it) !in steps[ref.stepIndex].pitches }
            .map(::MidiNote)

        private fun dropped(ref: ScoreNoteRef) = Change(ref, listOf(ref.played()), emptyList(), null)

        fun wrongNotes() =
            singleNoteRefs().flatMap { r ->
                shiftedPitches(r, WRONG_NOTE_SEMITONES).map { replace(r, r.played().copy(pitch = it)) }
            }

        fun octaves() =
            refs.flatMap { r ->
                shiftedPitches(r, OCTAVE_SEMITONES..OCTAVE_SEMITONES).map { replace(r, r.played().copy(pitch = it)) }
            }

        fun missedNotes() = singleNoteRefs().map(::dropped)

        fun incompleteChords() = refs.filter { steps[it.stepIndex].notes.size > 1 }.map(::dropped)

        fun extraNotes(): List<Change> {
            val low = max(MidiNote.RANGE.first, scorePitches.min() - EXTRA_PITCH_REACH)
            val high = minOf(MidiNote.RANGE.last, scorePitches.max() + EXTRA_PITCH_REACH)
            val pitches = (low..high).filter { p -> scorePitches.all { abs(it - p) >= MIN_EXTRA_SEMITONES } }
            return (0 until steps.size - 1).flatMap { i ->
                val gap = take.writtenOnsetNanos(i + 1) - take.writtenOnsetNanos(i)
                val down = take.writtenOnsetNanos(i) + (gap * EXTRA_GAP_FRACTION).roundToLong()
                val clear =
                    gap >= EXTRA_MIN_STEP_GAP_NANOS &&
                        take.notes.all { abs(it.downNanos - down) >= MIN_EXTRA_GAP_NANOS }
                if (!clear) return@flatMap emptyList()
                val velocity = random.nextInt(CLEAN_VELOCITY_RANGE)
                val r = lowestOf(i)
                pitches.map {
                    val up = down + EXTRA_LENGTH_NANOS
                    val extra = PlayedNote(MidiNote(it), velocity, down, up, up)
                    Change(r, emptyList(), listOf(extra), extra)
                }
            }
        }

        fun mistouches() =
            refs.flatMap { r ->
                val correct = r.played()
                val down = correct.downNanos + random.nextLong(MISTOUCH_MIN_OFFSET_NANOS, MISTOUCH_MAX_OFFSET_NANOS + 1)
                val up = down + random.nextLong(MISTOUCH_MIN_LENGTH_NANOS, MISTOUCH_MAX_LENGTH_NANOS + 1)
                val velocity = max(1, (correct.velocity * random.nextDouble(MISTOUCH_VELOCITY_FRACTION)).roundToInt())
                shiftedPitches(r, MISTOUCH_SEMITONES).map {
                    val touch = PlayedNote(it, velocity, down, up, up)
                    Change(r, emptyList(), listOf(touch), touch)
                }
            }

        fun timingShifts(sign: Int): List<Change> {
            val tolerance = timingToleranceNanos(take.beatNanos)
            val shift = sign * (tolerance * random.nextDouble(TIMING_SHIFT_TOLERANCES)).roundToLong()
            return refs.mapNotNull { r ->
                val i = r.stepIndex
                val new = r.played().shiftedBy(shift)
                val afterPrevious = i == 0 || stepDowns(i - 1).all { it < new.downNanos }
                val beforeNext = i == steps.lastIndex || stepDowns(i + 1).all { it > new.downNanos }
                if (afterPrevious && beforeNext) replace(r, new) else null
            }
        }

        fun hesitations() =
            (1 until steps.size).map { k ->
                val gap = take.writtenOnsetNanos(k) - take.writtenOnsetNanos(k - 1)
                val delay =
                    max((gap * random.nextDouble(HESITATION_GAP_FACTOR)).roundToLong(), HESITATION_MIN_DELAY_NANOS)
                val later = playedOf.filterKeys { it.stepIndex >= k }
                val shifted = later.mapValues { (_, n) -> n.shiftedBy(delay) }
                val r = lowestOf(k)
                Change(r, later.values.toList(), shifted.values.toList(), shifted.getValue(r))
            }

        fun lengths(fraction: ClosedFloatingPointRange<Double>): List<Change> {
            val factor = random.nextDouble(fraction)
            return refs.map { r ->
                val old = r.played()
                val up = old.downNanos + (take.writtenLengthNanos(notesOf.getValue(r)) * factor).roundToLong()
                replace(r, old.copy(upNanos = up, soundingEndNanos = up))
            }
        }

        private fun lowestOf(i: Int) = ScoreNoteRef(i, steps[i].pitches.minBy { it.number })

        private fun stepDowns(i: Int) = steps[i].notes.map { ScoreNoteRef(i, it.pitch).played().downNanos }

        /** No added note overlaps a remaining note of the same pitch: a key can't go down twice at once. */
        fun fits(change: Change): Boolean {
            val remaining = take.notes - change.remove.toSet()
            return change.add.all { new ->
                remaining.none {
                    it.pitch == new.pitch && it.downNanos <= new.soundingEndNanos &&
                        new.downNanos <= it.soundingEndNanos
                }
            }
        }
    }
}
