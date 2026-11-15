package pianoanchor.core.model

/**
 * One step of the curriculum: a titled group of [skills] (ids of [Skill]s) taught through its [passages].
 * [id] is stable and unique among units.
 */
public data class CurriculumUnit(
    val id: String,
    val title: String,
    val skills: List<String>,
    val passages: List<Passage>,
)
