package pianoanchor.core.model

private val KEBAB_ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")

/** One learnable skill. [id] is stable (reviews are stored against it), lowercase kebab-case, not blank. */
public data class Skill(
    val id: String,
    val title: String,
    val prerequisites: Set<String>,
    val track: Track = Track.CORE,
)

/** Skills with prerequisites that form a DAG: unique ids, every prerequisite known, no cycles. */
public class SkillGraph(
    skills: List<Skill>,
) {
    private val byId: Map<String, Skill> = index(skills)

    /** All skills in a topological order (prerequisites first), ties broken by id. */
    public val skills: List<Skill> = order(byId)

    /** The skill with [id], or null. */
    public fun byId(id: String): Skill? = byId[id]

    /** Skills whose prerequisites are all in [learned] and that are not themselves in [learned]. */
    public fun unlocked(learned: Set<String>): List<Skill> =
        skills.filter { it.id !in learned && learned.containsAll(it.prerequisites) }
}

private fun index(skills: List<Skill>): Map<String, Skill> {
    val byId = LinkedHashMap<String, Skill>()
    for (skill in skills) {
        require(KEBAB_ID.matches(skill.id)) { "skill id \"${skill.id}\" must be lowercase kebab-case and not blank" }
        require(byId.put(skill.id, skill) == null) { "skill \"${skill.id}\" is defined twice" }
    }
    for (skill in skills) {
        require(skill.id !in skill.prerequisites) { "skill \"${skill.id}\" is its own prerequisite" }
        skill.prerequisites.forEach {
            require(it in byId) { "skill \"${skill.id}\" has unknown prerequisite \"$it\"" }
        }
    }
    return byId
}

/** Kahn's algorithm, always taking the smallest id among the ready skills. */
private fun order(byId: Map<String, Skill>): List<Skill> {
    val waiting = byId.mapValues { it.value.prerequisites.toMutableSet() }.toMutableMap()
    val result = ArrayList<Skill>(byId.size)
    while (waiting.isNotEmpty()) {
        val next = waiting.filterValues { it.isEmpty() }.keys.minOrNull()
        require(next != null) { "skills form a cycle: ${findCycle(byId, waiting.keys)}" }
        waiting.remove(next)
        waiting.values.forEach { it.remove(next) }
        result += byId.getValue(next)
    }
    return result
}

/** A cycle among the [stuck] skills, as `a -> b -> a` where each skill needs the next. */
private fun findCycle(
    byId: Map<String, Skill>,
    stuck: Set<String>,
): String {
    val path = ArrayList<String>()
    var current = stuck.min()
    while (current !in path) {
        path += current
        current =
            byId
                .getValue(current)
                .prerequisites
                .filter { it in stuck }
                .min()
    }
    return (path.subList(path.indexOf(current), path.size) + current).joinToString(" -> ")
}
