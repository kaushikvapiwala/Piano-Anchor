package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.Passage
import pianoanchor.core.model.SkillGraph

/** The built-in content: units in teaching order, the skills they teach and every passage. */
public object Curriculum {
    /** The units in teaching order. Unit 0 holds the C-D-E placeholder until M3.2. */
    public val units: List<CurriculumUnit> =
        listOf(
            CurriculumUnit(
                id = "unit-0",
                title = "First notes",
                skills = listOf(Skills.RH_C_POSITION.id),
                passages = listOf(Passages.CDE),
            ),
        )

    /** Every skill, with its prerequisites. */
    public val graph: SkillGraph = SkillGraph(listOf(Skills.RH_C_POSITION))

    /** The passage with [id], or null if there is none (e.g. an attempt of a passage since removed). */
    public fun passage(id: String): Passage? = units.flatMap { it.passages }.firstOrNull { it.id == id }
}
