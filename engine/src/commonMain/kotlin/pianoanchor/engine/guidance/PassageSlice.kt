package pianoanchor.engine.guidance

import pianoanchor.core.model.BarRange
import pianoanchor.core.model.Passage

/** The 1-based bar the step at [stepIndex] starts in. */
internal fun Passage.barOf(stepIndex: Int): Int = steps[stepIndex].onsetTicks / timeSignature.barTicks + 1

internal fun Passage.barCount(): Int = barOf(steps.lastIndex)

/**
 * The steps of this passage inside [range], rebased so the first bar starts at tick 0. The slice keeps the time
 * signature, tempo, rights, title and skills; its id gets a `#bars<first>-<last>` suffix so attempts stay
 * attributable. Throws [IllegalArgumentException] when no step is in [range].
 */
public fun Passage.slice(range: BarRange): Passage {
    val barTicks = timeSignature.barTicks
    val offset = (range.first - 1) * barTicks
    val inside = steps.filter { it.onsetTicks / barTicks + 1 in range.first..range.last }
    require(inside.isNotEmpty()) { "passage \"$id\" has no step in bars ${range.first}-${range.last}" }
    return copy(
        id = "$id#bars${range.first}-${range.last}",
        steps =
            inside.map {
                it.copy(
                    onsetTicks =
                        it.onsetTicks - offset,
                )
            },
    )
}
