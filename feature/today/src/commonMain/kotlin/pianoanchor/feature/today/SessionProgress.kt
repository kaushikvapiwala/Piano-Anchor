package pianoanchor.feature.today

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionStep

/**
 * Which steps of the one-tap session are finished on a learner day, and whether the day's recap was reached. It is
 * stored with the [settings] (`Settings.sessionDay`, `sessionFinished`, `sessionCompleted`), so a restart of the app
 * resumes the day's session. A mark is kept as pending from the moment it is made until a write of it succeeds, and
 * every read merges the pending marks into what the store holds, so a mark that is still being written, or whose
 * write failed (the next mark or read retries it), is never lost.
 */
public class SessionProgress(
    private val settings: SettingsRepositoryPort,
) {
    private val pending = mutableListOf<(Settings) -> Settings>()
    private val flushing = Mutex()
    private var lastRead = Settings()

    /** The keys of the steps finished on [day]. */
    public suspend fun finishedOn(day: LocalDate): Set<String> =
        read().takeIf { it.sessionDay == day }?.sessionFinished.orEmpty()

    /** Whether the session's recap was reached on [day]. */
    public suspend fun completedOn(day: LocalDate): Boolean = read().let { it.sessionDay == day && it.sessionCompleted }

    /** Records [step] as finished on [day]; a new day forgets the earlier one. */
    public suspend fun markFinished(
        day: LocalDate,
        step: SessionStep,
    ) {
        recordFinished(day, step)
        flush()
    }

    /** Records that the recap of [day] was reached. */
    public suspend fun markCompleted(day: LocalDate) {
        pending += { it.onDay(day).copy(sessionCompleted = true) }
        flush()
    }

    /** Forgets the steps finished on [day] and that its recap was reached: its session starts over. */
    public suspend fun restart(day: LocalDate) {
        pending += { it.copy(sessionDay = day, sessionFinished = emptySet(), sessionCompleted = false) }
        flush()
    }

    /** Notes [step] as finished at once, so the next read sees it; [flush] stores it. */
    internal fun recordFinished(
        day: LocalDate,
        step: SessionStep,
    ) {
        pending += { it.onDay(day).let { s -> s.copy(sessionFinished = s.sessionFinished + step.key()) } }
    }

    /** Writes the pending marks in order; one that fails stays pending, with those after it. */
    internal suspend fun flush() {
        flushing.withLock {
            while (pending.isNotEmpty()) {
                val change = pending.first()
                try {
                    settings.update(change)
                } catch (_: StorageUnavailableException) {
                    return
                }
                pending.removeFirst()
            }
        }
    }

    private suspend fun read(): Settings {
        flush()
        val stored =
            try {
                settings.settings.first().also { lastRead = it }
            } catch (_: StorageUnavailableException) {
                lastRead
            }
        return pending.fold(stored) { acc, change -> change(acc) }
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
