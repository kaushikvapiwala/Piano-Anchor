package pianoanchor.engine.notation

import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PedalSpan
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.FOUR_FOUR
import pianoanchor.engine.content.NoteText
import pianoanchor.engine.content.THREE_FOUR
import pianoanchor.engine.content.exerciseRights
import pianoanchor.engine.sight.SightLevels
import pianoanchor.engine.sight.SightReadingGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val Q = TICKS_PER_QUARTER

class EngravingTest {
    private fun engrave(
        text: String,
        time: TimeSignature = FOUR_FOUR,
        key: KeySignature = KeySignature.C,
    ) = Engraving.engrave(passage(NoteText.parse(text, time), time, key))

    private fun passage(
        steps: List<Step>,
        time: TimeSignature = FOUR_FOUR,
        key: KeySignature = KeySignature.C,
    ) = Passage("p", "P", steps, time, 80, exerciseRights("test"), keySignature = key)

    private fun accidentals(engraved: Engraved) = engraved.chords.map { it.notes.single().accidental }

    @Test
    fun quartersHaveUpStemsAndNoFlagsOrRests() {
        val engraved = engrave("C4q D4q E4q F4q")

        assertEquals(4, engraved.chords.size)
        assertTrue(engraved.chords.all { it.value == NoteValue.QUARTER && it.stem == StemDirection.UP })
        assertTrue(engraved.chords.none { it.flag || it.beamGroup != null })
        assertTrue(engraved.rests.isEmpty())
        assertEquals(KeySignature.C, engraved.key)
    }

    @Test
    fun twoEighthsInOneBeatShareABeam() {
        val chords = engrave("C4e D4e E4q F4h").chords

        assertEquals(chords[0].beamGroup, chords[1].beamGroup)
        assertNotEquals(null, chords[0].beamGroup)
        assertTrue(chords.none { it.flag })
        assertEquals(NoteValue.QUARTER, chords[2].value)
        assertEquals(NoteValue.HALF, chords[3].value)
        assertNull(chords[2].beamGroup)
    }

    @Test
    fun eighthsSplitAcrossBeatsAreFlaggedNotBeamed() {
        val chords = engrave("C4q D4e E4q F4q G4e").chords

        assertTrue(chords[1].flag && chords[4].flag)
        assertTrue(chords.all { it.beamGroup == null })
    }

    @Test
    fun threeEighthsInABeatCannotExistSoRunsRestartAtTheNextBeat() {
        val chords = engrave("C4e D4e E4e F4e G4h").chords

        assertEquals(chords[0].beamGroup, chords[1].beamGroup)
        assertEquals(chords[2].beamGroup, chords[3].beamGroup)
        assertNotEquals(chords[0].beamGroup, chords[2].beamGroup)
    }

    @Test
    fun anEighthAfterARestIsNotBeamedToThePreviousOne() {
        val chords = engrave("C4e re D4e re E4h").chords

        assertTrue(chords.take(2).all { it.flag })
    }

    @Test
    fun beatsRestartInEachBar() {
        val chords = engrave("C4h D4q E4e F4e | G4w").chords

        assertEquals(chords[2].beamGroup, chords[3].beamGroup)
        assertNotEquals(null, chords[2].beamGroup)
    }

    @Test
    fun dottedValuesAreNamedExactly() {
        val chords = engrave("E4q. D4e D4h").chords

        assertEquals(NoteValue.DOTTED_QUARTER, chords[0].value)
        assertTrue(chords[0].value.dotted)
        assertEquals(NoteValue.EIGHTH, chords[1].value)
        assertTrue(chords[1].flag)
        assertEquals(NoteValue.HALF, chords[2].value)
        assertEquals(NoteValue.DOTTED_HALF, engrave("E4h. rq").chords.single().value)
    }

    @Test
    fun aWholeNoteHasNoStem() {
        assertEquals(StemDirection.NONE, engrave("C4w").chords.single().stem)
    }

    @Test
    fun aRestInTheMiddleIsDrawn() {
        val rests = engrave("C4q rq E4h").rests

        assertEquals(listOf(EngravedRest(Staff.TREBLE, Q, NoteValue.QUARTER)), rests)
    }

    @Test
    fun anEmptyBarIsAWholeRest() {
        assertEquals(listOf(EngravedRest(Staff.TREBLE, 0, NoteValue.WHOLE)), engrave("rw | C4w").rests)
    }

    @Test
    fun anEmptyBarIsAWholeRestInAnyMetre() {
        val rests = engrave("rq rq rq | C4h.", THREE_FOUR).rests

        assertEquals(listOf(EngravedRest(Staff.TREBLE, 0, NoteValue.WHOLE)), rests)
    }

