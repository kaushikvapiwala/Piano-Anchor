package pianoanchor.engine.lesson

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.SceneHand
import pianoanchor.core.model.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HandDemoTest {
    private fun demo(vararg notes: Int) = Demo(notes.mapIndexed { i, n -> Tone(MidiNote(n), i * 500L, 400) })

    @Test
    fun cToGDemoPutsTheThumbOnCAndFingersOneToFiveInOrder() {
        val hand = checkNotNull(HandDemo.of(demo(60, 62, 64, 65, 67)))
        assertEquals(SceneHand.RIGHT, hand.hand)
        assertEquals(MidiNote(60), hand.thumbNote)
        assertEquals(listOf(1, 2, 3, 4, 5), hand.presses.map { it.finger })
        assertEquals(listOf(60, 62, 64, 65, 67), hand.presses.map { it.note.number })
        assertEquals(listOf(0L, 500L, 1000L, 1500L, 2000L), hand.presses.map { it.startMs })
        assertEquals(List(5) { 400L }, hand.presses.map { it.lengthMs })
    }

    @Test
    fun eDCDemoUsesFingersThreeTwoOne() {
        val hand = checkNotNull(HandDemo.of(demo(64, 62, 60)))
        assertEquals(MidiNote(60), hand.thumbNote)
        assertEquals(listOf(3, 2, 1), hand.presses.map { it.finger })
    }

    @Test
    fun anEmptyDemoHasNoHand() = assertNull(HandDemo.of(Demo(emptyList())))

    @Test
    fun aBlackKeyHasNoHand() = assertNull(HandDemo.of(demo(60, 61)))

    @Test
    fun sixWhiteKeysHaveNoHand() = assertNull(HandDemo.of(demo(60, 69)))

    @Test
    fun fiveWhiteKeysAcrossAnOctaveBoundaryStillFit() {
        val hand = checkNotNull(HandDemo.of(demo(71, 72, 74, 76, 77)))
        assertEquals(MidiNote(71), hand.thumbNote)
        assertEquals(listOf(1, 2, 3, 4, 5), hand.presses.map { it.finger })
    }

    @Test
    fun aRepeatedNoteKeepsItsFinger() {
        val hand = checkNotNull(HandDemo.of(demo(62, 64, 62)))
        assertEquals(MidiNote(62), hand.thumbNote)
        assertEquals(listOf(1, 2, 1), hand.presses.map { it.finger })
    }

    @Test
    fun aChordPressesAllItsFingersTogether() {
        val hand = checkNotNull(HandDemo.of(Demo(listOf(60, 64, 67).map { Tone(MidiNote(it), 0, 800) })))
        assertEquals(listOf(1, 3, 5), hand.presses.map { it.finger })
    }

    @Test
    fun theLeftHandPutsTheThumbOnTheHighestKeyAndTheLittleFingerOnTheLowest() {
        val hand = checkNotNull(HandDemo.of(demo(48, 50, 52, 53, 55), SceneHand.LEFT))
        assertEquals(SceneHand.LEFT, hand.hand)
        assertEquals(MidiNote(55), hand.thumbNote)
        assertEquals(listOf(5, 4, 3, 2, 1), hand.presses.map { it.finger })
    }

    @Test
    fun theLeftHandHasNoHandForABlackKeyOrASixKeySpan() {
        assertNull(HandDemo.of(demo(48, 49), SceneHand.LEFT))
        assertNull(HandDemo.of(demo(48, 57), SceneHand.LEFT))
        assertNull(HandDemo.of(Demo(emptyList()), SceneHand.LEFT))
    }
}
