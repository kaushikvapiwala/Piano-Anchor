package pianoanchor.core.model

/** A range of bars of a passage, 1-based and inclusive: [first] <= [last], [first] >= 1. */
public data class BarRange(
    val first: Int,
    val last: Int,
) {
    init {
        require(first >= 1) { "bar range must start at bar 1 or later, was $first" }
        require(first <= last) { "bar range must not end before it starts, was $first..$last" }
    }

    /** The number of bars in the range. */
    val bars: Int get() = last - first + 1
}
