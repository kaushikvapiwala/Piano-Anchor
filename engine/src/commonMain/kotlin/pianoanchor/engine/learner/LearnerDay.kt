package pianoanchor.engine.learner

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import pianoanchor.engine.Tunables
import kotlin.time.Instant

/** The learner day of [instant] in [zone]: the local date, starting at LEARNER_DAY_START_HOUR. */
public fun learnerDay(
    instant: Instant,
    zone: TimeZone,
): LocalDate {
    val local = instant.toLocalDateTime(zone)
    return if (local.hour < Tunables.LEARNER_DAY_START_HOUR) local.date.minus(1, DateTimeUnit.DAY) else local.date
}
