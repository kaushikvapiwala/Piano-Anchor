package pianoanchor.engine.notation

import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.NoteText
import pianoanchor.engine.content.exerciseRights
import pianoanchor.engine.sight.SightLevels
import pianoanchor.engine.sight.SightReadingGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val FOUR_FOUR = TimeSignature(4, 4)

private fun passage(
    text: String,
    time: TimeSignature = FOUR_FOUR,
    key: KeySignature = KeySignature.C,
) = Passage("test", "Test", NoteText.parse(text, time), time, 80, exerciseRights("test"), keySignature = key)

private fun engrave(
    text: String,
    time: TimeSignature = FOUR_FOUR,
    key: KeySignature = KeySignature.C,
) = Engraving.engrave(passage(text, time, key))

class EngravingTest {
    @Test
    fun quarterRunHasUpStemsAndNoFlagsOrRests() {
        val engraved = engrave("C4q D4q E4q F4q")
        assertEquals(4, engraved.chords.size)
        assertTrue(engraved.chords.all { it.value == NoteValue.QUARTER && it.stem == StemDirection.UP })
        assertTrue(engraved.chords.none { it.flag || it.beamGroup != null })
        assertTrue(engraved.rests.isEmpty())
        assertEquals(KeySignature.C, engraved.key)
        assertEquals(listOf(0, 960, 1920, 2880), engraved.chords.map { it.onsetTicks })
        assertEquals(listOf(0, 1, 2, 3), engraved.chords.map { it.stepIndex })
    }

    @Test
    fun eighthsInOneBeatShareABeam() {
        val chords = engrave("C4e D4e E4q F4h").chords
        assertEquals(chords[0].beamGroup, chords[1].beamGroup)
        assertTrue(chords[0].beamGroup != null)
        assertTrue(chords.none { it.flag })
        assertEquals(NoteValue.QUARTER, chords[2].value)
        assertEquals(NoteValue.HALF, chords[3].value)
    }

    @Test
    fun eighthsSplitAcrossBeatsAreFlaggedNotBeamed() {
        val chords = engrave("C4q D4e E4q F4q G4e").chords
        val eighths = chords.filter { it.value == NoteValue.EIGHTH }
        assertEquals(2, eighths.size)
        assertTrue(eighths.all { it.flag && it.beamGroup == null })
    }

    @Test
    fun dottedValues() {
        val chords = engrave("E4q. D4e D4h").chords
        assertEquals(listOf(NoteValue.DOTTED_QUARTER, NoteValue.EIGHTH, NoteValue.HALF), chords.map { it.value })
        assertTrue(chords[0].value.dotted && !chords[1].value.dotted)
        assertTrue(chords[1].flag)
        assertEquals(listOf(NoteValue.DOTTED_HALF), engrave("C4h. rq").chords.map { it.value })
    }

    @Test
    fun wholeNoteHasNoStem() {
        assertEquals(StemDirection.NONE, engrave("C4w").chords.single().stem)
    }

    @Test
    fun restBetweenNotesIsAQuarterRest() {
        val rests = engrave("C4q rq E4h").rests
        assertEquals(listOf(EngravedRest(Staff.TREBLE, 960, NoteValue.QUARTER)), rests)
    }

    @Test
    fun emptyBarIsAWholeRest() {
        val rests = engrave("rw | C4w").rests
        assertEquals(listOf(EngravedRest(Staff.TREBLE, 0, NoteValue.WHOLE)), rests)
    }

    @Test
    fun emptyBarIsAWholeRestInAnyMetre() {
        val rests = engrave("rh. | C4h.", TimeSignature(3, 4)).rests
        assertEquals(listOf(EngravedRest(Staff.TREBLE, 0, NoteValue.WHOLE)), rests)
    }

    @Test
    fun restsAreAlignedNotDotted() {
        val rests = engrave("C4q rh.").rests
        assertEquals(
            listOf(
                EngravedRest(Staff.TREBLE, 960, NoteValue.QUARTER),
                EngravedRest(Staff.TREBLE, 1920, NoteValue.HALF),
            ),
            rests,
        )
    }

    @Test
    fun eighthRestFillsAHalfBeat() {
        val rests = engrave("C4e re D4q E4h").rests
        assertEquals(listOf(EngravedRest(Staff.TREBLE, 480, NoteValue.EIGHTH)), rests)
    }

    @Test
    fun restsAreCutAtBarLines() {
        val rests = engrave("C4h rh | rh C4h").rests
        assertEquals(
            listOf(
                EngravedRest(Staff.TREBLE, 1920, NoteValue.HALF),
                EngravedRest(Staff.TREBLE, 3840, NoteValue.HALF),
            ),
            rests,
        )
    }

    @Test
    fun trailingRestsFillTheLastBar() {
        val rests = engrave("C4q rq rh").rests
        assertEquals(listOf(960, 1920), rests.map { it.onsetTicks })
    }

    @Test
    fun gapAfterAStaffsLastNoteRunsToThePassageEnd() {
        val rests = engrave("[C3 E4]q C4q rh").rests
        assertEquals(listOf(960, 1920), rests.filter { it.staff == Staff.BASS }.map { it.onsetTicks })
    }

    @Test
    fun stemPointsUpFromANoteBelowTheMiddleLine() {
        assertEquals(StemDirection.UP, engrave("C4q rh.").chords.single().stem)
    }

    @Test
    fun stemPointsDownFromANoteOnTheMiddleLine() {
        assertEquals(StemDirection.DOWN, engrave("B4q rh.").chords.single().stem)
    }

