package pianoanchor.engine.learner

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LearnerDayTest {
    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun `03_59 belongs to the previous day`() {
        val instant = Instant.parse("2026-11-14T03:59:00Z")
        assertEquals(LocalDate(2026, 11, 13), learnerDay(instant, TimeZone.UTC))
    }

    @Test
    fun `04_00 starts a new day`() {
        val instant = Instant.parse("2026-11-14T04:00:00Z")
        assertEquals(LocalDate(2026, 11, 14), learnerDay(instant, TimeZone.UTC))
    }

    @Test
    fun `the zone decides the local time`() {
        // 02:30 UTC is 03:30 in Berlin in November (previous day), 04:30 once UTC is 03:30.
        assertEquals(LocalDate(2026, 11, 13), learnerDay(Instant.parse("2026-11-14T02:30:00Z"), berlin))
        assertEquals(LocalDate(2026, 11, 14), learnerDay(Instant.parse("2026-11-14T03:30:00Z"), berlin))
    }

    @Test
    fun `DST change keeps the date arithmetic right`() {
        // Berlin ended summer time on 2026-10-25 (03:00 -> 02:00): 02:59 CEST and 02:30 CET are both before 04:00.
        assertEquals(LocalDate(2026, 10, 24), learnerDay(Instant.parse("2026-10-25T00:59:00Z"), berlin))
        assertEquals(LocalDate(2026, 10, 24), learnerDay(Instant.parse("2026-10-25T01:30:00Z"), berlin))
        assertEquals(LocalDate(2026, 10, 25), learnerDay(Instant.parse("2026-10-25T03:00:00Z"), berlin))
        // The first day of the year: before 04:00 is the previous year's last day.
        assertEquals(LocalDate(2025, 12, 31), learnerDay(Instant.parse("2026-01-01T02:00:00Z"), TimeZone.UTC))
    }
}
