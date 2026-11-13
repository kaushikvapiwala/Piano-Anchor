package pianoanchor.feature.practice

import pianoanchor.core.model.MidiNote
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyRangeTest {
    @Test
    fun lowestNoteC4GivesC4ToB5() {
        assertEquals(60..83, keyStripRange(MidiNote(60)))
    }

    @Test
    fun lowestNoteDSharp3StartsAtTheCBelow() {
        assertEquals(48..71, keyStripRange(MidiNote(51)))
    }

    @Test
    fun keyStripRangeStaysOnTheKeyboard() {
        assertEquals(96..119, keyStripRange(MidiNote(120))) // starts on a C, ends below 127
    }

    @Test
    fun whiteAndBlackKeysAreTold() {
        assertEquals(listOf(false, true, false, true, false, false, true), (60..66).map { MidiNote(it).isBlack })
    }

    @Test
    fun staffPositionCountsDiatonicStepsAboveE4() {
        assertEquals(listOf(-2, -1, 0, 1, 5, 10), listOf(60, 62, 64, 65, 72, 81).map { MidiNote(it).staffPosition })
        assertEquals(-2, MidiNote(61).staffPosition) // C#4 sits on C4's line, with a sharp
    }
}
