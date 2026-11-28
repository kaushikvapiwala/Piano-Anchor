package pianoanchor.engine.notation

import pianoanchor.core.model.TimeSignature

/** Beats per bar that make a metre with an eighth beat compound: 6/8, 9/8 and 12/8 (3/8 stays simple). */
private val COMPOUND_BEATS = setOf(6, 9, 12)
private const val EIGHTH_BEAT_UNIT = 8

internal fun isCompound(time: TimeSignature): Boolean =
    time.beatUnit == EIGHTH_BEAT_UNIT && time.beats in COMPOUND_BEATS

/** The length of a beat for beaming and rests: a dotted quarter in compound metres, else a quarter. */
internal fun beatTicks(time: TimeSignature): Int =
    if (isCompound(time)) NoteValue.DOTTED_QUARTER.ticks else NoteValue.QUARTER.ticks

/** Rests of [staff] between its chords (and to the passage end), cut at bar lines and aligned within the bar. */
internal fun restsOn(
    staff: Staff,
    written: List<Written>,
    barTicks: Int,
    time: TimeSignature,
    endTicks: Int,
): List<EngravedRest> {
    val rests = ArrayList<EngravedRest>()
    var cursor = 0
    written.forEach { chord ->
        if (chord.onsetTicks > cursor) fillGap(staff, cursor, chord.onsetTicks, barTicks, time, rests)
        cursor = maxOf(cursor, chord.onsetTicks + chord.lengthTicks)
    }
    if (endTicks > cursor) fillGap(staff, cursor, endTicks, barTicks, time, rests)
    return rests
}

private fun fillGap(
    staff: Staff,
    from: Int,
    to: Int,
    barTicks: Int,
    time: TimeSignature,
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
                REST_VALUES.firstOrNull { fits(it, position - barStart, end - barStart, time) } ?: break
            into += EngravedRest(staff, position, value)
            position += value.ticks
        }
    }
}

/**
 * The rest values a gap is filled with, longest first. A half rest never fits in a compound beat and a dotted quarter
 * never fits in a simple one, so one list serves both.
 */
private val REST_VALUES = listOf(NoteValue.HALF, NoteValue.DOTTED_QUARTER, NoteValue.QUARTER, NoteValue.EIGHTH)

/**
 * Whether a rest of [value] may start [offset] ticks into its bar and end by [end]: aligned to its own length, and
 * (in compound metres only) inside one beat of [time]. A simple-metre beat (a quarter) is not a limit: a half rest
 * spans two of them.
 */
private fun fits(
    value: NoteValue,
    offset: Int,
    end: Int,
    time: TimeSignature,
): Boolean {
    val compound = isCompound(time)
    val beatTicks = beatTicks(time)
    return when {
        offset + value.ticks > end -> false
        compound -> offset % beatTicks % value.ticks == 0 && offset % beatTicks + value.ticks <= beatTicks
        else -> !value.dotted && offset % value.ticks == 0
    }
}
