package pianoanchor.engine.content

import pianoanchor.core.model.ConceptCard
import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.Passage
import pianoanchor.core.model.SkillGraph

/** The built-in content: units in teaching order, the skills they teach and every passage. */
public object Curriculum {
    /**
     * The units in teaching order: 0 "Meet the piano", 1 "Five-finger tunes", 2 "Left hand, then both hands",
     * 3 "Chords and songs", then the optional "More songs".
     */
    public val units: List<CurriculumUnit> = listOf(UNIT_0, UNIT_1, UNIT_2, UNIT_3, MORE_SONGS_UNIT)

    /** Every passage of every unit, in teaching order. */
    public val passages: List<Passage> = units.flatMap { it.passages }

    /**
     * Every skill, with its prerequisites: the 23 course skills in map order, the "More songs" skills, then the ear
     * skills (no passage, no unit).
     */
    public val graph: SkillGraph =
        SkillGraph(
            listOf(
                Skills.HAND_SHAPE,
                Skills.MIDDLE_C,
                Skills.BLACK_KEY_GROUPS,
                Skills.HIGH_AND_LOW,
                Skills.STEADY_BEAT,
                Skills.QUARTER_AND_HALF,
                Skills.WHITE_KEY_NAMES,
                Skills.RH_C_POSITION,
                Skills.STEPS_AND_SKIPS,
                Skills.FINGER_NUMBER_TUNES,
                Skills.BARS_AND_COUNTING,
                Skills.STAFF_STEPS,
                Skills.TREBLE_CLEF,
                Skills.SOFT_AND_LOUD,
                Skills.LH_C_POSITION,
                Skills.BASS_CLEF_WHOLE_NOTE,
                Skills.GRAND_STAFF_TURNS_RESTS,
                Skills.THREE_FOUR_TIME,
                Skills.HANDS_TOGETHER_HELD,
                Skills.C_CHORD,
                Skills.G_AND_F_CHORDS,
                Skills.MELODY_AND_CHORDS,
                Skills.SHARPS_AND_FLATS,
                Skills.MORE_INVERSIONS,
                Skills.MORE_A_MINOR,
                Skills.MORE_FOUR_CHORDS,
            ) + EarSkills.all,
        )

    /** The passage with [id], or null if there is none (e.g. an attempt of a passage since removed). */
    public fun passage(id: String): Passage? = passages.firstOrNull { it.id == id }

    /** The concept card of [skillId], or null for a skill without one (More songs, ear skills). */
    public fun card(skillId: String): ConceptCard? = ConceptCards.all.firstOrNull { it.skillId == skillId }
}
