package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.Passage
import pianoanchor.core.model.SkillGraph

/** The built-in content: units in teaching order, the skills they teach and every passage. */
public object Curriculum {
    /** The units in teaching order: 0 "Find your way", 1 "Five-finger tunes", 2 "Both hands". */
    public val units: List<CurriculumUnit> = listOf(UNIT_0, UNIT_1, UNIT_2)

    /** Every skill, with its prerequisites: those the units teach, then the ear skills (no passage, no unit). */
    public val graph: SkillGraph =
        SkillGraph(
            listOf(
                Skills.KB_BLACK_GROUPS,
                Skills.KB_FIND_C,
                Skills.KB_MIDDLE_C,
                Skills.RH_C_POSITION,
                Skills.RHYTHM_QUARTER,
                Skills.RH_STEPS,
                Skills.RH_SKIPS,
                Skills.RHYTHM_HALF,
                Skills.RH_TUNES,
                Skills.TEMPO_STEADY,
                Skills.LH_C_POSITION,
                Skills.LH_STEPS,
                Skills.RHYTHM_WHOLE,
                Skills.HANDS_TURNS,
                Skills.TIME_THREE_FOUR,
                Skills.HANDS_HELD_BASS,
            ) + EarSkills.all,
        )

    /** The passage with [id], or null if there is none (e.g. an attempt of a passage since removed). */
    public fun passage(id: String): Passage? = units.flatMap { it.passages }.firstOrNull { it.id == id }
}
