package pianoanchor.engine.notation

import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PedalSpan
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.TICKS_PER_QUARTER
import kotlin.math.abs

/** The two staves of a grand staff. */
public enum class Staff { TREBLE, BASS }

private const val QUARTERS_PER_WHOLE = 4
private const val WHOLE_TICKS = QUARTERS_PER_WHOLE * TICKS_PER_QUARTER
private const val HALF_TICKS = WHOLE_TICKS / 2
private const val QUARTER_TICKS = TICKS_PER_QUARTER
private const val EIGHTH_TICKS = TICKS_PER_QUARTER / 2
private const val DOTTED_HALF_TICKS = HALF_TICKS + QUARTER_TICKS
private const val DOTTED_QUARTER_TICKS = QUARTER_TICKS + EIGHTH_TICKS

/** A written note value, with its length in ticks; the rules are in `docs/algorithms/engraving.md`. */
public enum class NoteValue(
    public val ticks: Int,
    public val dotted: Boolean,
) {
    WHOLE(WHOLE_TICKS, false),
    DOTTED_HALF(DOTTED_HALF_TICKS, true),
    HALF(HALF_TICKS, false),
    DOTTED_QUARTER(DOTTED_QUARTER_TICKS, true),
    QUARTER(QUARTER_TICKS, false),
    EIGHTH(EIGHTH_TICKS, false),
    ;

    public companion object {
        /** The value that is exactly [ticks] long, or null when there is none. */
        public fun exact(ticks: Int): NoteValue? = entries.firstOrNull { it.ticks == ticks }

        /** The value for [ticks]: the exact one, else the longest that fits, else the shortest value. */
        internal fun fitting(ticks: Int): NoteValue = entries.firstOrNull { it.ticks <= ticks } ?: EIGHTH
    }
}

/** An accidental sign drawn before a notehead. */
public enum class Accidental { SHARP, NATURAL }

/** Which way a stem points; [NONE] for a note without one (a whole note). */
public enum class StemDirection { UP, DOWN, NONE }

/** One notehead of a chord: its [pitch] and the [accidental] drawn before it (null: none). */
public data class EngravedNote(
    val pitch: MidiNote,
    val accidental: Accidental?,
)

/**
 * The notes of step [stepIndex] that sit on [staff], low to high, written as one [value] starting at [onsetTicks].
 * [stem] is the shared stem; [flag] is true for a lone eighth; [beamGroup] numbers the beam an eighth belongs to
 * (null: none).
 */
public data class EngravedChord(
    val stepIndex: Int,
    val staff: Staff,
    val onsetTicks: Int,
    val value: NoteValue,
    val notes: List<EngravedNote>,
    val stem: StemDirection,
    val flag: Boolean,
    val beamGroup: Int?,
)

/** A rest of [value] on [staff] starting at [onsetTicks]. */
public data class EngravedRest(
    val staff: Staff,
    val onsetTicks: Int,
    val value: NoteValue,
)

/**
 * One pedal mark: a line from [fromTicks] to [toTicks] with an inverted-V notch at each of [changeTicks], where one
 * span ends and the next starts.
 */
public data class EngravedPedalLine(
    val fromTicks: Int,
    val toTicks: Int,
    val changeTicks: List<Int>,
)

/**
 * A passage as symbols: [chords] by step then staff, [rests] by onset then staff, and the [key]. [pedal] are the
 * pedal lines, drawn under [pedalStaff]; [pedalEachBar] is true when every bar starts a pedal span.
 */
public data class Engraved(
    val chords: List<EngravedChord>,
    val rests: List<EngravedRest>,
    val key: KeySignature,
    val pedal: List<EngravedPedalLine> = emptyList(),
    val pedalStaff: Staff = Staff.BASS,
    val pedalEachBar: Boolean = false,
)

/** Turns a passage's steps into the symbols a staff draws. Pure music-theory rules; no drawing. */
public object Engraving {
    /**
     * Engraves [passage]; see `docs/algorithms/engraving.md` for every rule. A pedal line is cut at each of
     * [systemBreakTicks] (where a new system of bars starts), so no line crosses a break.
     */
    public fun engrave(
        passage: Passage,
        systemBreakTicks: List<Int> = emptyList(),
    ): Engraved {
        val barTicks = passage.timeSignature.barTicks
        val lastNoteEnd = passage.steps.fold(0) { end, step -> maxOf(end, step.onsetTicks + step.notes.longest()) }
        val endTicks = (lastNoteEnd + barTicks - 1) / barTicks * barTicks
        val starts = passage.pedal.map { it.startTicks }.toSet()
        val groups = BeamNumbers()
        val chords = ArrayList<EngravedChord>()
        val rests = ArrayList<EngravedRest>()
        for (staff in Staff.entries) {
            val written = chordsOn(staff, passage)
            if (written.isEmpty()) continue
            chords += finish(staff, written, passage.keySignature, barTicks, groups)
            rests += restsOn(staff, written, barTicks, endTicks)
        }
        return Engraved(
            chords = chords.sortedWith(compareBy({ it.stepIndex }, { it.staff })),
            rests = rests.sortedWith(compareBy({ it.onsetTicks }, { it.staff })),
            key = passage.keySignature,
            pedal = pedalLines(passage.pedal, systemBreakTicks),
            pedalStaff = if (chords.any { it.staff == Staff.BASS }) Staff.BASS else Staff.TREBLE,
            pedalEachBar = passage.pedal.isNotEmpty() && (0 until endTicks step barTicks).all { it in starts },
        )
    }
}

