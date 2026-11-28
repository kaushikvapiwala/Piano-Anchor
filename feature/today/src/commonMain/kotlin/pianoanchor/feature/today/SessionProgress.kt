package pianoanchor.feature.today

import kotlinx.datetime.LocalDate
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionStep

/**
 * Which steps of the one-tap session are finished on a learner day, and whether the day's recap was reached. It is
 * held in memory for the life of the app (one instance, shared by Today and the session flow): a restart of the app
 * starts the day's session over.
 */
public class SessionProgress {
    private var day: LocalDate? = null
    private val finished = mutableSetOf<String>()
    private var completed = false

    /** The keys of the steps finished on [day]. */
    public fun finishedOn(day: LocalDate): Set<String> = if (day == this.day) finished.toSet() else emptySet()

    /** Whether the session's recap was reached on [day]. */
    public fun completedOn(day: LocalDate): Boolean = day == this.day && completed

    /** Records [step] as finished on [day]; a new day forgets the earlier one. */
    public fun markFinished(
        day: LocalDate,
        step: SessionStep,
    ) {
        roll(day)
        finished += step.key()
    }

    /** Records that the recap of [day] was reached. */
    public fun markCompleted(day: LocalDate) {
        roll(day)
        completed = true
    }

    private fun roll(day: LocalDate) {
        if (day != this.day) {
            this.day = day
            finished.clear()
            completed = false
        }
    }
}

/** What identifies a step across a recomposed session: its kind and passage, skill or nothing. */
internal fun SessionStep.key(): String =
    when (val card = card) {
        is Card.ColdOpen -> "cold:${card.passage.id}"
        is Card.Quizzes -> "quizzes"
        is Card.Lesson -> "lesson:${card.skill.id}"
        is Card.Song -> "song:${card.passage.id}"
        is Card.Ear -> "ear"
        is Card.SightReading -> "sight:${card.passage.id}"
    }
