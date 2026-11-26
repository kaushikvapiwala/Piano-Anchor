package pianoanchor.engine.matcher

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.Strictness
import pianoanchor.engine.profile
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TempoJudgeTest {
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val ms = 1_000_000L
    private val start = 10_000 * ms

    // 60 BPM: one step per second; the tolerance is max(60 ms, 1/8 beat) = 125 ms.
    private val passage = passageOf(setOf(c4), setOf(d4), setOf(e4))
    private val judge = TempoJudge(passage, bpm = 60, startNanos = start)

    private fun on(
        note: MidiNote,
        atMs: Long,
    ) = NoteOn(start + atMs * ms, 0, note, 80)

    @Test
    fun onTimeNoteIsCorrect() {
        assertEquals(LiveVerdict.Correct(ScoreNoteRef(0, c4)), judge.onNoteOn(on(c4, 100)))
        assertEquals(LiveVerdict.Correct(ScoreNoteRef(1, d4)), judge.onNoteOn(on(d4, 875)))
    }

    @Test
    fun lateBeyondToleranceIsTimingOffWithPlusMs() {
        assertEquals(LiveVerdict.TimingOff(ScoreNoteRef(1, d4), 192), judge.onNoteOn(on(d4, 1_192)))
    }

    @Test
    fun earlyGivesMinusMs() {
        assertEquals(LiveVerdict.TimingOff(ScoreNoteRef(2, e4), -240), judge.onNoteOn(on(e4, 1_760)))
    }

    @Test
    fun wrongPitchIsWrong() {
        assertEquals(LiveVerdict.Wrong(MidiNote(61)), judge.onNoteOn(on(MidiNote(61), 0)))
    }

    @Test
    fun aNoteTooFarFromItsOnsetIsWrong() {
        // d4 is written at 1000 ms; half the gap to c4's step is 500 ms.
        assertEquals(LiveVerdict.Wrong(d4), judge.onNoteOn(on(d4, 400)))
        assertEquals(LiveVerdict.Wrong(e4), judge.onNoteOn(on(e4, 2_600)))
    }

    @Test
    fun aNoteAlreadyJudgedIsNotMatchedTwice() {
        assertEquals(LiveVerdict.Correct(ScoreNoteRef(0, c4)), judge.onNoteOn(on(c4, 0)))
        assertEquals(LiveVerdict.Wrong(c4), judge.onNoteOn(on(c4, 20)))
    }

    @Test
    fun theNearestUnjudgedStepOfARepeatedPitchWins() {
        val twice = TempoJudge(passageOf(setOf(c4), setOf(c4)), bpm = 60, startNanos = start)

        assertEquals(LiveVerdict.Correct(ScoreNoteRef(1, c4)), twice.onNoteOn(on(c4, 900)))
        assertEquals(LiveVerdict.Correct(ScoreNoteRef(0, c4)), twice.onNoteOn(on(c4, 100)))
    }

    @Test
    fun aLonePassageStepUsesOneBeatEitherSide() {
        val lone = TempoJudge(passageOf(setOf(c4)), bpm = 60, startNanos = start)

        assertEquals(LiveVerdict.TimingOff(ScoreNoteRef(0, c4), -400), lone.onNoteOn(on(c4, -400)))
        assertEquals(LiveVerdict.Wrong(c4), lone.onNoteOn(on(c4, 600)))
    }

    @Test
    fun aNoteWhoseWindowPassedUnplayedIsMissed() {
        // c4's window closes half a gap after its onset: 500 ms.
        assertNull(judge.missedNote(start + 500 * ms))
        assertEquals(ScoreNoteRef(0, c4), judge.missedNote(start + 501 * ms))
    }

    @Test
    fun aPlayedNoteIsNotMissedAndTheNextOneIs() {
        judge.onNoteOn(on(c4, 0))

        assertNull(judge.missedNote(start + 1_000 * ms))
        assertEquals(ScoreNoteRef(1, d4), judge.missedNote(start + 1_501 * ms))
    }

    @Test
    fun theLinkDelayShiftsWhenANoteCountsAsMissed() {
        val delayed = TempoJudge(passage, bpm = 60, startNanos = start, latencyOffsetNanos = 40 * ms)

        assertNull(delayed.missedNote(start + 540 * ms))
        assertEquals(ScoreNoteRef(0, c4), delayed.missedNote(start + 541 * ms))
    }

    @Test
    fun aChordNoteLeftOutIsMissedWhileItsPartnerIsPlayed() {
        val chord = TempoJudge(passageOf(setOf(e4, c4), setOf(d4)), bpm = 60, startNanos = start)
        chord.onNoteOn(on(e4, 0))

        assertEquals(ScoreNoteRef(0, c4), chord.missedNote(start + 600 * ms))
    }

    @Test
    fun keysBeforeTheFirstStepsWindowBelongToTheCountIn() {
        // c4's window opens half a gap (500 ms) before its onset.
        assertTrue(judge.beforeFirstWindow(start - 501 * ms))
        assertFalse(judge.beforeFirstWindow(start - 500 * ms))
        val delayed = TempoJudge(passage, bpm = 60, startNanos = start, latencyOffsetNanos = 40 * ms)
        assertTrue(delayed.beforeFirstWindow(start - 461 * ms))
        assertFalse(delayed.beforeFirstWindow(start - 460 * ms))
    }

    @Test
    fun cursorFollowsWrittenOnsets() {
        assertEquals(0, judge.cursorStep(start))
        assertEquals(0, judge.cursorStep(start + 999 * ms))
        assertEquals(1, judge.cursorStep(start + 1_000 * ms))
        assertEquals(2, judge.cursorStep(start + 2_500 * ms))
    }

    @Test
    fun cursorClampsBeforeStartAndAfterTheEnd() {
        assertEquals(0, judge.cursorStep(start - 5_000 * ms))
        assertEquals(2, judge.cursorStep(start + 99_000 * ms))
    }

    @Test
    fun endNanosIsOneBeatAfterTheLastNotesEnd() {
        // The last note starts at 2 s and lasts one beat (1 s); the run ends one beat later.
        assertEquals(start + 4_000 * ms, judge.endNanos)
    }

    @Test
    fun theLatencyOffsetIsSubtractedFromPlayedTimes() {
        val calibrated = TempoJudge(passage, bpm = 60, startNanos = start, latencyOffsetNanos = 200 * ms)
        // 200 ms late on the wire is on time; without the offset it is 200 ms late, past the 125 ms band.
        assertEquals(LiveVerdict.Correct(ScoreNoteRef(0, c4)), calibrated.onNoteOn(on(c4, 200)))
        assertEquals(LiveVerdict.TimingOff(ScoreNoteRef(0, c4), 200), judge.onNoteOn(on(c4, 200)))
        // A negative offset moves the other way.
        val early = TempoJudge(passage, bpm = 60, startNanos = start, latencyOffsetNanos = -300 * ms)
        assertEquals(LiveVerdict.TimingOff(ScoreNoteRef(0, c4), 400), early.onNoteOn(on(c4, 100)))
    }

    @Test
    fun theSameNoteGetsADifferentVerdictPerStrictness() {
        // 60 BPM: Gentle's band is 187.5 ms, Standard's 125 ms, Strict's 83 ms. A note 100 ms and one 150 ms late.
        fun verdict(
            strictness: Strictness,
            lateMs: Long,
        ) = TempoJudge(passage, bpm = 60, startNanos = start, strictness = strictness.profile())
            .onNoteOn(on(d4, 1_000 + lateMs))

        val onTime = LiveVerdict.Correct(ScoreNoteRef(1, d4))
        assertEquals(onTime, verdict(Strictness.GENTLE, 150))
        assertEquals(LiveVerdict.TimingOff(ScoreNoteRef(1, d4), 150), verdict(Strictness.STANDARD, 150))
        assertEquals(onTime, verdict(Strictness.STANDARD, 100))
        assertEquals(LiveVerdict.TimingOff(ScoreNoteRef(1, d4), 100), verdict(Strictness.STRICT, 100))
    }

    @Test
    fun theDefaultStrictnessIsStandard() {
        val standard = TempoJudge(passage, 60, start, strictness = Strictness.STANDARD.profile())
        assertEquals(standard.onNoteOn(on(d4, 1_150)), judge.onNoteOn(on(d4, 1_150)))
    }
}
