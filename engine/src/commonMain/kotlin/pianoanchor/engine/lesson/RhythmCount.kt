package pianoanchor.engine.lesson

import pianoanchor.core.model.TICKS_PER_QUARTER

private const val BEATS_PER_BAR = 4
private const val EIGHTH_TICKS = TICKS_PER_QUARTER / 2
private const val EIGHTHS_PER_BEAT = 2
private const val AND = "&"

/**
 * The count a rhythm of [lengths] (ticks, 4/4) is played to, as text: a number for a tap on a beat, "&" for a tap
 * halfway, and the number in brackets for a beat it is held through, so eighths read "1 & 2 &" and a dotted quarter
 * with an eighth reads "1 (2) &". The last tap's own length is not counted. Pure. Throws [IllegalArgumentException]
 * unless [lengths] is non-empty and every length is a whole number of eighths.
 */
public fun rhythmCount(lengths: List<Int>): String {
    require(lengths.isNotEmpty() && lengths.all { it > 0 && it % EIGHTH_TICKS == 0 }) {
        "a rhythm count needs whole eighths: $lengths"
    }
    val taps =
        lengths
            .runningFold(0) { at, length -> at + length }
            .dropLast(1)
            .map { it / EIGHTH_TICKS }
            .toSet()
    return (0..taps.max())
        .mapNotNull { eighth ->
            val onBeat = eighth % EIGHTHS_PER_BEAT == 0
            val word = if (onBeat) (eighth / EIGHTHS_PER_BEAT % BEATS_PER_BAR + 1).toString() else AND
            when {
                eighth in taps -> word
                onBeat -> "($word)"
                else -> null
            }
        }.joinToString(" ")
}
