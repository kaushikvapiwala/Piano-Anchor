package pianoanchor.engine.lesson

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Rating
import pianoanchor.core.model.Review
import pianoanchor.core.model.Skill
import pianoanchor.core.model.Track
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.learnerDay
import pianoanchor.engine.session.PassageHistory
import kotlin.time.Instant

/**
 * Which concepts of the beginner course the learner has passed and which is suggested next
 * (`docs/course/beginner-course.md`, "Mastery"). Pure.
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
     * Course skills (Track.CORE, map order) *passed*: a clean wait-mode run with no hint shown
     * ([PassageHistory.cleanUnaidedWait]) of any of their passages, by passage id in [history], and every course skill
     * in [placed] (the onboarding's placement test): a placed learner starts at the first concept after them. A clean
     * run with a hint, or a clean timed run alone, doesn't pass.
     */
    public fun passed(
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
        placed: Set<String> = emptySet(),
    ): Set<String> {
        val passed =
            curriculum.passages.filter { history[it.id]?.cleanUnaidedWait == true }.flatMapTo(
                HashSet(),
            ) { it.skills }
        return skills(curriculum).map { it.id }.filterTo(LinkedHashSet()) { it in passed || it in placed }
    }

    /**
     * The learner day ([zone]) each course skill was passed on, by skill id: the day of the earliest clean unaided wait
     * run of any of its passages in [history]. Skills not passed by a played run (placed ones too) are absent.
     */
    public fun passedOn(
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
        zone: TimeZone,
    ): Map<String, LocalDate> {
        val first = HashMap<String, Instant>()
        for (passage in curriculum.passages) {
            val at = history[passage.id]?.firstCleanUnaidedWait ?: continue
            for (skill in passage.skills) first.merge(skill, at) { a, b -> minOf(a, b) }
        }
        return skills(curriculum)
            .mapNotNull { skill ->
                first[skill.id]?.let { skill.id to learnerDay(it, zone) }
            }.toMap()
    }

    /**
     * The passed course skills (those in [passed], by the day they were passed, in map order) that are *learned*: an
     * unaided review rated Hard, Good or Easy (not Again) in [reviews] on a learner day after the day they were passed,
     * and every course skill in [placed]. A quiz that is failed (Again) leaves the skill passed, not learned.
     */
    public fun learned(
        curriculum: Curriculum,
        passed: Map<String, LocalDate>,
        reviews: List<Review>,
        placed: Set<String> = emptySet(),
    ): Set<String> {
        val recalled = reviews.filter { it.unaided && it.rating != Rating.AGAIN }
        return skills(curriculum).map { it.id }.filterTo(LinkedHashSet()) { id ->
            id in placed || passed[id]?.let { day -> recalled.any { it.skillId == id && it.day > day } } == true
        }
    }

    /** The first course skill in map order not in [passed]; null when all are. */
    public fun next(
        curriculum: Curriculum,
        passed: Set<String>,
    ): Skill? = skills(curriculum).firstOrNull { it.id !in passed }
}