/**
 * The pedal spans as lines: spans that meet join into one line with a notch at the joint; a span across a break, and a
 * joint on one, end a line and start the next.
 */
private fun pedalLines(
    spans: List<PedalSpan>,
    breaks: List<Int>,
): List<EngravedPedalLine> {
    val pieces =
        spans.flatMap { span ->
            val cuts = breaks.filter { it > span.startTicks && it < span.endTicks }.sorted()
            (listOf(span.startTicks) + cuts + span.endTicks).zipWithNext()
        }
    val lines = ArrayList<EngravedPedalLine>()
    pieces.forEach { (from, to) ->
        val last = lines.lastOrNull()
        if (last != null && last.toTicks == from && from !in breaks) {
            lines[lines.lastIndex] = last.copy(toTicks = to, changeTicks = last.changeTicks + from)
        } else {
            lines += EngravedPedalLine(from, to, emptyList())
        }
    }
    return lines
}

/** A chord before its stem, beam and accidentals are known: [lengthTicks] is the longest note's real length. */
private class Written(
    val stepIndex: Int,
    val onsetTicks: Int,
    val pitches: List<MidiNote>,
    val value: NoteValue,
    val lengthTicks: Int,
)

/** The length of the longest note, in ticks. */
private fun Iterable<ScoreNote>.longest(): Int = fold(0) { longest, note -> maxOf(longest, note.lengthTicks) }

private fun staffOf(pitch: MidiNote): Staff = if (pitch.number < MIDDLE_C) Staff.BASS else Staff.TREBLE

private fun chordsOn(
    staff: Staff,
    passage: Passage,
): List<Written> =
    passage.steps.mapIndexedNotNull { index, step ->
        val notes = step.notes.filter { staffOf(it.pitch) == staff }
        if (notes.isEmpty()) {
            null
        } else {
            Written(
                stepIndex = index,
                onsetTicks = step.onsetTicks,
                pitches = notes.map { it.pitch }.sortedBy { it.number },
                value =
                    NoteValue.fitting(
                        notes.fold(Int.MAX_VALUE) { shortest, note -> minOf(shortest, note.lengthTicks) },
                    ),
                lengthTicks = notes.longest(),
            )
        }
    }

/** Hands out beam numbers across both staves. */
private class BeamNumbers {
    private var next = 0

    fun take(): Int = next++
}

private fun finish(
    staff: Staff,
    written: List<Written>,
    key: KeySignature,
    barTicks: Int,
    groups: BeamNumbers,
): List<EngravedChord> {
    val beamOf = HashMap<Int, Int>()
    val stemOf = HashMap<Int, StemDirection>()
    beamRuns(written, barTicks).forEach { run ->
        val number = groups.take()
        val direction = stemFor(staff, run.flatMap { written[it].pitches })
        run.forEach { index ->
            beamOf[index] = number
            stemOf[index] = direction
        }
    }
    val bars = AccidentalState(key, barTicks)
    return written.mapIndexed { index, chord ->
        val lone = index !in beamOf
        val stem =
            stemOf[index]
                ?: if (chord.value == NoteValue.WHOLE) StemDirection.NONE else stemFor(staff, chord.pitches)
        EngravedChord(
            stepIndex = chord.stepIndex,
            staff = staff,
            onsetTicks = chord.onsetTicks,
            value = chord.value,
            notes = chord.pitches.map { EngravedNote(it, bars.accidentalFor(it, chord.onsetTicks)) },
            stem = stem,
            flag = lone && chord.value == NoteValue.EIGHTH,
            beamGroup = beamOf[index],
        )
    }
}

