package pianoanchor.engine.guidance

import pianoanchor.core.model.BarRange
import pianoanchor.core.model.Passage

/** The 1-based bar the step at [stepIndex] starts in. */
internal fun Passage.barOf(stepIndex: Int): Int = steps[stepIndex].onsetTicks / timeSignature.barTicks + 1

internal fun Passage.barCount(): Int = barOf(steps.lastIndex)

/**
 * The steps of this passage inside [range], rebased so the first bar starts at tick 0. The slice keeps the time
 * signature, tempo, rights, title and skills, but not the chord line (the backing plays with whole passages only);
 * its id gets a `#bars<first>-<last>` suffix to tell it apart in memory
 * (attempts are saved under the piece's id). Throws [IllegalArgumentException] when no step is in [range].
 */
public fun Passage.slice(range: BarRange): Passage {
    val inside = steps.filterIndexed { i, _ -> barOf(i) in range.first..range.last }
    require(inside.isNotEmpty()) { "passage \"$id\" has no step in bars ${range.first}-${range.last}" }
    val offset = (range.first - 1) * timeSignature.barTicks
    return copy(
        id = "$id$BARS_MARKER${range.first}-${range.last}",
        steps =
            inside.map { it.copy(onsetTicks = it.onsetTicks - offset) },
        chords = emptyList(),
    )
}

private const val BARS_MARKER = "#bars"
