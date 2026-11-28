package pianoanchor.engine.content

import pianoanchor.core.model.PedalSpan
import pianoanchor.core.model.TimeSignature
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.onboarding.PlacementTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentTest {
    @Test
    fun builtInGraphIsValid() {
        assertEquals(52, Curriculum.graph.skills.size)
        assertEquals(setOf("finger-number-tunes"), Curriculum.graph.byId("bars-and-counting")!!.prerequisites)
    }

    @Test
    fun cdeExercisesRhCPosition() = assertEquals(setOf("rh-c-position"), Passages.CDE.skills)

    @Test
    fun placementPassagesExistAndAreInTheirUnits() {
        assertEquals(8, PlacementTest.passageIds.size)
        PlacementTest.passageIds.forEachIndexed { unit, id ->
            assertTrue(Curriculum.units[unit].passages.any { it.id == id }, "$id is not in unit $unit")
        }
    }

    private val rights = exerciseRights("a test")

    @Test
    fun aChordPassageTakesItsTimeSignatureAndPedalMarks() {
        val pedal = listOf(PedalSpan(0, 2880), PedalSpan(2880, 5760))
        val passage =
            chordPassage(
                id = "waltz",
                title = "Waltz",
                left = "[C3 E3 G3]h. | [F2 A2 C3]h.",
                right = "C4q D4q E4q | F4q G4q A4q",
                tempoBpm = 60,
                skills = setOf("s"),
                rights = rights,
                timeSignature = TimeSignature(3, 4),
                pedal = pedal,
            )
        assertEquals(TimeSignature(3, 4), passage.timeSignature)
        assertEquals(pedal, passage.pedal)
        assertEquals(listOf(0, 960, 1920, 2880, 3840, 4800), passage.steps.map { it.onsetTicks })
    }

    @Test
    fun builtinPassageKeepsPedalMarks() {
        val pedal = listOf(PedalSpan(0, 3840))
        val passage = builtinPassage("p", "P", "C4w", 60, setOf("s"), rights, pedal = pedal)
        assertEquals(pedal, passage.pedal)
    }

    @Test
    fun pedalIsMusicForPedalChecksAndPedalledPieces() {
        val piece = builtinPassage("p", "P", "C4w", 60, setOf("s"), rights, piece = true)
        val pedalled = piece.copy(pedal = listOf(PedalSpan(0, 3840)))
        val change = TryCheck.PedalChange(listOf(setOf(48), setOf(53)))
        assertTrue(Curriculum.pedalIsMusic("s", change, emptyList()))
        assertTrue(Curriculum.pedalIsMusic("s", TryCheck.PedalHold(setOf(48)), emptyList()))
        assertTrue(Curriculum.pedalIsMusic("s", TryCheck.AnyKey, listOf(pedalled)))
        assertFalse(Curriculum.pedalIsMusic("s", TryCheck.AnyKey, listOf(piece)))
        assertFalse(Curriculum.pedalIsMusic("t", TryCheck.AnyKey, listOf(pedalled)))
        assertFalse(Curriculum.pedalIsMusic("s", null, listOf(pedalled.copy(piece = false))))
        val earlier = piece.copy(id = "builtin:earlier")
        assertFalse(Curriculum.pedalIsMusic("s", TryCheck.AnyKey, listOf(earlier, pedalled)))
        assertTrue(Curriculum.pedalIsMusic("s", TryCheck.AnyKey, listOf(pedalled, earlier)))
        assertFalse(Curriculum.pedalIsMusic("rh-c-position"))
    }
}
