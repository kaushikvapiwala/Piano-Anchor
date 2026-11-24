package pianoanchor.engine.sight

import pianoanchor.core.model.AttemptSummary
import pianoanchor.core.model.Band
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val OPEN_ALL = 8

class SightStaircaseTest {
    private var clock = 0L
    private var next = 0L

    private fun attempt(
        band: Band,
        passageId: String = SightReadingGenerator.idOf(1, next++),
        at: Long = clock++,
    ) = AttemptSummary(
        id = "a$at-$passageId",
        passageId = passageId,
        startedAt = Instant.fromEpochSeconds(at),
        band = band,
    )

    private fun run(
        vararg bands: Band,
        open: Int = OPEN_ALL,
    ) = SightStaircase.level(bands.map { attempt(it) }, open)

    @Test
    fun startsAt1() = assertEquals(1, SightStaircase.level(emptyList(), OPEN_ALL))

    @Test
    fun twoCleanStepUp() {
        assertEquals(1, run(Band.CLEAN))
        assertEquals(2, run(Band.CLEAN, Band.CLEAN))
        assertEquals(2, run(Band.CLEAN, Band.CLEAN, Band.CLEAN))
        assertEquals(3, run(Band.CLEAN, Band.CLEAN, Band.CLEAN, Band.CLEAN))
    }

    @Test
    fun goodBreaksTheStreak() {
        assertEquals(1, run(Band.CLEAN, Band.GOOD, Band.CLEAN))
        assertEquals(2, run(Band.CLEAN, Band.GOOD, Band.CLEAN, Band.CLEAN))
    }

    @Test
    fun keepPractisingStepsDown() {
        assertEquals(1, run(Band.CLEAN, Band.CLEAN, Band.KEEP_PRACTISING))
        assertEquals(2, run(Band.CLEAN, Band.CLEAN, Band.CLEAN, Band.CLEAN, Band.KEEP_PRACTISING))
        assertEquals(1, run(Band.CLEAN, Band.KEEP_PRACTISING, Band.CLEAN))
    }

    @Test
    fun neverBelow1() = assertEquals(1, run(Band.KEEP_PRACTISING, Band.KEEP_PRACTISING))

    @Test
    fun clampedToOpen() {
        val many = Array(8) { Band.CLEAN }
        assertEquals(3, run(*many, open = 3))
        assertEquals(5, run(*many, open = OPEN_ALL))
        assertEquals(1, run(*many, open = 1))
    }

    @Test
    fun onlyFirstPlayOfAnIdCounts() {
        val replay = SightReadingGenerator.idOf(1, 99)
        val attempts =
            listOf(
                attempt(Band.CLEAN),
                attempt(Band.CLEAN, passageId = replay),
                attempt(Band.CLEAN, passageId = replay),
            )
        assertEquals(2, SightStaircase.level(attempts, OPEN_ALL))
        val replayOnly = listOf(attempt(Band.CLEAN, passageId = replay), attempt(Band.CLEAN, passageId = replay))
        assertEquals(1, SightStaircase.level(replayOnly, OPEN_ALL))
    }

    @Test
    fun attemptsMayComeInAnyOrder() {
        val first = attempt(Band.CLEAN, at = 1)
        val second = attempt(Band.CLEAN, at = 2)
        val third = attempt(Band.KEEP_PRACTISING, at = 3)
        assertEquals(1, SightStaircase.level(listOf(third, first, second), OPEN_ALL))
        val replay = SightReadingGenerator.idOf(1, 5)
        val early = attempt(Band.CLEAN, passageId = replay, at = 1)
        val late = attempt(Band.KEEP_PRACTISING, passageId = replay, at = 2)
        assertEquals(1, SightStaircase.level(listOf(late, early), OPEN_ALL))
    }

    @Test
    fun ignoresNonSightAttempts() {
        val attempts = listOf(attempt(Band.CLEAN), attempt(Band.CLEAN, passageId = "ode-to-joy"))
        assertEquals(1, SightStaircase.level(attempts, OPEN_ALL))
    }

    @Test
    fun zeroWhenClosed() = assertEquals(0, run(Band.CLEAN, Band.CLEAN, open = 0))
}