/** Index runs (two or more) of consecutive eighths with no rest between them, all inside one beat. */
private fun beamRuns(
    written: List<Written>,
    barTicks: Int,
): List<List<Int>> {
    val runs = ArrayList<List<Int>>()
    var run = ArrayList<Int>()

    fun close() {
        if (run.size >= 2) runs += run
        run = ArrayList()
    }
    written.forEachIndexed { index, chord ->
        val previous = run.lastOrNull()?.let { written[it] }
        val continues =
            chord.value == NoteValue.EIGHTH &&
                previous != null &&
                previous.onsetTicks + previous.value.ticks == chord.onsetTicks &&
                beatOf(previous.onsetTicks, barTicks) == beatOf(chord.onsetTicks, barTicks)
        if (!continues) close()
        if (chord.value == NoteValue.EIGHTH) run += index
    }
    close()
    return runs
}

/** The beat (a quarter's span from the bar start) a tick falls in, as (bar, beat in the bar). */
private fun beatOf(
    tick: Int,
    barTicks: Int,
): Pair<Int, Int> = tick / barTicks to tick % barTicks / TICKS_PER_QUARTER

/** Up when the note farthest from the middle line is below it, down when it is on or above it. */
private fun stemFor(
    staff: Staff,
    pitches: List<MidiNote>,
): StemDirection {
    val middle = if (staff == Staff.TREBLE) TREBLE_MIDDLE else BASS_MIDDLE
    val farthest = pitches.map { diatonic(it) - middle }.maxWith(compareBy({ abs(it) }, { it }))
    return if (farthest < 0) StemDirection.UP else StemDirection.DOWN
}

/** Tracks which accidental each letter and octave carries in the current bar of one staff. */
private class AccidentalState(
    private val key: KeySignature,
    private val barTicks: Int,
) {
    private var bar = -1
    private val state = HashMap<Int, Int>()

    /** The sign [pitch] needs at [onset] (null when the key or an earlier sign in the bar already says it). */
    fun accidentalFor(
        pitch: MidiNote,
        onset: Int,
    ): Accidental? {
        if (onset / barTicks != bar) {
            bar = onset / barTicks
            state.clear()
        }
        val slot = diatonic(pitch)
        val letter = slot % LETTERS
        val written = if (pitch.number % SEMITONES in BLACK_PITCH_CLASSES) 1 else 0
        val current = state[slot] ?: if (letter in key.alteredLetters) key.sharps.coerceIn(-1, 1) else 0
        state[slot] = written
        return when {
            written == current -> null
            written == 1 -> Accidental.SHARP
            else -> Accidental.NATURAL
        }
    }
}

/** Rests of [staff] between its chords (and to the passage end), cut at bar lines and aligned within the bar. */
private fun restsOn(
    staff: Staff,
    written: List<Written>,
    barTicks: Int,
    endTicks: Int,
): List<EngravedRest> {
    val rests = ArrayList<EngravedRest>()
    var cursor = 0
    written.forEach { chord ->
        if (chord.onsetTicks > cursor) fillGap(staff, cursor, chord.onsetTicks, barTicks, rests)
        cursor = maxOf(cursor, chord.onsetTicks + chord.lengthTicks)
    }
    if (endTicks > cursor) fillGap(staff, cursor, endTicks, barTicks, rests)
    return rests
}

private fun fillGap(
    staff: Staff,
    from: Int,
    to: Int,
    barTicks: Int,
    into: MutableList<EngravedRest>,
) {
    for (bar in (from / barTicks..(to - 1) / barTicks).toList()) {
        val barStart = bar * barTicks
        val start = maxOf(from, barStart)
        val end = minOf(to, barStart + barTicks)
        if (start == barStart && end == barStart + barTicks) {
            into += EngravedRest(staff, start, NoteValue.WHOLE)
            continue
        }
        var position = start
        while (position < end) {
            val value =
                REST_VALUES.firstOrNull { (position - barStart) % it.ticks == 0 && position + it.ticks <= end }
                    ?: break
            into += EngravedRest(staff, position, value)
            position += value.ticks
        }
    }
}

/** The rest values a gap is filled with, longest first. */
private val REST_VALUES = listOf(NoteValue.HALF, NoteValue.QUARTER, NoteValue.EIGHTH)

private const val MIDDLE_C = 60
private const val SEMITONES = 12
private const val LETTERS = 7
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)

/** Letter index (C = 0 … B = 6) of each pitch class; a sharp shares its natural's letter. */
private val LETTER_OF_PITCH_CLASS = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)

/** Diatonic slot of a note: octave × 7 + letter, so neighbours on the staff differ by one. */
private fun diatonic(note: MidiNote): Int =
    note.number / SEMITONES * LETTERS + LETTER_OF_PITCH_CLASS[note.number % SEMITONES]

/** B4, the treble staff's middle line. */
private val TREBLE_MIDDLE = diatonic(MidiNote(71))

/** D3, the bass staff's middle line. */
private val BASS_MIDDLE = diatonic(MidiNote(50))
