package pianoanchor.core.model

/**
 * Ticks per quarter note: positions and lengths in a passage are whole ticks. 960 = 2^6·3·5, so halves, thirds,
 * quarters, fifths, sixths and eighths of a beat are exact.
 */
public const val TICKS_PER_QUARTER: Int = 960

/** The slowest written tempo a passage may state: below it a beat lasts over 3 s and stops feeling like a pulse. */
public const val MIN_TEMPO_BPM: Int = 20

/** The fastest written tempo a passage may state: above it a quarter lasts under 200 ms, past any beginner piece. */
public const val MAX_TEMPO_BPM: Int = 300

private val BEATS_RANGE = 1..32
private val BEAT_UNITS = setOf(1, 2, 4, 8, 16)

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
}

/**
 * A piece of music to practise. [id] is stable (stored with attempts) and not blank; [steps] is not empty and
 * their onsets strictly increase. [tempoBpm] is the written tempo in quarter notes per minute
 * ([MIN_TEMPO_BPM]..[MAX_TEMPO_BPM]).
 */
public data class Passage(
    val id: String,
    val title: String,
    val steps: List<Step>,
    val timeSignature: TimeSignature,
    val tempoBpm: Int,
) {
    init {
        require(id.isNotBlank()) { "passage id must not be blank" }
        require(steps.isNotEmpty()) { "passage \"$id\" must have at least one step" }
        steps.zipWithNext().forEach { (a, b) ->
            require(b.onsetTicks > a.onsetTicks) {
                "passage \"$id\": step onsets must strictly increase, got ${a.onsetTicks} then ${b.onsetTicks}"
            }
        }
        require(tempoBpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM) {
            "passage \"$id\": tempo must be in $MIN_TEMPO_BPM..$MAX_TEMPO_BPM BPM, was $tempoBpm"
        }
    }
}
