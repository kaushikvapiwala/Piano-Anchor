package pianoanchor.engine.notation

import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.TICKS_PER_QUARTER

/** The two staves of the grand staff. */
public enum class Staff { TREBLE, BASS }

private const val QUARTERS_PER_WHOLE = 4
private const val QUARTERS_PER_DOTTED_HALF = 3

/** A written note length: its [ticks] and whether it carries an augmentation dot. */
public enum class NoteValue(
    public val ticks: Int,
    public val dotted: Boolean,
) {
    WHOLE(QUARTERS_PER_WHOLE * TICKS_PER_QUARTER, false),
    DOTTED_HALF(QUARTERS_PER_DOTTED_HALF * TICKS_PER_QUARTER, true),
    HALF(2 * TICKS_PER_QUARTER, false),
    DOTTED_QUARTER(TICKS_PER_QUARTER + TICKS_PER_QUARTER / 2, true),
    QUARTER(TICKS_PER_QUARTER, false),
    EIGHTH(TICKS_PER_QUARTER / 2, false),
}

/** A sign drawn before a notehead. */
public enum class Accidental { SHARP, NATURAL }

/** Which way a stem points; [NONE] for a whole note. */
public enum class StemDirection { UP, DOWN, NONE }

/** One notehead of a chord and the [accidental] it needs (null = none). */
public data class EngravedNote(
    val pitch: MidiNote,
    val accidental: Accidental?,
)

/**
 * The notes of [stepIndex] that sit on [staff], low to high, as one symbol. [flag] is set on a lone eighth; the
 * chords with the same [beamGroup] (not null) share one beam.
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

/** A passage ready to draw: [chords] (by step, treble first), [rests] (by onset) and the [key]. */
public data class Engraved(
    val chords: List<EngravedChord>,
    val rests: List<EngravedRest>,
    val key: KeySignature,
)

private const val SEMITONES_PER_OCTAVE = 12
private const val LETTERS_PER_OCTAVE = 7

/** Letter index (C = 0 … B = 6) of each pitch class; a sharp shares its natural's letter. */
private val LETTER_OF_PITCH_CLASS = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)

/** Notes below middle C (C4 = 60) go on the bass staff, the rest on the treble staff. */
private const val FIRST_TREBLE_NOTE = 60

/** The middle lines: B4 on the treble staff, D3 on the bass staff. */
private const val TREBLE_MIDDLE_NOTE = 71
private const val BASS_MIDDLE_NOTE = 50

/** Rest values a gap is filled with, longest first. */
private val REST_VALUES = listOf(NoteValue.HALF, NoteValue.QUARTER, NoteValue.EIGHTH)

private val NoteValue.isEighth get() = this == NoteValue.EIGHTH

private fun Collection<ScoreNote>.longest() = fold(0) { longest, note -> maxOf(longest, note.lengthTicks) }

private fun diatonic(note: MidiNote): Int =
    note.number / SEMITONES_PER_OCTAVE * LETTERS_PER_OCTAVE + LETTER_OF_PITCH_CLASS[note.number % SEMITONES_PER_OCTAVE]

private fun staffOf(note: MidiNote) = if (note.number < FIRST_TREBLE_NOTE) Staff.BASS else Staff.TREBLE

private fun Staff.middle() = diatonic(MidiNote(if (this == Staff.TREBLE) TREBLE_MIDDLE_NOTE else BASS_MIDDLE_NOTE))

/** One staff's chord before stems, beams and accidentals; [endTicks] is where its longest note stops sounding. */
private class Draft(
    val stepIndex: Int,
    val onsetTicks: Int,
    val value: NoteValue,
    val pitches: List<MidiNote>,
    val endTicks: Int,
)

/**
 * Turns a passage's steps into the symbols a staff draws: values, rests, stems, beams, dots and accidentals. The
 * rules are in `docs/algorithms/engraving.md`.
 */
public object Engraving {
    /** The [Engraved] form of [passage]. */
    public fun engrave(passage: Passage): Engraved {
        val barTicks = passage.timeSignature.barTicks
        val longest = passage.steps.fold(0) { end, step -> maxOf(end, step.onsetTicks + step.notes.longest()) }
        val end = (longest + barTicks - 1) / barTicks * barTicks
        val chords = ArrayList<EngravedChord>()
        val rests = ArrayList<EngravedRest>()
        for (staff in Staff.entries) {
            val drafts = drafts(passage, staff)
            chords += chords(drafts, staff, passage, chords.mapNotNull { it.beamGroup }.maxOrNull()?.plus(1) ?: 0)
            rests += rests(drafts, staff, end, barTicks)
        }
        return Engraved(
            chords.sortedWith(compareBy({ it.stepIndex }, { it.staff })),
            rests.sortedWith(compareBy({ it.onsetTicks }, { it.staff })),
            passage.keySignature,
        )
    }

    private fun drafts(
        passage: Passage,
        staff: Staff,
    ): List<Draft> =
        passage.steps.mapIndexedNotNull { index, step ->
            val notes = step.notes.filter { staffOf(it.pitch) == staff }
            if (notes.isEmpty()) {
                null
            } else {
                Draft(
                    index,
                    step.onsetTicks,
                    valueOf(notes.fold(Int.MAX_VALUE) { shortest, note -> minOf(shortest, note.lengthTicks) }),
                    notes.map { it.pitch }.sortedBy { it.number },
                    step.onsetTicks + notes.longest(),
                )
            }
        }

