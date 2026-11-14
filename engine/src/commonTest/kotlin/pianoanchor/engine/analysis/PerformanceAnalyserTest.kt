package pianoanchor.engine.analysis

import pianoanchor.core.model.ErrorKind.EARLY
import pianoanchor.core.model.ErrorKind.EXTRA_NOTE
import pianoanchor.core.model.ErrorKind.HESITATION
import pianoanchor.core.model.ErrorKind.INCOMPLETE_CHORD
import pianoanchor.core.model.ErrorKind.LATE
import pianoanchor.core.model.ErrorKind.MISSED_NOTE
import pianoanchor.core.model.ErrorKind.MISTOUCH
import pianoanchor.core.model.ErrorKind.OCTAVE
import pianoanchor.core.model.ErrorKind.TOO_LONG
import pianoanchor.core.model.ErrorKind.TOO_SHORT
import pianoanchor.core.model.ErrorKind.WRONG_NOTE
import pianoanchor.core.model.Finding
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.analysis.TempoReference.Fitted
import pianoanchor.engine.analysis.TempoReference.Target
import pianoanchor.engine.analysis.TempoReference.Untimed
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MS = 1_000_000L

/** Passages from `passageOf` are quarter notes one beat (1000 ms at 60 BPM) apart. */
class PerformanceAnalyserTest {
    private val c4 = MidiNote(60)
    private val cSharp4 = MidiNote(61)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val f4 = MidiNote(65)
    private val g4 = MidiNote(67)
    private val a4 = MidiNote(69)
    private val cde = passageOf(setOf(c4), setOf(d4), setOf(e4))

    /** A note struck at [downMs] that sounds for [lengthMs] (900 ms: a clean quarter at 60 BPM). */
    private fun note(
        pitch: MidiNote,
        downMs: Long,
        lengthMs: Long = 900,
        velocity: Int = 70,
        soundingMs: Long = lengthMs,
    ) = PlayedNote(pitch, velocity, downMs * MS, (downMs + lengthMs) * MS, (downMs + soundingMs) * MS)

    private fun cleanCde() = listOf(note(c4, 0), note(d4, 1000), note(e4, 2000))

    private fun analyse(
        notes: List<PlayedNote>,
        passage: Passage = cde,
        tempo: TempoReference = Target(60),
    ) = PerformanceAnalyser.analyse(passage, notes, tempo)

    private fun kinds(
        notes: List<PlayedNote>,
        passage: Passage = cde,
        tempo: TempoReference = Target(60),
    ) = analyse(notes, passage, tempo).findings.map { it.kind to it.at }

    private fun at(
        step: Int,
        pitch: MidiNote,
    ) = ScoreNoteRef(step, pitch)

    /** One step per entry: (onset in quarters, length in quarters, pitch), at 60 BPM. */
    private fun passage(vararg steps: Triple<Int, Int, MidiNote>) =
        Passage(
            id = "test:lengths",
            title = "Lengths",
            steps =
                steps.map { (onset, length, pitch) ->
                    Step(
                        onset * TICKS_PER_QUARTER,
                        setOf(
                            ScoreNote(
                                pitch,
                                length * TICKS_PER_QUARTER,
                            ),
                        ),
                    )
                },
            timeSignature = TimeSignature(4, 4),
            tempoBpm = 60,
        )

    /** Equal steps of a quarter / [divisor] each, written at [bpm]. */
    private fun evenSteps(
        divisor: Int,
        bpm: Int,
        vararg pitches: MidiNote,
    ) = Passage(
        id = "test:even",
        title = "Even steps",
        steps =
            pitches.mapIndexed { i, pitch ->
                Step(
                    i * TICKS_PER_QUARTER / divisor,
                    setOf(
                        ScoreNote(
                            pitch,
                            TICKS_PER_QUARTER / divisor,
                        ),
                    ),
                )
            },
        timeSignature = TimeSignature(4, 4),
        tempoBpm = bpm,
    )

    @Test
    fun exactTakeHasNoFindings() {
        val analysis = analyse(cleanCde())
        assertEquals(emptyList(), analysis.findings)
        assertEquals(60.0, assertNotNull(analysis.fittedTempoBpm), 1e-9)
    }

