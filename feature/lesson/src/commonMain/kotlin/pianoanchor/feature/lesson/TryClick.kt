package pianoanchor.feature.lesson

import kotlinx.coroutines.delay
import pianoanchor.core.model.TryCheck
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.engine.matcher.beatNanos

private const val NANOS_PER_MILLI = 1_000_000L
private const val BEATS_PER_BAR = 4

/** The click tempo of [check], or null for a Try without a click. */
internal fun clickOf(check: TryCheck): Int? =
    when (check) {
        is TryCheck.Repeat -> check.bpm
        is TryCheck.Hold -> check.bpm
        is TryCheck.StressBeatOne -> check.bpm
        is TryCheck.Rhythm -> check.bpm
        else -> null
    }

/**
 * Starts [metronome] at [bpm] and counts one bar in ("3, 2, 1", each reported to [onNumeral], then null). [onAccepting]
 * runs half a beat before the first beat: a first beat played a little early still counts. The caller stops the click.
 */
internal suspend fun countIn(
    metronome: MetronomePort,
    time: ClockPort,
    bpm: Int,
    onNumeral: (Int?) -> Unit,
    onAccepting: () -> Unit,
) {
    val beat = beatNanos(bpm)
    metronome.start(time.nowNanos() + beat, beat, BEATS_PER_BAR)
    val beatMs = beat / NANOS_PER_MILLI
    // The count-in is one bar: the click starts a beat from now, then four clicks, the last three numbered.
    delay(beatMs)
    for (i in 1..BEATS_PER_BAR) {
        val numeral = BEATS_PER_BAR + 1 - i
        onNumeral(numeral.takeIf { n -> n < BEATS_PER_BAR })
        if (i < BEATS_PER_BAR) {
            delay(beatMs)
        } else {
            // A first beat played a little early still counts: notes count from half a beat before it.
            delay(beatMs - beatMs / 2)
            onAccepting()
            delay(beatMs / 2)
        }
    }
    onNumeral(null)
}
