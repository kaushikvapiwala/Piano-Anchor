package pianoanchor.engine.onboarding

import pianoanchor.core.model.Band
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacementTestTest {
    @Test
    fun startsWithUnit0() = assertEquals(0, PlacementTest.next(emptyList()))

    @Test
    fun goodPasses() {
        assertTrue(PlacementTest.passes(Band.GOOD))
        assertTrue(PlacementTest.passes(Band.CLEAN))
    }

    @Test
    fun keepPractisingFails() = assertFalse(PlacementTest.passes(Band.KEEP_PRACTISING))

    @Test
    fun stopsAtFirstFailure() {
        val bands = listOf(Band.CLEAN, Band.KEEP_PRACTISING)
        assertNull(PlacementTest.next(bands))
        assertEquals(1, PlacementTest.startUnit(bands))
    }

    @Test
    fun passedPiecesLeadToTheNext() {
        assertEquals(1, PlacementTest.next(listOf(Band.GOOD)))
        assertEquals(3, PlacementTest.next(listOf(Band.GOOD, Band.CLEAN, Band.GOOD)))
    }

    @Test
    fun allFivePassedStartsAt5() {
        val bands = List(5) { Band.GOOD }
        assertNull(PlacementTest.next(bands))
        assertEquals(5, PlacementTest.startUnit(bands))
    }

    @Test
    fun failingTheUnit4PieceStartsAt4() {
        val bands = List(4) { Band.GOOD } + Band.KEEP_PRACTISING
        assertNull(PlacementTest.next(bands))
        assertEquals(4, PlacementTest.startUnit(bands))
        assertEquals(4, PlacementTest.next(List(4) { Band.GOOD }))
    }

    @Test
    fun firstFailedStartsAt0() {
        val bands = listOf(Band.KEEP_PRACTISING)
        assertNull(PlacementTest.next(bands))
        assertEquals(0, PlacementTest.startUnit(bands))
    }

    @Test
    fun sameBandsSameStart() {
        val bands = listOf(Band.CLEAN, Band.GOOD, Band.KEEP_PRACTISING)
        assertEquals(PlacementTest.startUnit(bands), PlacementTest.startUnit(bands.toList()))
        assertEquals(2, PlacementTest.startUnit(bands))
    }
}