    @Test
    fun notesAreTakenInOnsetOrderWhateverTheInputOrder() {
        assertEquals(emptyList(), analyse(cleanCde().reversed()).findings)
    }

    @Test
    fun octaveShiftIsOctaveNotWrongNote() {
        val take = listOf(note(c4, 0), note(MidiNote(74), 1000), note(MidiNote(40), 2000))
        assertEquals(listOf(OCTAVE to at(1, d4), OCTAVE to at(2, e4)), kinds(take))
    }

    @Test
    fun otherWrongPitchIsWrongNote() {
        val take = listOf(note(c4, 0), note(g4, 1000), note(e4, 2000))
        assertEquals(listOf(WRONG_NOTE to at(1, d4)), kinds(take))
    }

    @Test
    fun droppedNoteIsMissedNote() {
        val analysis = analyse(listOf(note(c4, 0), note(e4, 2000)))
        assertEquals(listOf(Finding(MISSED_NOTE, at(1, d4), null)), analysis.findings)
    }

    @Test
    fun oneNoteMissingFromAChordIsIncompleteChord() {
        val passage = passageOf(setOf(c4, e4, g4), setOf(d4))
        val take = listOf(note(c4, 0), note(g4, 5), note(d4, 1000))
        assertEquals(listOf(INCOMPLETE_CHORD to at(0, e4)), kinds(take, passage))
    }

    @Test
    fun chordNotesMayComeInAnyOrder() {
        val passage = passageOf(setOf(c4, e4, g4), setOf(d4))
        val take = listOf(note(g4, 0), note(c4, 4), note(e4, 8), note(d4, 1000))
        assertEquals(emptyList(), kinds(take, passage))
    }

    @Test
    fun extraNotePointsAtTheNearestScoreNote() {
        val take = cleanCde() + note(a4, 1300, lengthMs = 100) + note(a4, 2700, lengthMs = 100, velocity = 30)
        assertEquals(listOf(EXTRA_NOTE to at(1, d4), EXTRA_NOTE to at(2, e4)), kinds(take))
    }

    @Test
    fun extraNoteNearAChordPointsAtItsLowestPitch() {
        val passage = passageOf(setOf(c4, e4, g4), setOf(d4))
        val take =
            listOf(note(c4, 0), note(e4, 0), note(g4, 0), note(MidiNote(80), 200, lengthMs = 100), note(d4, 1000))
        assertEquals(listOf(EXTRA_NOTE to at(0, c4)), kinds(take, passage))
    }

    @Test
    fun extraNoteFindsItsStepWhenTheFirstStepIsMissed() {
        // C is missed, so its time is placed a beat before D's.
        val take = listOf(note(d4, 1000), note(e4, 2000), note(a4, 2600, lengthMs = 100))
        assertEquals(listOf(MISSED_NOTE to at(0, c4), EXTRA_NOTE to at(2, e4)), kinds(take))
    }

    @Test
    fun extraNoteInAOneStepPassagePointsAtThatStep() {
        val take = listOf(note(c4, 0), note(a4, 500, lengthMs = 100))
        assertEquals(listOf(EXTRA_NOTE to at(0, c4)), kinds(take, passageOf(setOf(c4))))
    }

    @Test
    fun softerNeighbourWithin40msIsMistouch() {
        val touch = note(cSharp4, 20, lengthMs = 50, velocity = 30)
        val analysis = analyse(cleanCde() + touch)
        assertEquals(listOf(Finding(MISTOUCH, at(0, c4), touch)), analysis.findings)
    }

    @Test
    fun mistouchPointsAtTheLouderNoteNearestInTime() {
        val passage = passageOf(setOf(c4, e4), setOf(g4))
        val take = listOf(note(c4, 0), note(e4, 15), note(d4, 30, lengthMs = 50, velocity = 20), note(g4, 1000))
        assertEquals(listOf(MISTOUCH to at(0, e4)), kinds(take, passage))
    }

    @Test
    fun mistouchThatTookAScoreNoteInTheFirstPassFreesIt() {
        // Played D is the score's next note, but it comes 20 ms after a louder C: a brushed key, not D played early.
        val take = listOf(note(c4, 0), note(d4, 20, lengthMs = 50, velocity = 20), note(e4, 2000))
        assertEquals(listOf(MISTOUCH to at(0, c4), MISSED_NOTE to at(1, d4)), kinds(take))
    }

