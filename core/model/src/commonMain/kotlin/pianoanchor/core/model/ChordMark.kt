package pianoanchor.core.model

private val PITCH_CLASSES = 0..11

/** The kinds of chord a chord line uses: a major or minor triad, or a major triad with a minor seventh. */
public enum class ChordQuality { MAJOR, MINOR, DOMINANT_7 }

/**
 * One chord of a chord line: from [onsetTicks] (>= 0) until the next chord, a chord on [root] (pitch class 0..11,
 * C = 0) of [quality].
 */
public data class ChordMark(
    val onsetTicks: Int,
    val root: Int,
    val quality: ChordQuality,
) {
    init {
        require(onsetTicks >= 0) { "chord at $onsetTicks ticks must not have a negative onset" }
        require(root in PITCH_CLASSES) { "chord root must be a pitch class in $PITCH_CLASSES, was $root" }
    }
}
