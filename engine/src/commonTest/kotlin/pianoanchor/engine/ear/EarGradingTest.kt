package pianoanchor.engine.ear

import pianoanchor.core.model.Band
import pianoanchor.core.model.EarItemOutcome.FIRST_TRY
import pianoanchor.core.model.EarItemOutcome.MISSED
import pianoanchor.core.model.EarItemOutcome.RECOVERED
import kotlin.test.Test
import kotlin.test.assertEquals

class EarGradingTest {
    @Test
    fun `4 first try is clean`() =
        assertEquals(Band.CLEAN, EarGrading.band(listOf(FIRST_TRY, FIRST_TRY, FIRST_TRY, FIRST_TRY)))

    @Test
    fun `3 first try and 1 recovered is good`() =
        assertEquals(Band.GOOD, EarGrading.band(listOf(FIRST_TRY, RECOVERED, FIRST_TRY, FIRST_TRY)))

    @Test
    fun `2 first try 1 recovered and 1 missed is good`() =
        assertEquals(Band.GOOD, EarGrading.band(listOf(FIRST_TRY, RECOVERED, MISSED, FIRST_TRY)))

    @Test
    fun `2 missed is keep practising`() =
        assertEquals(Band.KEEP_PRACTISING, EarGrading.band(listOf(FIRST_TRY, RECOVERED, MISSED, MISSED)))

    @Test
    fun `an empty set is keep practising`() = assertEquals(Band.KEEP_PRACTISING, EarGrading.band(emptyList()))
}