    @Test
    fun softerSecondInAChordIsNotAMistouch() {
        val passage = passageOf(setOf(c4, d4), setOf(e4))
        val take = listOf(note(c4, 0, velocity = 80), note(d4, 10, velocity = 40), note(e4, 1000))
        assertEquals(emptyList(), kinds(take, passage))
    }

    @Test
    fun fastRunOnTimeIsNotAMistouch() {
        // Thirty-second notes at 300 BPM are 25 ms apart: each softer neighbour is the next step, on time.
        val run = evenSteps(8, 300, c4, d4, e4)
        val take =
            listOf(note(c4, 0, 20, velocity = 80), note(d4, 25, 20, velocity = 40), note(e4, 50, 20, velocity = 60))
        assertEquals(emptyList(), kinds(take, run, Target(300)))
    }

    @Test
    fun louderNeighbourIsNotMistouch() {
        val take = cleanCde() + note(cSharp4, 20, lengthMs = 50, velocity = 100)
        assertEquals(listOf(EXTRA_NOTE to at(0, c4)), kinds(take))
    }

    @Test
    fun neighbourOutsideTheWindowIsNotMistouch() {
        val take = cleanCde() + note(cSharp4, 60, lengthMs = 50, velocity = 30)
        assertEquals(listOf(EXTRA_NOTE to at(0, c4)), kinds(take))
    }

    @Test
    fun lateBeyondToleranceIsLate() {
        val take = listOf(note(c4, 0), note(d4, 1200), note(e4, 1800))
        assertEquals(listOf(LATE to at(1, d4), EARLY to at(2, e4)), kinds(take))
    }

    @Test
    fun insideToleranceIsNot() {
        val take = listOf(note(c4, 0), note(d4, 1100), note(e4, 1900))
        assertEquals(emptyList(), kinds(take))
    }

    @Test
    fun toleranceUsesTheBeatFractionAtSlowTempo() {
        // 100 ms late: inside an eighth of a 1000 ms beat (125 ms), outside the 60 ms floor at 240 BPM (beat 250 ms).
        assertEquals(emptyList(), kinds(listOf(note(c4, 0), note(d4, 1100), note(e4, 2000))))
        val fast = listOf(note(c4, 0, lengthMs = 200), note(d4, 350, lengthMs = 200), note(e4, 500, lengthMs = 200))
        assertEquals(listOf(LATE to at(1, d4)), kinds(fast, tempo = Target(240)))
    }

    @Test
    fun oneLongPauseIsASingleHesitationAndLaterNotesAreOnTime() {
        val passage = passageOf(setOf(c4), setOf(d4), setOf(e4), setOf(f4))
        val take = listOf(note(c4, 0), note(d4, 1000), note(e4, 3500), note(f4, 4500))
        val analysis = analyse(take, passage)
        assertEquals(listOf(Finding(HESITATION, at(2, e4), take[2])), analysis.findings)
        assertEquals(listOf(0L, 0L, 0L, 0L), analysis.matches.map { it.onsetDeviationNanos })
    }

    @Test
    fun hesitationAfterAMissedStepIsMeasuredFromTheLastPlayedStep() {
        val passage = passageOf(setOf(c4), setOf(d4), setOf(e4), setOf(f4))
        val take = listOf(note(c4, 0), note(e4, 4000), note(f4, 5000))
        assertEquals(listOf(MISSED_NOTE to at(1, d4), HESITATION to at(2, e4)), kinds(take, passage))
    }

    @Test
    fun hesitationPointsAtTheLowestPlayedNoteOfAChord() {
        val passage = passageOf(setOf(d4), setOf(c4, e4, g4))
        val take = listOf(note(d4, 0), note(e4, 3000), note(g4, 3000))
        assertEquals(listOf(INCOMPLETE_CHORD to at(1, c4), HESITATION to at(1, e4)), kinds(take, passage))
    }

    @Test
    fun shortPauseAtAFastTempoIsNotAHesitation() {
        // At 240 BPM an eighth is 125 ms: a 300 ms gap is over twice that but under the 400 ms
        // floor, so D is late (and E after it), not a hesitation.
        val eighths = evenSteps(2, 240, c4, d4, e4)
        val take = listOf(note(c4, 0, lengthMs = 100), note(d4, 300, lengthMs = 100), note(e4, 375, lengthMs = 100))
        assertEquals(listOf(LATE to at(1, d4), LATE to at(2, e4)), kinds(take, eighths, Target(240)))
    }

