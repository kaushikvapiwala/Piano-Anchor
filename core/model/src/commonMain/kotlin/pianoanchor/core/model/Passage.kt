package pianoanchor.core.model

/**
 * Ticks per quarter note: positions and lengths in a passage are whole ticks. 960 = 2^6·3·5, so halves, thirds,
 * quarters, fifths, sixths and eighths of a beat are exact.
 */
public const val TICKS_PER_QUARTER: Int = 960

/** The slowest written tempo a passage may state: below it a quarter lasts over 3 s and stops feeling like a pulse. */
public const val MIN_TEMPO_BPM: Int = 20

/** The fastest written tempo a passage may state: above it a quarter lasts under 200 ms, past any beginner piece. */
public const val MAX_TEMPO_BPM: Int = 300

private val BEATS_RANGE = 1..32
private val BEAT_UNITS = setOf(1, 2, 4, 8, 16)
private const val QUARTERS_PER_WHOLE = 4

/** One key of a step: its [pitch] and written length in ticks ([lengthTicks] > 0). */
public data class ScoreNote(
    val pitch: MidiNote,
    val lengthTicks: Int,
) {
    init {
        require(lengthTicks > 0) { "note ${pitch.name} must have a positive length, was $lengthTicks ticks" }
    }
}

/**
 * One moment in the music: the keys pressed together at [onsetTicks] (>= 0); one note = melody, several = chord.
 * [notes] is not empty and its pitches are distinct.
 */
public data class Step(
    val onsetTicks: Int,
    val notes: Set<ScoreNote>,
) {
    /** The pitches of [notes]: all that wait mode, grading and the practice screen read. */
    public val pitches: Set<MidiNote> = notes.mapTo(LinkedHashSet()) { it.pitch }

    init {
        require(onsetTicks >= 0) { "step at $onsetTicks ticks must not have a negative onset" }
        require(notes.isNotEmpty()) { "step at $onsetTicks ticks must have at least one note" }
        require(pitches.size == notes.size) { "step at $onsetTicks ticks has the same pitch twice: $notes" }
    }
}

/** [beats] per bar (1..32) of [beatUnit] notes (1, 2, 4, 8 or 16), e.g. 3/4 = `TimeSignature(3, 4)`. */
public data class TimeSignature(
    val beats: Int,
    val beatUnit: Int,
) {
    init {
        require(beats in BEATS_RANGE) { "time signature beats must be in $BEATS_RANGE, was $beats" }
        require(beatUnit in BEAT_UNITS) { "time signature beat unit must be one of $BEAT_UNITS, was $beatUnit" }
    }

    /** The length of one bar in ticks. */
    val barTicks: Int get() = beats * QUARTERS_PER_WHOLE * TICKS_PER_QUARTER / beatUnit
}

/** Letter indexes (C = 0 … B = 6) of the sharps of a key in signature order: F C G D A E B. */
private val SHARP_ORDER = listOf(3, 0, 4, 1, 5, 2, 6)

/** Letter indexes of the flats of a key in signature order: B E A D G C F. */
private val FLAT_ORDER = listOf(6, 2, 5, 1, 4, 0, 3)

private const val MAX_ACCIDENTALS = 7

/** A key by its sharps (1..7) or flats (-1..-7); 0 = C major / A minor. */
public data class KeySignature(
    val sharps: Int,
) {
    init {
        require(sharps in -MAX_ACCIDENTALS..MAX_ACCIDENTALS) { "key signature must have -7..7 sharps, was $sharps" }
    }

    /**
     * Letter indexes (C = 0 … B = 6) the key raises (sharps > 0) or lowers (< 0), in signature order
     * F C G D A E B / B E A D G C F.
     */
    val alteredLetters: List<Int>
        get() = if (sharps >= 0) SHARP_ORDER.take(sharps) else FLAT_ORDER.take(-sharps)

    public companion object {
        /** C major / A minor: no sharps or flats. */
        public val C: KeySignature = KeySignature(0)

        /** G major / E minor: one sharp (F). */
        public val G: KeySignature = KeySignature(1)
    }
}

/** The sustain pedal held from [startTicks] to [endTicks] (start < end, start >= 0). */
public data class PedalSpan(
    val startTicks: Int,
    val endTicks: Int,
) {
    init {
        require(startTicks >= 0) { "a pedal span must not start before 0, was $startTicks" }
        require(endTicks > startTicks) { "a pedal span must end after it starts, was $startTicks to $endTicks" }
    }
}

/**
 * A piece of music to practise. [id] is stable (stored with attempts) and not blank; [steps] is not empty and
 * their onsets strictly increase. [tempoBpm] is the written tempo in quarter notes per minute
 * ([MIN_TEMPO_BPM]..[MAX_TEMPO_BPM]). [rights] says why it may ship. [skills] are the ids of the [Skill]s a run
 * exercises (may be empty: such a passage is never scheduled). A [piece] is a tune or song, listed on the Songs
 * screen; an exercise is not. [keySignature] is the key the staff draws (C by default). [chords] is the chord line
 * the play-along backing sounds (`docs/algorithms/play-along.md`): empty when the passage has none, else sorted by
 * strictly increasing onset. [pedal] is where the sustain pedal is held (marks only, never judged): sorted by start,
 * none overlapping (one may end where the next starts: a change), all within the passage.
 */
public data class Passage(
    val id: String,
    val title: String,
    val steps: List<Step>,
    val timeSignature: TimeSignature,
    val tempoBpm: Int,
    val rights: Rights,
    val skills: Set<String> = emptySet(),
    val piece: Boolean = false,
    val keySignature: KeySignature = KeySignature.C,
    val chords: List<ChordMark> = emptyList(),
    val pedal: List<PedalSpan> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "passage id must not be blank" }
        require(steps.isNotEmpty()) { "passage \"$id\" must have at least one step" }
        steps.zipWithNext().forEach { (a, b) ->
            require(b.onsetTicks > a.onsetTicks) {
                "passage \"$id\": step onsets must strictly increase, got ${a.onsetTicks} then ${b.onsetTicks}"
            }
        }
        chords.zipWithNext().forEach { (a, b) ->
            require(b.onsetTicks > a.onsetTicks) {
                "passage \"$id\": chord onsets must strictly increase, got ${a.onsetTicks} then ${b.onsetTicks}"
            }
        }
        pedal.zipWithNext().forEach { (a, b) ->
            require(b.startTicks >= a.endTicks) {
                "passage \"$id\": pedal spans must be sorted and not overlap, got $a then $b"
            }
        }
        require(pedal.isEmpty() || pedal.last().endTicks <= lengthTicks()) {
            "passage \"$id\": pedal span ${pedal.last()} runs past the end of the passage"
        }
        require(tempoBpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM) {
            "passage \"$id\": tempo must be in $MIN_TEMPO_BPM..$MAX_TEMPO_BPM BPM, was $tempoBpm"
        }
    }

    /** The end of the last bar that has a note in it, in ticks: where a pedal span may end at the latest. */
    private fun lengthTicks(): Int {
        val end =
            steps.fold(0) { end, step ->
                maxOf(end, step.onsetTicks + step.notes.fold(0) { longest, note -> maxOf(longest, note.lengthTicks) })
            }
        val bar = timeSignature.barTicks
        return (end + bar - 1) / bar * bar
    }
}
