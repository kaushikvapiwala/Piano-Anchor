package pianoanchor.engine.lesson

import pianoanchor.core.model.NoteNames
import pianoanchor.engine.content.Skills
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val MIDDLE_C = 60
private const val C_SHARP = 61
private const val D = 62

private fun name(
    letter: Letter,
    accidental: Accidental,
) = KeyName(letter, accidental)

private fun natural(letter: Letter) = KeyName(letter, Accidental.NATURAL)

class NoteNameRuleTest {
    private val keyNames = Skills.WHITE_KEY_NAMES.id

    @Test
    fun `nothing passed lettered all white keys`() {
        assertEquals(NoteNameStage.ALL_WHITE, NoteNameRule.stage(NoteNames.AUTO, emptySet(), emptySet()))
    }

    @Test
    fun `key names passed leaves only the target letter`() {
        assertEquals(NoteNameStage.TARGET_ONLY, NoteNameRule.stage(NoteNames.AUTO, setOf(keyNames), emptySet()))
    }

    @Test
    fun `key names learned fades every letter`() {
        assertEquals(NoteNameStage.NONE, NoteNameRule.stage(NoteNames.AUTO, setOf(keyNames), setOf(keyNames)))
    }

    @Test
    fun `off shows no letters at any progress`() {
        assertEquals(NoteNameStage.NONE, NoteNameRule.stage(NoteNames.OFF, emptySet(), emptySet()))
    }

    @Test
    fun `sharps are passed once the concept is`() {
        assertEquals(false, NoteNameRule.sharpsPassed(setOf(keyNames)))
        assertEquals(true, NoteNameRule.sharpsPassed(setOf(Skills.SHARPS_AND_FLATS.id)))
    }

    @Test
    fun `white keys are lettered at all white`() {
        assertEquals(
            natural(Letter.C),
            NoteNameRule.label(MIDDLE_C, NoteNameStage.ALL_WHITE, emptySet(), emptySet(), false),
        )
        assertEquals(natural(Letter.D), NoteNameRule.label(D, NoteNameStage.ALL_WHITE, emptySet(), emptySet(), false))
    }

    @Test
    fun `black keys wait for sharps and flats`() {
        assertNull(NoteNameRule.label(C_SHARP, NoteNameStage.ALL_WHITE, emptySet(), emptySet(), false))
        assertEquals(
            name(Letter.C, Accidental.SHARP),
            NoteNameRule.label(C_SHARP, NoteNameStage.ALL_WHITE, emptySet(), emptySet(), true),
        )
        assertEquals(name(Letter.D, Accidental.FLAT), NoteNameRule.flatLabel(C_SHARP))
        assertNull(NoteNameRule.flatLabel(D))
    }

    @Test
    fun `target only keeps just the target`() {
        assertNull(NoteNameRule.label(D, NoteNameStage.TARGET_ONLY, setOf(MIDDLE_C), emptySet(), false))
        assertEquals(
            natural(Letter.C),
            NoteNameRule.label(MIDDLE_C, NoteNameStage.TARGET_ONLY, setOf(MIDDLE_C), emptySet(), false),
        )
    }

    @Test
    fun `a wrong key hint letters its keys at any stage`() {
        assertEquals(natural(Letter.D), NoteNameRule.label(D, NoteNameStage.NONE, emptySet(), setOf(D), false))
        assertNull(NoteNameRule.label(D, NoteNameStage.NONE, setOf(D), emptySet(), false))
    }

    @Test
    fun `labels combine the setting with progress`() {
        val auto = NoteLabels.of(NoteNames.AUTO, setOf(keyNames), setOf(keyNames))
        assertEquals(NoteLabels(NoteNameStage.NONE, sharpsPassed = false, hints = true), auto)
        assertEquals(natural(Letter.D), auto.label(D, wrongHint = setOf(D)))
        assertNull(auto.label(D))
        val off = NoteLabels.of(NoteNames.OFF, emptySet(), emptySet())
        assertNull(off.label(D, wrongHint = setOf(D)))
        assertEquals(natural(Letter.C), NoteLabels.of(NoteNames.AUTO, emptySet(), emptySet()).label(MIDDLE_C))
    }

    @Test
    fun `all twelve pitch classes map to their names`() {
        val expected =
            listOf(
                Letter.C to Accidental.NATURAL,
                Letter.C to Accidental.SHARP,
                Letter.D to Accidental.NATURAL,
                Letter.D to Accidental.SHARP,
                Letter.E to Accidental.NATURAL,
                Letter.F to Accidental.NATURAL,
                Letter.F to Accidental.SHARP,
                Letter.G to Accidental.NATURAL,
                Letter.G to Accidental.SHARP,
                Letter.A to Accidental.NATURAL,
                Letter.A to Accidental.SHARP,
                Letter.B to Accidental.NATURAL,
            )
        expected.forEachIndexed { i, (letter, accidental) ->
            val label = NoteNameRule.label(MIDDLE_C + i, NoteNameStage.ALL_WHITE, emptySet(), emptySet(), true)
            assertEquals(name(letter, accidental), label)
        }
    }

    @Test
    fun `flat labels name the black keys`() {
        val flats =
            mapOf(
                61 to Letter.D,
                63 to Letter.E,
                66 to Letter.G,
                68 to Letter.A,
                70 to Letter.B,
            )
        flats.forEach { (note, letter) -> assertEquals(name(letter, Accidental.FLAT), NoteNameRule.flatLabel(note)) }
        assertNull(NoteNameRule.flatLabel(MIDDLE_C))
    }
}
