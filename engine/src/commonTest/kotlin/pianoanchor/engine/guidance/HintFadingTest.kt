package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.HintLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HintFadingTest {
    private fun run(band: Band) =
        Attempt(
            id = "a",
            passageId = "p",
            deviceId = "d",
            startedAt = Instant.fromEpochSeconds(0),
            durationNanos = 1,
            stepCount = 1,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = band,
            events = emptyList(),
            tempoBpm = null,
            playedNotes = emptyList(),
        )

    private fun level(vararg bands: Band) = HintFading.level(bands.map(::run))

    private val clean = Band.CLEAN
    private val good = Band.GOOD
    private val weak = Band.KEEP_PRACTISING

    @Test
    fun noHistoryStartsFull() = assertEquals(HintLevel.FULL, level())

    @Test
    fun oneCleanRunChangesNothing() = assertEquals(HintLevel.FULL, level(clean))

    @Test
    fun twoCleanRunsFadeToDelayedAndFourToOff() {
        assertEquals(HintLevel.DELAYED, level(clean, clean))
        assertEquals(HintLevel.DELAYED, level(clean, clean, clean))
        assertEquals(HintLevel.OFF, level(clean, clean, clean, clean))
    }

    @Test
    fun cleanRunsAtOffStayOff() = assertEquals(HintLevel.OFF, level(clean, clean, clean, clean, clean, clean))

    @Test
    fun aGoodRunBreaksTheCleanStreak() {
        assertEquals(HintLevel.FULL, level(clean, good, clean))
        assertEquals(HintLevel.DELAYED, level(clean, good, clean, clean))
    }

    @Test
    fun twoKeepPractisingRunsRaiseTheLevel() {
        assertEquals(HintLevel.FULL, level(clean, clean, weak, weak))
        assertEquals(HintLevel.OFF, level(clean, clean, clean, clean, weak))
        assertEquals(HintLevel.DELAYED, level(clean, clean, clean, clean, weak, weak))
    }

    @Test
    fun keepPractisingAtFullStaysFull() = assertEquals(HintLevel.FULL, level(weak, weak, weak, weak))

    @Test
    fun aGoodRunBreaksTheKeepPractisingStreak() = assertEquals(HintLevel.DELAYED, level(clean, clean, weak, good, weak))

    @Test
    fun aMixedStreakDoesNotCount() = assertEquals(HintLevel.FULL, level(clean, weak, clean, weak))

    @Test
    fun aMoveResetsTheCount() = assertEquals(HintLevel.DELAYED, level(clean, clean, clean))
}
