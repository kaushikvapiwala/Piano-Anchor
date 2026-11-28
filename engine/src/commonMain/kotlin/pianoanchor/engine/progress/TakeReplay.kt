package pianoanchor.engine.progress

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.PLAYED_NOTE_ORDER
import pianoanchor.core.model.Tone
import pianoanchor.engine.Tunables

private const val NANOS_PER_MS = 1_000_000L

/** Turns a saved take into tones for the speaker (`docs/algorithms/take-replay.md`). Pure. */
public object TakeReplay {
    /**
     * The take as tones: each played note from its key-down to its sounding end, offsets from the first note,
     * velocity kept. Empty for a take with no notes. A note that sounds for less than a millisecond (a tap) plays for
     * one, as a tone needs a length. Long takes are cut at [Tunables.TAKE_REPLAY_MAX_MS]: notes starting at or after it
     * are dropped and one crossing it ends there.
     */
    public fun tones(attempt: Attempt): List<Tone> {
        val notes = attempt.playedNotes.sortedWith(PLAYED_NOTE_ORDER)
        val origin = notes.firstOrNull()?.downNanos ?: return emptyList()
        return notes.mapNotNull { note ->
            val start = (note.downNanos - origin) / NANOS_PER_MS
            if (start >= Tunables.TAKE_REPLAY_MAX_MS) return@mapNotNull null
            val length = ((note.soundingEndNanos - note.downNanos) / NANOS_PER_MS).coerceAtLeast(1)
            Tone(note.pitch, start, minOf(length, Tunables.TAKE_REPLAY_MAX_MS - start), note.velocity)
        }
    }
}