    @Test
    fun aGapIsFilledWithAlignedRestsNotDottedOnes() {
        val rests = engrave("C4q rh.").rests

        assertEquals(
            listOf(
                EngravedRest(Staff.TREBLE, Q, NoteValue.QUARTER),
                EngravedRest(Staff.TREBLE, 2 * Q, NoteValue.HALF),
            ),
            rests,
        )
    }

    @Test
    fun aGapIsCutAtTheBarLine() {
        val rests = engrave("C4h. rq | rh C4h").rests

        assertEquals(
            listOf(
                EngravedRest(Staff.TREBLE, 3 * Q, NoteValue.QUARTER),
                EngravedRest(Staff.TREBLE, 4 * Q, NoteValue.HALF),
            ),
            rests,
        )
    }

    @Test
    fun anEighthGapGetsAnEighthRest() {
        val rests = engrave("C4e re D4q E4h").rests

        assertEquals(listOf(EngravedRest(Staff.TREBLE, Q / 2, NoteValue.EIGHTH)), rests)
    }

    @Test
    fun aGapShorterThanAnEighthGetsNoRest() {
        val sixteenth = Q / 4
        val steps =
            listOf(
                Step(0, setOf(ScoreNote(MidiNote(60), sixteenth))),
                Step(2 * sixteenth, setOf(ScoreNote(MidiNote(62), 4 * Q - 2 * sixteenth))),
            )

        val engraved = Engraving.engrave(passage(steps))

        assertTrue(engraved.rests.isEmpty())
        assertEquals(NoteValue.EIGHTH, engraved.chords[0].value)
    }

    @Test
    fun aNoteLengthWithoutAValueUsesTheLongestThatFits() {
        val steps = listOf(Step(0, setOf(ScoreNote(MidiNote(60), 2 * Q + 100))))

        assertEquals(
            NoteValue.HALF,
            Engraving
                .engrave(passage(steps))
                .chords
                .single()
                .value,
        )
        assertEquals(NoteValue.EIGHTH, NoteValue.fitting(1))
        assertNull(NoteValue.exact(2 * Q + 100))
        assertEquals(NoteValue.QUARTER, NoteValue.exact(Q))
    }

    @Test
    fun aNoteOverlappingTheNextOnsetLeavesNoRest() {
        val steps =
            listOf(
                Step(0, setOf(ScoreNote(MidiNote(60), 4 * Q))),
                Step(2 * Q, setOf(ScoreNote(MidiNote(62), 2 * Q))),
            )

        assertTrue(Engraving.engrave(passage(steps)).rests.isEmpty())
    }

    @Test
    fun aNoteHighOnTheStaffGetsADownStem() {
        assertEquals(StemDirection.DOWN, engrave("B4q rq rh").chords.single().stem)
    }

    @Test
    fun aNoteLowOnTheStaffGetsAnUpStem() {
        assertEquals(StemDirection.UP, engrave("A4q rq rh").chords.single().stem)
    }

    @Test
    fun aChordIsOneChordWithOneStemFromItsFarthestNote() {
        val chords = engrave("[C4 E4 G4]q rq rh").chords

        assertEquals(1, chords.size)
        assertEquals(listOf(60, 64, 67), chords.single().notes.map { it.pitch.number })
        assertEquals(StemDirection.UP, chords.single().stem)
    }

    @Test
    fun aChordWithEqualDistancesAboveAndBelowGetsADownStem() {
        assertEquals(StemDirection.DOWN, engrave("[A4 C5]q rq rh").chords.single().stem)
    }

    @Test
    fun aBeamGroupTakesItsStemDirectionFromItsFarthestNote() {
        val chords = engrave("G4e B5e rq rh").chords

        assertTrue(chords.all { it.stem == StemDirection.DOWN })
        assertEquals(chords[0].beamGroup, chords[1].beamGroup)
    }

    @Test
    fun aKeyOfGNeedsNoSignOnFSharpAndAnaturalOnF() {
        val engraved = engrave("F#4q F4q F4q rq | F4q rq rh", key = KeySignature.G)

        assertEquals(listOf(null, Accidental.NATURAL, null, Accidental.NATURAL), accidentals(engraved))
    }

    @Test
    fun aSharpOutsideTheKeyNeedsASignOnceAndAnotherOctaveNeedsItsOwn() {
        val engraved = engrave("F#4q F#4q F#5q F4q")

        assertEquals(listOf(Accidental.SHARP, null, Accidental.SHARP, Accidental.NATURAL), accidentals(engraved))
    }