    @Test
    fun chordIsOneEngravedChordAndTheFarthestNoteDecidesItsStem() {
        val chords = engrave("[C4 E4 G4]q rh.").chords
        assertEquals(1, chords.size)
        assertEquals(StemDirection.UP, chords.single().stem)
        assertEquals(listOf("C4", "E4", "G4"), chords.single().notes.map { it.pitch.name })
        assertEquals(StemDirection.DOWN, engrave("[A4 G5]q rh.").chords.single().stem)
    }

    @Test
    fun beamGroupTakesItsStemFromTheNoteFarthestFromTheMiddle() {
        val chords = engrave("G4e B5e rh.").chords
        assertEquals(listOf(StemDirection.DOWN, StemDirection.DOWN), chords.map { it.stem })
        assertEquals(listOf(StemDirection.UP, StemDirection.UP), engrave("C4e B4e rh.").chords.map { it.stem })
    }

    @Test
    fun bassStaffUsesItsOwnMiddleLine() {
        assertEquals(StemDirection.DOWN, engrave("F3q rh.").chords.single().stem)
        assertEquals(StemDirection.UP, engrave("C3q rh.").chords.single().stem)
    }

    @Test
    fun beamGroupsOnTwoStavesHaveDifferentIds() {
        val chords = engrave("[C3 C4]e [D3 D4]e rh.").chords
        assertEquals(2, chords.mapNotNull { it.beamGroup }.toSet().size)
    }

    @Test
    fun accidentalsInGMajor() {
        val chords = engrave("F#4q F4q F4q rq | F4q rh.", FOUR_FOUR, KeySignature.G).chords
        val signs = chords.map { it.notes.single().accidental }
        assertEquals(listOf(null, Accidental.NATURAL, null, Accidental.NATURAL), signs)
    }

    @Test
    fun accidentalsInCMajor() {
        val chords = engrave("F#4q F#4q rh").chords
        assertEquals(listOf(Accidental.SHARP, null), chords.map { it.notes.single().accidental })
    }

    @Test
    fun sharpOnAnUnalteredLetterInGMajorNeedsASign() {
        val chords = engrave("C#4q rh.", key = KeySignature.G).chords
        assertEquals(
            Accidental.SHARP,
            chords
                .single()
                .notes
                .single()
                .accidental,
        )
    }

    @Test
    fun accidentalsAreRememberedPerOctave() {
        val chords = engrave("F#4q F#5q F#4q rq").chords
        assertEquals(listOf(Accidental.SHARP, Accidental.SHARP, null), chords.map { it.notes.single().accidental })
    }

    @Test
    fun flatKeyDrawsNoRaisedAccidentals() {
        val chords = engrave("B4q rh.", key = KeySignature(-1)).chords
        assertNull(
            chords
                .single()
                .notes
                .single()
                .accidental,
        )
    }

    @Test
    fun staffWithNoNotesHasNoRests() {
        assertTrue(engrave("C4q rq E4h").rests.none { it.staff == Staff.BASS })
    }

    @Test
    fun stepOnBothStavesGivesOneChordPerStaff() {
        val chords = engrave("[C3 E4]q rh.").chords
        assertEquals(listOf(Staff.TREBLE, Staff.BASS), chords.map { it.staff }.sortedBy { it.ordinal })
        assertEquals(setOf(0), chords.map { it.stepIndex }.toSet())
        assertEquals(
            MidiNote(48),
            chords
                .single { it.staff == Staff.BASS }
                .notes
                .single()
                .pitch,
        )
    }

    @Test
    fun chordNotesOfDifferentLengthsShareTheShortestValue() {
        val steps =
            listOf(Step(0, setOf(ScoreNote(MidiNote(60), 3840), ScoreNote(MidiNote(64), 960))))
        val chord = Engraving.engrave(passage(steps)).chords.single()
        assertEquals(NoteValue.QUARTER, chord.value)
    }

    @Test
    fun aLengthWithNoExactValueUsesTheLongestValueThatFits() {
        fun valueOf(length: Int) =
            Engraving
                .engrave(passage(listOf(Step(0, setOf(ScoreNote(MidiNote(60), length))))))
                .chords
                .single()
                .value
        assertEquals(NoteValue.QUARTER, valueOf(1000))
        assertEquals(NoteValue.EIGHTH, valueOf(100))
    }

    @Test
    fun gapsBetweenOddLengthsStillTerminate() {
        val steps =
            listOf(
                Step(0, setOf(ScoreNote(MidiNote(60), 100))),
                Step(1000, setOf(ScoreNote(MidiNote(60), 100))),
            )
        Engraving.engrave(passage(steps))
    }

    @Test
    fun everyBuiltInPassageAndSightExerciseEngravesWithExactValues() {
        val sight = (1..SightLevels.all.size).map { SightReadingGenerator.passage(it, 7L) }
        val exact = NoteValue.entries.map { it.ticks }.toSet()
        (Curriculum.passages + sight).forEach { passage ->
            passage.steps.forEach { step ->
                step.notes.forEach { note ->
                    assertTrue(note.lengthTicks in exact, "${passage.id}: ${note.lengthTicks} ticks has no note value")
                }
            }
            Engraving.engrave(passage)
        }
    }

    private fun passage(steps: List<Step>) = Passage("odd", "Odd", steps, FOUR_FOUR, 80, exerciseRights("test"))
}