    @Test
    fun untimedReportsPitchAndHesitationOnly() {
        val passage = passageOf(setOf(c4), setOf(d4), setOf(e4), setOf(f4), setOf(g4), setOf(a4))
        val take =
            listOf(
                note(c4, 0, lengthMs = 100),
                note(d4, 1300),
                note(e4, 2000, lengthMs = 3000),
                note(f4, 8000),
                note(MidiNote(79), 9000),
                note(a4, 10000),
            )
        assertEquals(listOf(HESITATION to at(3, f4), OCTAVE to at(4, g4)), kinds(take, passage, Untimed))
        assertTrue(analyse(take, passage, Untimed).matches.all { it.onsetDeviationNanos == null })
    }

    @Test
    fun untimedWithoutAFittedTempoReportsNoHesitation() {
        val take = listOf(note(c4, 0), note(MidiNote(74), 5000))
        assertEquals(listOf(OCTAVE to at(1, d4), MISSED_NOTE to at(2, e4)), kinds(take, tempo = Untimed))
    }

    @Test
    fun targetMeasuresAgainstTheTargetTempo() {
        val take = listOf(note(c4, 0, lengthMs = 450), note(d4, 500, lengthMs = 450), note(e4, 1000, lengthMs = 450))
        assertEquals(emptyList(), kinds(take, tempo = Target(120)))
        assertEquals(
            listOf(EARLY to at(1, d4), EARLY to at(2, e4), TOO_SHORT to at(2, e4)),
            kinds(take, tempo = Target(60)),
        )
    }

    @Test
    fun targetWithStartNanosReportsALateFirstNote() {
        val take = listOf(note(c4, 300), note(d4, 1000), note(e4, 2000))
        assertEquals(emptyList(), kinds(take, tempo = Target(60)).filter { it.second == at(0, c4) })
        assertEquals(listOf(LATE to at(0, c4)), kinds(take, tempo = Target(60, startNanos = 0)))
    }

