package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class EarExerciseTest {
    private val c4 = setOf(MidiNote(60))
    private val e4 = setOf(MidiNote(64))

    @Test
    fun `an echo keeps what is played and whether the octave counts`() {
        val echo = EarExercise.Echo(listOf(c4, e4), exactOctave = false)
        assertEquals(listOf(c4, e4), echo.sounds)
        assertEquals(false, echo.exactOctave)
        assertEquals(echo, echo.copy())
    }

    @Test
    fun `an identify item keeps its choices and answer`() {
        val item = EarExercise.Identify(listOf(c4, e4), listOf(EarChoice.HIGHER, EarChoice.LOWER), EarChoice.HIGHER)
        assertEquals(listOf(c4, e4), item.sounds)
        assertEquals(listOf(EarChoice.HIGHER, EarChoice.LOWER), item.choices)
        assertEquals(EarChoice.HIGHER, item.answer)
        assertEquals(item, item.copy())
    }

    @Test
    fun `an item ends first try, recovered or missed`() =
        assertEquals(
            listOf(EarItemOutcome.FIRST_TRY, EarItemOutcome.RECOVERED, EarItemOutcome.MISSED),
            EarItemOutcome.entries,
        )
}
