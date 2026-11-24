package pianoanchor.engine.sight

import pianoanchor.engine.content.Skills

/** One staircase level: [number] 1..8, the [skill] that opens it, the hands and material it uses. */
public data class SightLevel(
    val number: Int,
    val skill: String,
    val description: String,
)

/** The eight sight-reading levels (`docs/algorithms/sight-reading.md`). */
public object SightLevels {
    /** The 8 levels, in order. */
    public val all: List<SightLevel> =
        listOf(
            SightLevel(1, Skills.STAFF_STEPS.id, "Right hand, C to G, steps, quarters and halves"),
            SightLevel(2, Skills.TREBLE_CLEF.id, "Right hand, adds skips"),
            SightLevel(3, Skills.GRAND_STAFF_TURNS_RESTS.id, "Adds whole notes and quarter rests"),
            SightLevel(4, Skills.BASS_CLEF_WHOLE_NOTE.id, "Left hand alone, C to G below middle C"),
            SightLevel(5, Skills.GRAND_STAFF_TURNS_RESTS.id, "Hands taking turns, one bar each"),
            SightLevel(6, Skills.THREE_FOUR_TIME.id, "Hands taking turns in 3/4, with dotted halves"),
            SightLevel(7, Skills.HANDS_TOGETHER_HELD.id, "Right-hand tune over a held left-hand note"),
            SightLevel(8, Skills.MELODY_AND_CHORDS.id, "Right-hand tune over left-hand C, F and G chords"),
        )

    /** The highest level n such that levels 1..n all have their skill in [learned]; 0 when level 1 is closed. */
    public fun open(learned: Set<String>): Int = all.takeWhile { it.skill in learned }.size
}