    @Test
    fun aFlatKeyLowersItsLettersSoANaturalNeedsASign() {
        val engraved = engrave("B3q B3q A3q A#3q", key = KeySignature(-1))

        assertEquals(listOf(Accidental.NATURAL, null, null, Accidental.FLAT), accidentals(engraved))
    }

    @Test
    fun aGrandStaffPassageWithAnEmptyBassHasNoBassRests() {
        val engraved = engrave("C4q rq E4h | rw")

        assertTrue(engraved.chords.all { it.staff == Staff.TREBLE })
        assertTrue(engraved.rests.none { it.staff == Staff.BASS })
    }

    @Test
    fun aStepOnBothStavesYieldsOneChordPerStaff() {
        val engraved = engrave("[C3 E4]w")

        assertEquals(listOf(Staff.TREBLE, Staff.BASS), engraved.chords.map { it.staff })
        assertEquals(listOf(64), engraved.chords[0].notes.map { it.pitch.number })
        assertEquals(listOf(48), engraved.chords[1].notes.map { it.pitch.number })
        assertTrue(engraved.rests.isEmpty())
    }

    @Test
    fun bassNotesUseTheBassMiddleLine() {
        val chords = engrave("[F3 C4]q rq rh").chords

        assertEquals(StemDirection.DOWN, chords.single { it.staff == Staff.BASS }.stem)
        assertEquals(StemDirection.UP, chords.single { it.staff == Staff.TREBLE }.stem)
    }

    @Test
    fun theHandsTakeTurnsWithRestsInTheOtherStaff() {
        val engraved = engrave("C4h rh | rh C3h")

        assertEquals(
            listOf(
                EngravedRest(Staff.BASS, 0, NoteValue.WHOLE),
                EngravedRest(Staff.TREBLE, 2 * Q, NoteValue.HALF),
                EngravedRest(Staff.TREBLE, 4 * Q, NoteValue.WHOLE),
                EngravedRest(Staff.BASS, 4 * Q, NoteValue.HALF),
            ),
            engraved.rests,
        )
    }

    @Test
    fun chordsFromBothStavesShareBeamNumbersWithoutClashing() {
        val engraved = engrave("[C3 C4]e [D3 D4]e rq rh")

        assertEquals(
            2,
            engraved.chords
                .mapNotNull { it.beamGroup }
                .toSet()
                .size,
        )
    }

    @Test
    fun everyBuiltInPassageAndOneExercisePerSightLevelEngravesWithExactNoteValues() {
        val sight = SightLevels.all.map { SightReadingGenerator.passage(it.number, seed = 1) }

        (Curriculum.passages + sight).forEach { passage ->
            val lengths = passage.steps.flatMap { step -> step.notes.map { it.lengthTicks } }
            val inexact = lengths.filter { NoteValue.exact(it) == null }
            assertTrue(inexact.isEmpty(), "${passage.id} has lengths without a note value: $inexact")
            assertTrue(Engraving.engrave(passage).chords.isNotEmpty(), passage.id)
        }
    }

    private val bar = 4 * Q

    private fun pedalled(
        text: String,
        spans: List<PedalSpan>,
    ) = Engraving.engrave(passage(NoteText.parse(text, FOUR_FOUR)).copy(pedal = spans))

    @Test
    fun aPassageWithoutPedalMarksHasNoPedalLines() {
        val engraved = engrave("C4w")
        assertTrue(engraved.pedal.isEmpty())
        assertFalse(engraved.pedalEachBar)
    }

    @Test
    fun twoAdjacentBarSpansAreOneLineWithOneNotch() {
        val engraved = pedalled("C3w | D3w", listOf(PedalSpan(0, bar), PedalSpan(bar, 2 * bar)))
        assertEquals(listOf(EngravedPedalLine(0, 2 * bar, listOf(bar))), engraved.pedal)
        assertTrue(engraved.pedalEachBar)
        assertEquals(Staff.BASS, engraved.pedalStaff)
    }

    @Test
    fun spansWithAGapAreSeparateLinesAndNotEachBar() {
        val engraved = pedalled("C4w | D4w | E4w", listOf(PedalSpan(0, bar), PedalSpan(2 * bar, 3 * bar)))
        assertEquals(
            listOf(EngravedPedalLine(0, bar, emptyList()), EngravedPedalLine(2 * bar, 3 * bar, emptyList())),
            engraved.pedal,
        )
        assertFalse(engraved.pedalEachBar)
        assertEquals(Staff.TREBLE, engraved.pedalStaff)
    }

    private val sixEight = TimeSignature(6, 8)
    private val f = KeySignature.F