    /** The value of exactly [length] ticks, else the longest that fits (the shortest if none does). */
    private fun valueOf(length: Int): NoteValue =
        NoteValue.entries.sortedByDescending { it.ticks }.firstOrNull { it.ticks <= length }
            ?: NoteValue.EIGHTH

    private fun chords(
        drafts: List<Draft>,
        staff: Staff,
        passage: Passage,
        firstGroup: Int,
    ): List<EngravedChord> {
        val barTicks = passage.timeSignature.barTicks
        val groups = beamGroups(drafts, barTicks, firstGroup)
        val groupPitches =
            drafts.indices.groupBy({ groups[it] }, { drafts[it].pitches }).mapValues { it.value.flatten() }
        val accidentals = AccidentalTracker(passage.keySignature, barTicks)
        return drafts.mapIndexed { i, draft ->
            val group = groups[i]
            val stem =
                if (draft.value == NoteValue.WHOLE) {
                    StemDirection.NONE
                } else {
                    stemFor(if (group == null) draft.pitches else groupPitches.getValue(group), staff)
                }
            EngravedChord(
                stepIndex = draft.stepIndex,
                staff = staff,
                onsetTicks = draft.onsetTicks,
                value = draft.value,
                notes = draft.pitches.map { EngravedNote(it, accidentals.next(it, draft.onsetTicks)) },
                stem = stem,
                flag = draft.value.isEighth && group == null,
                beamGroup = group,
            )
        }
    }

    /** Up when the note farthest from the middle line is below it, down when it is on or above it. */
    private fun stemFor(
        pitches: List<MidiNote>,
        staff: Staff,
    ): StemDirection {
        val farthest = pitches.map { diatonic(it) - staff.middle() }.maxBy { kotlin.math.abs(it) }
        return if (farthest < 0) StemDirection.UP else StemDirection.DOWN
    }

    /** Per draft: the id of the beam its eighth belongs to, or null (not an eighth, or alone in its beat). */
    private fun beamGroups(
        drafts: List<Draft>,
        barTicks: Int,
        firstGroup: Int,
    ): List<Int?> {
        fun beat(draft: Draft) = draft.onsetTicks / barTicks to draft.onsetTicks % barTicks / TICKS_PER_QUARTER
        var group = firstGroup - 1
        val ids =
            drafts.mapIndexed { i, draft ->
                val before = drafts.getOrNull(i - 1)
                if (!draft.value.isEighth) {
                    null
                } else {
                    if (before == null || !before.value.isEighth || beat(before) != beat(draft)) group++
                    group
                }
            }
        val sizes = ids.filterNotNull().groupingBy { it }.eachCount()
        return ids.map { id -> id?.takeIf { sizes.getValue(it) > 1 } }
    }

    private fun rests(
        drafts: List<Draft>,
        staff: Staff,
        end: Int,
        barTicks: Int,
    ): List<EngravedRest> {
        if (drafts.isEmpty()) return emptyList()
        val rests = ArrayList<EngravedRest>()
        var cursor = 0
        for (draft in drafts) {
            if (draft.onsetTicks > cursor) rests += fillGap(staff, cursor, draft.onsetTicks, barTicks)
            cursor = maxOf(cursor, draft.endTicks)
        }
        if (end > cursor) rests += fillGap(staff, cursor, end, barTicks)
        return rests
    }

    /** Rests from [from] to [to], cut at bar lines: a whole bar is a whole rest, else aligned half/quarter/eighth. */
    private fun fillGap(
        staff: Staff,
        from: Int,
        to: Int,
        barTicks: Int,
    ): List<EngravedRest> {
        val rests = ArrayList<EngravedRest>()
        var at = from
        while (at < to) {
            val barStart = at / barTicks * barTicks
            val segmentEnd = minOf(to, barStart + barTicks)
            if (at == barStart && segmentEnd == barStart + barTicks) {
                rests += EngravedRest(staff, at, NoteValue.WHOLE)
            } else {
                var q = at
                while (q < segmentEnd) {
                    val value =
                        REST_VALUES.firstOrNull { (q - barStart) % it.ticks == 0 && q + it.ticks <= segmentEnd }
                            ?: break
                    rests += EngravedRest(staff, q, value)
                    q += value.ticks
                }
            }
            at = segmentEnd
        }
        return rests
    }
}

/** Remembers, per letter and octave, the accidental in force in the current bar of one staff. */
private class AccidentalTracker(
    key: KeySignature,
    private val barTicks: Int,
) {
    private val keyRaised = if (key.sharps > 0) key.alteredLetters.toSet() else emptySet()
    private val raised = HashMap<Pair<Int, Int>, Boolean>()
    private var bar = 0

    /** The sign [note] (at [onsetTicks]) needs, remembering it to the end of the bar. */
    fun next(
        note: MidiNote,
        onsetTicks: Int,
    ): Accidental? {
        if (onsetTicks / barTicks != bar) {
            bar = onsetTicks / barTicks
            raised.clear()
        }
        val letter = LETTER_OF_PITCH_CLASS[note.number % SEMITONES_PER_OCTAVE]
        val spot = letter to note.number / SEMITONES_PER_OCTAVE
        val sharp = note.number % SEMITONES_PER_OCTAVE in BLACK_PITCH_CLASSES
        if (sharp == (raised[spot] ?: (letter in keyRaised))) return null
        raised[spot] = sharp
        return if (sharp) Accidental.SHARP else Accidental.NATURAL
    }
}