    @Test
    fun targetTempoOutsideTheWrittenRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> { Target(19) }
        assertFailsWith<IllegalArgumentException> { Target(301) }
    }

    @Test
    fun fittedMeasuresAgainstTheTakesOwnTempo() {
        val steady = listOf(note(c4, 0, lengthMs = 450), note(d4, 500, lengthMs = 450), note(e4, 1000, lengthMs = 450))
        val analysis = analyse(steady, tempo = Fitted)
        assertEquals(emptyList(), analysis.findings)
        assertEquals(120.0, assertNotNull(analysis.fittedTempoBpm), 1e-9)
        val passage = passageOf(setOf(c4), setOf(d4), setOf(e4), setOf(f4), setOf(g4))
        val dragged = listOf(note(c4, 0), note(d4, 1000), note(e4, 2400), note(f4, 3000), note(g4, 4000))
        assertEquals(listOf(LATE to at(2, e4)), kinds(dragged, passage, Fitted))
    }

    @Test
    fun fittedWithoutATempoJudgesPitchOnly() {
        val take = listOf(note(c4, 0, lengthMs = 50), note(MidiNote(74), 1000))
        assertEquals(listOf(OCTAVE to at(1, d4), MISSED_NOTE to at(2, e4)), kinds(take, tempo = Fitted))
    }

    @Test
    fun pedalHeldNoteIsNotTooShort() {
        val pedalled = listOf(note(c4, 0, lengthMs = 200, soundingMs = 900), note(d4, 1000), note(e4, 2000))
        assertEquals(emptyList(), kinds(pedalled))
        val clipped = listOf(note(c4, 0, lengthMs = 200), note(d4, 1000), note(e4, 2000))
        assertEquals(listOf(TOO_SHORT to at(0, c4)), kinds(clipped))
    }

    @Test
    fun legatoReleaseBeforeTheNextOnsetIsNotTooShort() {
        // Sixteenths at 250 BPM are written 60 ms long: a lift 40 ms before the next note (inside the 60 ms tolerance)
        // is legato, though the note sounded only a third of its length.
        val sixteenths = evenSteps(4, 250, c4, d4)
        val take = listOf(note(c4, 0, lengthMs = 20), note(d4, 60, lengthMs = 50))
        assertEquals(emptyList(), kinds(take, sixteenths, Target(250)))
    }

    @Test
    fun noteWrittenPastTheNextStepIsTooShortWhenLiftedThere() {
        // A half note under a melody that moves on after one beat: lifting it at the next onset clips it.
        val passage = passage(Triple(0, 2, c4), Triple(1, 1, d4))
        assertEquals(
            listOf(TOO_SHORT to at(0, c4)),
            kinds(listOf(note(c4, 0, lengthMs = 950), note(d4, 1000)), passage),
        )
    }

    @Test
    fun held2xIsTooLong() {
        val take = listOf(note(c4, 0, lengthMs = 2000), note(d4, 1000), note(e4, 2000))
        assertEquals(listOf(TOO_LONG to at(0, c4)), kinds(take))
    }

    @Test
    fun findingsAreInScoreOrder() {
        val take =
            listOf(note(a4, 50, lengthMs = 100), note(c4, 0), note(d4, 1300, lengthMs = 100), note(MidiNote(76), 2000))
        assertEquals(
            listOf(EXTRA_NOTE to at(0, c4), LATE to at(1, d4), TOO_SHORT to at(1, d4), OCTAVE to at(2, e4)),
            kinds(take),
        )
    }

    @Test
    fun fittedTempoBpmIsNullWithFewerThanTwoMatches() {
        assertNull(analyse(listOf(note(c4, 0))).fittedTempoBpm)
        assertNull(analyse(listOf(note(c4, 0), note(e4, 0)), passageOf(setOf(c4, e4))).fittedTempoBpm)
    }

    @Test
    fun emptyTakeGivesMissedNoteForEveryScoreNote() {
        val passage = passageOf(setOf(c4, e4), setOf(d4))
        assertEquals(
            listOf(MISSED_NOTE to at(0, c4), MISSED_NOTE to at(0, e4), MISSED_NOTE to at(1, d4)),
            kinds(emptyList(), passage),
        )
    }

    @Test
    fun matchesListEveryScoreNoteWithDeviationAndGap() {
        val take = listOf(note(c4, 0), note(d4, 1100), note(e4, 1950))
        val analysis = analyse(take, tempo = Target(60, startNanos = -20 * MS))
        assertEquals(
            listOf(
                NoteMatch(at(0, c4), take[0], 20 * MS, null),
                NoteMatch(at(1, d4), take[1], 120 * MS, 1100 * MS),
                NoteMatch(at(2, e4), take[2], -30 * MS, 850 * MS),
            ),
            analysis.matches,
        )
    }

    @Test
    fun missedNotesMatchHasNoPlayedNote() {
        val analysis = analyse(listOf(note(c4, 0), note(e4, 2000)))
        assertEquals(
            listOf(
                NoteMatch(at(0, c4), note(c4, 0), 0, null),
                NoteMatch(at(1, d4), null, null, null),
                NoteMatch(at(2, e4), note(e4, 2000), 0, null),
            ),
            analysis.matches,
        )
    }

    @Test
    fun repeatedPitchMissIsPlacedByTiming() {
        val passage = passageOf(setOf(c4), setOf(c4), setOf(c4))
        val take = listOf(note(c4, 0), note(c4, 2000))
        assertEquals(listOf(MISSED_NOTE to at(1, c4)), kinds(take, passage))
    }

    @Test
    fun workedExampleFromTheAlgorithmDoc() {
        // docs/algorithms/performance-analysis.md, "Worked example".
        val passage = passageOf(setOf(c4), setOf(d4), setOf(e4), setOf(f4))
        val take =
            listOf(
                note(c4, 0),
                note(cSharp4, 20, lengthMs = 50, velocity = 30),
                note(d4, 1200),
                note(e4, 4000),
                note(f4, 5000, lengthMs = 300),
            )
        assertEquals(
            listOf(MISTOUCH to at(0, c4), LATE to at(1, d4), HESITATION to at(2, e4), TOO_SHORT to at(3, f4)),
            kinds(take, passage),
        )
    }
}
