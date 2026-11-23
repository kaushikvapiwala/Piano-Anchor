package pianoanchor.engine.lesson

import pianoanchor.core.model.Skill
import pianoanchor.core.model.Track
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.session.PassageHistory

/**
 * Which concepts of the beginner course the learner has passed and which is suggested next
 * (`docs/course/beginner-course.md`, "Nothing locked"). Pure.
 */
public object CourseProgress {
    /** The course skills of [curriculum] in map order: the skills of its units on the core track. */
    public fun skills(curriculum: Curriculum): List<Skill> {
        val core =
            curriculum.graph.skills
                .filter { it.track == Track.CORE }
                .associateBy { it.id }
        return curriculum.units.flatMap { it.skills }.mapNotNull { core[it] }
    }

    /**
     * Course skills (Track.CORE, map order) with a clean wait-mode run of any of their passages, by passage id in
     * [history]. A clean timed run alone doesn't tick.
     */
    public fun ticked(
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
    ): Set<String> {
        val passed = curriculum.passages.filter { history[it.id]?.cleanWait == true }.flatMapTo(HashSet()) { it.skills }
        return skills(curriculum).map { it.id }.filterTo(LinkedHashSet()) { it in passed }
    }

    /** The first course skill in map order not in [ticked]; null when all are. */
    public fun next(
        curriculum: Curriculum,
        ticked: Set<String>,
    ): Skill? = skills(curriculum).firstOrNull { it.id !in ticked }
}
