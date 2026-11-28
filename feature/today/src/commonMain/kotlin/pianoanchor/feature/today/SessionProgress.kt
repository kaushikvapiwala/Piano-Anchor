package pianoanchor.feature.today

import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionStep

/**
 * Which steps of the one-tap session are finished on a learner day, and whether the day's recap was reached. It is
 * stored with the [settings] (`Settings.sessionDay`, `sessionFinished`, `sessionCompleted`), so a restart of the app
 * resumes the day's session. When the store can't be read or written, the run carries on from a copy in memory.
 */
public class SessionProgress(
    private val settings: SettingsRepositoryPort,
) {
    private var memory = Settings()

    /** The keys of the steps finished on [day]. */
    public suspend fun finishedOn(day: LocalDate): Set<String> =
        read().takeIf { it.sessionDay == day }?.sessionFinished.orEmpty()

    /** Whether the session's recap was reached on [day]. */
    public suspend fun completedOn(day: LocalDate): Boolean = read().let { it.sessionDay == day && it.sessionCompleted }

    /** Records [step] as finished on [day]; a new day forgets the earlier one. */
    public suspend fun markFinished(
        day: LocalDate,
        step: SessionStep,
    ): Unit = write { it.onDay(day).let { s -> s.copy(sessionFinished = s.sessionFinished + step.key()) } }

    /** Records that the recap of [day] was reached. */
    public suspend fun markCompleted(day: LocalDate): Unit = write { it.onDay(day).copy(sessionCompleted = true) }

    private suspend fun read(): Settings =
        try {
            settings.settings.first().also { memory = it }
        } catch (_: StorageUnavailableException) {
            memory
        }

    private suspend fun write(change: (Settings) -> Settings) {
        memory = change(memory)
        try {
            settings.update(change)
        } catch (_: StorageUnavailableException) {
            // The run carries on from memory.
        }
    }

    private fun Settings.onDay(day: LocalDate): Settings =
        if (sessionDay == day) {
            this
        } else {
            copy(sessionDay = day, sessionFinished = emptySet(), sessionCompleted = false)
        }
}

/** What identifies a step across a recomposed session: its kind and passage, skill or nothing. */
internal fun SessionStep.key(): String =
    when (val card = card) {
        is Card.ColdOpen -> "cold:${card.passage.id}"
        is Card.Quizzes -> "quizzes"
        is Card.Lesson -> "lesson:${card.skill.id}"
        is Card.Song -> "${if (this is SessionStep.PlayAlong) "along" else "song"}:${card.passage.id}"
        is Card.Ear -> "ear"
        is Card.SightReading -> "sight:${card.passage.id}"
    }
