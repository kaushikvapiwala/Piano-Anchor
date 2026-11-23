package pianoanchor.engine.guidance

private const val SEMITONES_PER_OCTAVE = 12

/** Where a key sits against the piano's groups of two and three black keys (`docs/algorithms/wrong-key-hint.md`). */
public enum class Landmark {
    /** C: the white key just left of the group of two black keys. */
    LEFT_OF_TWO,

    /** D: the white key between the two black keys. */
    BETWEEN_TWO,

    /** E: the white key just right of the group of two. */
    RIGHT_OF_TWO,

    /** F: the white key just left of the group of three black keys. */
    LEFT_OF_THREE,

    /** G: between the first and middle black key of the three. */
    IN_THREE_LOW,

    /** A: between the middle and last black key of the three. */
    IN_THREE_HIGH,

    /** B: the white key just right of the group of three. */
    RIGHT_OF_THREE,

    /** Any black key: found as the one just above the white key below it. */
    BLACK_ABOVE,
}

/** Which way the expected key lies from the key that was played. */
public enum class Direction {
    /** The expected key is lower (to the left) than the played one. */
    LEFT,

    /** The expected key is higher (to the right) than the played one. */
    RIGHT,

    /** The same letter, in another octave. */
    SAME_KEY_OTHER_OCTAVE,
}

/** How to find [expected] (MIDI note) from its black-key group, and which way it lies from [played]. */
public data class WrongKeyHint(
    val played: Int,
    val expected: Int,
    val landmark: Landmark,
    val direction: Direction,
)

/** The landmark of each pitch class, C first. */
private val LANDMARKS =
    listOf(
        Landmark.LEFT_OF_TWO,
        Landmark.BLACK_ABOVE,
        Landmark.BETWEEN_TWO,
        Landmark.BLACK_ABOVE,
        Landmark.RIGHT_OF_TWO,
        Landmark.LEFT_OF_THREE,
        Landmark.BLACK_ABOVE,
        Landmark.IN_THREE_LOW,
        Landmark.BLACK_ABOVE,
        Landmark.IN_THREE_HIGH,
        Landmark.BLACK_ABOVE,
        Landmark.RIGHT_OF_THREE,
    )

/**
 * The hint for a wrong key: the landmark that finds [expected] and the direction from [played]. [played] and
 * [expected] differ (a right key needs no hint).
 */
public fun wrongKeyHint(
    played: Int,
    expected: Int,
): WrongKeyHint {
    require(played != expected) { "a hint needs two different notes, got $played twice" }
    val direction =
        when {
            (expected - played) % SEMITONES_PER_OCTAVE == 0 -> Direction.SAME_KEY_OTHER_OCTAVE
            expected < played -> Direction.LEFT
            else -> Direction.RIGHT
        }
    return WrongKeyHint(played, expected, LANDMARKS[expected % SEMITONES_PER_OCTAVE], direction)
}