    @Test
    fun flatKeySpellsBlackKeysAsFlatsOfTheLetterAbove() {
        val engraved = engrave("A4q Bb4q B4q Eb4q", key = f)
        val d =
            engrave("C#4q rq rh", key = f)
                .chords
                .single()
                .notes
                .single()

        assertEquals(listOf(null, null, Accidental.NATURAL, Accidental.FLAT), accidentals(engraved))
        assertEquals(Accidental.FLAT, d.accidental)
        assertEquals(KeySignature(-1), f)
    }

    @Test
    fun flatKeyStemsFollowTheFlatSpelling() {
        // B-flat4 sits on the middle line (stem down); sharp spelling (A#4) sits below it (stem up).
        assertEquals(StemDirection.DOWN, engrave("Bb4q rq rh", key = f).chords.single().stem)
        assertEquals(StemDirection.UP, engrave("A#4q rq rh").chords.single().stem)
    }

    @Test
    fun sharpKeysAndCStaySharp() {
        assertEquals(
            Accidental.SHARP,
            engrave("C#4q rq rh")
                .chords
                .single()
                .notes
                .single()
                .accidental,
        )
        assertNull(
            engrave("F#4q rq rh", key = KeySignature.G)
                .chords
                .single()
                .notes
                .single()
                .accidental,
        )
    }

    @Test
    fun aFlatSignHoldsWithinTheBarAndResetsInTheNext() {
        val chords = engrave("Eb4q Eb4q E4q Eb4q | Eb4w", key = f).chords

        assertEquals(
            listOf(Accidental.FLAT, null, Accidental.NATURAL, Accidental.FLAT, Accidental.FLAT),
            chords.map { it.notes.single().accidental },
        )
    }

    @Test
    fun sixEightBeamsEighthsInThrees() {
        val chords = engrave("C4e D4e E4e F4e G4e A4e | C4q. C4q.", sixEight).chords

        assertEquals(
            1,
            chords
                .take(3)
                .map { it.beamGroup }
                .distinct()
                .size,
        )
        assertEquals(
            1,
            chords
                .subList(3, 6)
                .map { it.beamGroup }
                .distinct()
                .size,
        )
        assertNotEquals(chords[0].beamGroup, chords[3].beamGroup)
        assertNotEquals(null, chords[0].beamGroup)
        assertTrue(chords.none { it.flag })
    }

    @Test
    fun sixEightEighthQuarterEighthFormNoBeams() {
        val chords = engrave("C4e D4q E4e rq", sixEight).chords

        assertTrue(chords.take(3).all { it.beamGroup == null })
        assertTrue(chords[0].flag && chords[2].flag)
    }

    @Test
    fun sixEightRestsFollowTheDottedQuarterBeat() {
        assertEquals(
            listOf(EngravedRest(Staff.TREBLE, 0, NoteValue.DOTTED_QUARTER)),
            engrave("rq. C4e C4e C4e", sixEight).rests,
        )
        assertEquals(
            listOf(EngravedRest(Staff.TREBLE, 3 * Q / 2, NoteValue.DOTTED_QUARTER)),
            engrave("C4q. rq.", sixEight).rests,
        )
        assertEquals(
            listOf(EngravedRest(Staff.TREBLE, 0, NoteValue.WHOLE)),
            engrave("rq. rq. | C4q. C4q.", sixEight).rests,
        )
    }

    @Test
    fun sixEightRestsNeverCrossABeat() {
        // After an eighth, a quarter may not start mid-beat (it would cross it): two eighths, then a dotted quarter.
        assertEquals(
            listOf(
                EngravedRest(Staff.TREBLE, Q / 2, NoteValue.EIGHTH),
                EngravedRest(Staff.TREBLE, Q, NoteValue.EIGHTH),
                EngravedRest(Staff.TREBLE, 3 * Q / 2, NoteValue.DOTTED_QUARTER),
            ),
            engrave("C4e re re rq.", sixEight).rests,
        )
        assertEquals(
            listOf(EngravedRest(Staff.TREBLE, 0, NoteValue.QUARTER)),
            engrave("rq C4e C4e C4q", sixEight).rests,
        )
    }

    @Test
    fun nineEightAndTwelveEightAreCompoundButThreeEightIsNot() {
        val nine = engrave("C4e C4e C4e C4e C4e C4e C4e C4e C4e", TimeSignature(9, 8)).chords
        assertEquals(3, nine.map { it.beamGroup }.distinct().size)
        val twelve = engrave("rq. rq. rq. C4q.", TimeSignature(12, 8)).rests
        assertEquals(3, twelve.size)
        val threeEight = engrave("C4e C4e C4e", TimeSignature(3, 8)).chords
        assertEquals(threeEight[0].beamGroup, threeEight[1].beamGroup)
        assertNull(threeEight[2].beamGroup)
    }
}
