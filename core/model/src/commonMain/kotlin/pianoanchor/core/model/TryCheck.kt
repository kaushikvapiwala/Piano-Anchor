package pianoanchor.core.model

/**
 * The small, piano-checked task of one course concept (its "Try", `docs/algorithms/try-checks.md`). Notes are MIDI
 * note numbers; middle C = 60.
 */
public sealed interface TryCheck {
    /** Any key counts (the app can't see fingers; it trusts the learner). */
    public data object AnyKey : TryCheck

    /** Play [note]; [lit] says whether the strip lights it. */
    public data class PlayKey(
        val note: Int,
        val lit: Boolean,
    ) : TryCheck

    /** Play every key of [pitchClass] (0 = C ... 11 = B) in [range], each once, in any order. */
    public data class FindAll(
        val pitchClass: Int,
        val range: IntRange,
    ) : TryCheck

    /** Play [notes] in order. */
    public data class Sequence(
        val notes: List<Int>,
    ) : TryCheck

    /** Play [note] [times] times with the click at [bpm]; timing is not judged. */
    public data class Repeat(
        val note: Int,
        val times: Int,
        val bpm: Int,
    ) : TryCheck

    /** Hold [note] for [clicks] clicks at [bpm]. */
    public data class Hold(
        val note: Int,
        val clicks: Int,
        val bpm: Int,
    ) : TryCheck

    /** Play [note] softly, then loudly. */
    public data class SoftThenLoud(
        val note: Int,
    ) : TryCheck

    /** Play [note] on every click for [bars] bars of 4 at [bpm], louder on beat 1. */
    public data class StressBeatOne(
        val note: Int,
        val bars: Int,
        val bpm: Int,
    ) : TryCheck

    /**
     * Play [note] in the rhythm [lengths] (ticks, `TICKS_PER_QUARTER` = a quarter) with the click at [bpm]; intervals
     * are judged.
     */
    public data class Rhythm(
        val note: Int,
        val lengths: List<Int>,
        val bpm: Int,
    ) : TryCheck {
        init {
            require(lengths.size >= 2 && lengths.all { it > 0 }) {
                "a rhythm needs at least two lengths, all above 0: $lengths"
            }
        }
    }

    /**
     * Play [notes] in order, one per click at [bpm], evenly: like Sequence for the notes and like Rhythm for the timing
     * (every length one quarter).
     */
    public data class EvenSequence(
        val notes: List<Int>,
        val bpm: Int,
    ) : TryCheck {
        init {
            require(notes.size >= 2) { "an even sequence needs at least two notes: $notes" }
            require(bpm > 0) { "an even sequence needs a tempo above 0: $bpm" }
        }
    }

    /** Play [notes] together. */
    public data class Chord(
        val notes: Set<Int>,
    ) : TryCheck

    /** Play [notes] in order, joined: each key goes down as the one before comes up (no gap, no blur). */
    public data class Legato(
        val notes: List<Int>,
    ) : TryCheck {
        init {
            require(notes.size >= 2) { "a legato check needs at least two notes: $notes" }
        }
    }

    /** Press the pedal, play [chord], lift the hands: the sound must ring on until the pedal comes up. */
    public data class PedalHold(
        val chord: Set<Int>,
    ) : TryCheck {
        init {
            require(chord.isNotEmpty()) { "a pedal hold needs a chord" }
        }
    }

    /** Play [chords] in order with the pedal, changing it (up, then down again) just after each new chord. */
    public data class PedalChange(
        val chords: List<Set<Int>>,
    ) : TryCheck {
        init {
            require(chords.size >= 2 && chords.all { it.isNotEmpty() }) {
                "a pedal change needs at least two chords, none empty: $chords"
            }
        }
    }

    /** Play passage [passageId] in wait mode with right notes (the Use step's check, for piece concepts). */
    public data class Passage(
        val passageId: String,
    ) : TryCheck
}
