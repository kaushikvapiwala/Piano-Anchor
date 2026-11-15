package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StepCorrect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val MS = 1_000_000L

class HintDelayTest {
    /** A run whose steps were completed [gapsMs] apart (the first at 0). */
    private fun run(vararg gapsMs: Long): Attempt {
        var t = 0L
        val times = listOf(0L) + gapsMs.map { gap -> (t + gap * MS).also { t = it } }
        val steps = times.mapIndexed { i, at -> StepCorrect(at, i) }
        return Attempt(
            id = "a",
            passageId = "p",
            deviceId = "d",
            startedAt = Instant.fromEpochSeconds(0),
            durationNanos = 1,
            stepCount = times.size,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = steps + PassageComplete(times.last(), times.lastIndex),
            tempoBpm = null,
            playedNotes = emptyList(),
        )
    }

    @Test
    fun noHistoryGivesTheDefault() = assertEquals(1_500, HintDelay.millis(emptyList()))

    @Test
    fun runsWithoutGapsGiveTheDefault() = assertEquals(1_500, HintDelay.millis(listOf(run())))

    @Test
    fun isTheMedianGapTimesOnePointFive() = assertEquals(1_350, HintDelay.millis(listOf(run(900, 800, 5_000))))

    @Test
    fun aMedianBetweenTwoGapsIsTheirMean() = assertEquals(1_200, HintDelay.millis(listOf(run(700, 900))))

    @Test
    fun isClampedLow() = assertEquals(800, HintDelay.millis(listOf(run(100, 100))))

    @Test
    fun isClampedHigh() = assertEquals(3_000, HintDelay.millis(listOf(run(4_000, 4_000))))

    @Test
    fun usesOnlyTheNewestFiveRuns() {
        val newest = List(5) { run(600, 600) }
        val older = List(5) { run(2_000, 2_000) }
        assertEquals(900, HintDelay.millis(newest + older))
    }
}
