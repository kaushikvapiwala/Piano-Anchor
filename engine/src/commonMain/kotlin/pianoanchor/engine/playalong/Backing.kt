package pianoanchor.engine.playalong

import pianoanchor.core.model.ChordMark
import pianoanchor.core.model.ChordQuality
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.Tone
import pianoanchor.engine.Tunables

private const val MS_PER_MINUTE = 60_000L
private const val MAJOR_THIRD = 4
private const val MINOR_THIRD = 3
private const val FIFTH = 7
private const val MINOR_SEVENTH = 10

/** The play-along backing: a passage's chord line as tones (`docs/algorithms/play-along.md`). Pure. */
public object Backing {
    /**
     * Tones for [passage]'s chord line at [tempoBpm], offsets from the first beat of bar 1: each chord as a
     * root-position triad (7th added for DOMINANT_7) with its root in the octave from BACKING_LOWEST_ROOT, sounding
     * until the next chord or the passage's end, at BACKING_VELOCITY. Empty when the passage has no chords.
     */
    public fun tones(
        passage: Passage,
        tempoBpm: Int,
    ): List<Tone> {
        val endTicks =
            passage.steps.fold(0) { end, step ->
                step.notes.fold(end) { latest, note -> maxOf(latest, step.onsetTicks + note.lengthTicks) }
            }
        val nextOnsets = passage.chords.drop(1).map { it.onsetTicks } + endTicks
        return passage.chords.zip(nextOnsets).flatMap { (chord, nextTicks) ->
            val startMs = chord.onsetTicks.toMillis(tempoBpm)
            val lengthMs = nextTicks.toMillis(tempoBpm) - startMs
            // A chord at or past the passage's end sounds for no time.
            if (lengthMs <= 0) {
                emptyList()
            } else {
                chord.pitches().map { Tone(MidiNote(it), startMs, lengthMs, Tunables.BACKING_VELOCITY) }
            }
        }
    }

    private fun ChordMark.pitches(): List<Int> {
        val bottom = Tunables.BACKING_LOWEST_ROOT + root
        val third = if (quality == ChordQuality.MINOR) MINOR_THIRD else MAJOR_THIRD
        val triad = listOf(bottom, bottom + third, bottom + FIFTH)
        return if (quality == ChordQuality.DOMINANT_7) triad + (bottom + MINOR_SEVENTH) else triad
    }

    private fun Int.toMillis(bpm: Int): Long = this * MS_PER_MINUTE / (TICKS_PER_QUARTER.toLong() * bpm)
}
