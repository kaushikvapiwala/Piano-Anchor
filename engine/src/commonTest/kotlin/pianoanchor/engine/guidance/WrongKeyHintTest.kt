package pianoanchor.engine.guidance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WrongKeyHintTest {
    @Test
    fun everyPitchClassGivesItsLandmark() {
        val expected =
            listOf(
                Landmark.LEFT_OF_TWO,
                Landmark.BLACK_ABOVE,
                Landmark.BETWEEN_TWO,
                Landmark.BLACK_ABOVE,
                Landmark.RIGHT_OF_TWO,
                Landmark.LEFT_OF_THREE,
                Landmark.BLACK_ABOVE,
                Landmark.IN_THREE_LOW,
                Landmark.BLACK_ABOVE,
                Landmark.IN_THREE_HIGH,
                Landmark.BLACK_ABOVE,
                Landmark.RIGHT_OF_THREE,
            )
        expected.forEachIndexed { pitchClass, landmark ->
            assertEquals(landmark, wrongKeyHint(played = 100, expected = 48 + pitchClass).landmark)
        }
    }

    @Test
    fun expectedKeyBelowThePlayedOneLiesLeft() {
        val hint = wrongKeyHint(played = 62, expected = 60)
        assertEquals(Direction.LEFT, hint.direction)
        assertEquals(Landmark.LEFT_OF_TWO, hint.landmark)
    }

    @Test
    fun expectedKeyAboveThePlayedOneLiesRight() {
        assertEquals(Direction.RIGHT, wrongKeyHint(played = 60, expected = 64).direction)
    }

    @Test
    fun theSameLetterInAnotherOctaveIsSaidSo() {
        assertEquals(Direction.SAME_KEY_OTHER_OCTAVE, wrongKeyHint(played = 72, expected = 60).direction)
        assertEquals(Direction.SAME_KEY_OTHER_OCTAVE, wrongKeyHint(played = 60, expected = 72).direction)
    }

    @Test
    fun aBlackExpectedKeyIsFoundAboveItsWhiteKey() {
        assertEquals(Landmark.BLACK_ABOVE, wrongKeyHint(played = 60, expected = 61).landmark)
    }

    @Test
    fun theHintKeepsBothNotes() {
        assertEquals(WrongKeyHint(65, 60, Landmark.LEFT_OF_TWO, Direction.LEFT), wrongKeyHint(65, 60))
    }

    @Test
    fun aRightKeyNeedsNoHint() {
        assertFailsWith<IllegalArgumentException> { wrongKeyHint(60, 60) }
    }
}
