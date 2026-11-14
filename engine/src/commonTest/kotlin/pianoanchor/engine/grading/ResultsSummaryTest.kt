package pianoanchor.engine.grading

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.analysis.PerformanceAnalyser
import pianoanchor.engine.analysis.TempoReference
import pianoanchor.engine.content.Passages
import pianoanchor.testing.TEST_TEMPO_BPM
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val MS = 1_000_000L

class ResultsSummaryTest {
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)
    private val cde = passageOf(setOf(c4), setOf(d4), setOf(e4))

    private fun note(
        pitch: MidiNote,
        downMs: Long,
        lengthMs: Long = 900,
    ) = PlayedNote(pitch, 70, downMs * MS, (downMs + lengthMs) * MS, (downMs + lengthMs) * MS)

    private fun attempt(
        notes: List<PlayedNote>,
        tempoBpm: Int? = TEST_TEMPO_BPM,
        passage: Passage = cde,
    ) = Attempt(
        id = "a-1",
        passageId = passage.id,
        deviceId = "usb-1",
        startedAt = Instant.fromEpochSeconds(100),
        durationNanos = 4_000 * MS,
        stepCount = passage.steps.size,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = Band.CLEAN,
        events = emptyList(),
        tempoBpm = tempoBpm,
        playedNotes = notes,
    )

    private fun summarise(
        notes: List<PlayedNote>,
        passage: Passage = cde,
    ): ResultsSummary {
        val attempt = attempt(notes, passage = passage)
        val analysis =
            PerformanceAnalyser.analyse(passage, notes, TempoReference.Target(TEST_TEMPO_BPM, startNanos = 0))
        return ResultsSummary.of(passage, attempt, analysis)
    }

    private fun ref(
        step: Int,
        pitch: MidiNote,
    ) = ScoreNoteRef(step, pitch)

    @Test
    fun meanAbsoluteDeviationOverExactMatches() {
        val summary = summarise(listOf(note(c4, 0), note(d4, 1020), note(e4, 1960)))

        assertEquals(20, summary.timing?.meanAbsDeviationMs)
    }

    @Test
    fun offBeatTrueWithAnEarlyOrLateFinding() {
        assertTrue(summarise(listOf(note(c4, 0), note(d4, 1000), note(e4, 2500))).timing!!.offBeat)
        assertTrue(summarise(listOf(note(c4, 0), note(d4, 600), note(e4, 2000))).timing!!.offBeat)
        val tidy = summarise(listOf(note(c4, 0), note(d4, 1010), note(e4, 2000))).timing!!
        assertFalse(tidy.offBeat)
        assertNull(tidy.worst)
    }

    @Test
    fun worstTimingIsTheLargestDeviation() {
        val worst = summarise(listOf(note(c4, 0), note(d4, 700), note(e4, 2400))).timing!!.worst

        assertEquals(ErrorKind.LATE, worst?.kind)
        assertEquals(ref(2, e4), worst?.at)
        val early = summarise(listOf(note(c4, 0), note(d4, 600), note(e4, 2200))).timing!!.worst
        assertEquals(ErrorKind.EARLY, early?.kind)
        assertEquals(ref(1, d4), early?.at)
    }

    @Test
    fun heldRightPercentExcludesLengthFindings() {
        val summary = summarise(listOf(note(c4, 0), note(d4, 1000, lengthMs = 100), note(e4, 2000)))

        assertEquals(67, summary.length?.heldRightPercent)
        assertEquals(100, summarise(listOf(note(c4, 0), note(d4, 1000), note(e4, 2000))).length?.heldRightPercent)
    }

    @Test
    fun worstLengthByRatio() {
        val short = summarise(listOf(note(c4, 0, lengthMs = 100), note(d4, 1000, lengthMs = 2000), note(e4, 2000)))
        assertEquals(ErrorKind.TOO_SHORT, short.length?.worst?.kind)
        assertEquals(ref(0, c4), short.length?.worst?.at)

        val long = summarise(listOf(note(c4, 0, lengthMs = 400), note(d4, 1000, lengthMs = 5000), note(e4, 2000)))
        assertEquals(ErrorKind.TOO_LONG, long.length?.worst?.kind)
        assertEquals(ref(1, d4), long.length?.worst?.at)

        assertNull(summarise(listOf(note(c4, 0), note(d4, 1000), note(e4, 2000))).length?.worst)
    }

    @Test
    fun hesitationCountAndLongestGap() {
        val summary = summarise(listOf(note(c4, 0), note(d4, 1000), note(e4, 3500)))

        assertEquals(HesitationSummary(count = 1, longestMs = 2500, longestAt = ref(2, e4)), summary.hesitation)
        assertEquals(HesitationSummary(0, null, null), summarise(listOf(note(c4, 0), note(d4, 1000))).hesitation)
    }

    @Test
    fun aPauseAfterAMissedNoteHasNoKnownLength() {
        val summary = summarise(listOf(note(c4, 0), note(e4, 5000)))

        assertEquals(HesitationSummary(count = 1, longestMs = null, longestAt = null), summary.hesitation)
    }

    @Test
    fun aTakeWithNoExactMatchesHasNoTimingOrLength() {
        val summary = summarise(emptyList())

        assertNull(summary.timing)
        assertNull(summary.length)
        assertEquals(0, summary.correctPitches)
        assertEquals(3, summary.missedNotes)
    }

    @Test
    fun theLongestPauseIsRoundedToTheNearestMillisecond() {
        val late = PlayedNote(e4, 70, 3_500_600_000, 3_600_000_000, 3_600_000_000)
        val summary = summarise(listOf(note(c4, 0), note(d4, 1000), late))

        assertEquals(2501, summary.hesitation?.longestMs)
    }

    @Test
    fun timedPitchSlipsAreCountedFromTheAnalysis() {
        val chords = passageOf(setOf(c4, e4), setOf(d4), setOf(e4), setOf(c4))
        // Step 0 lacks its e4 (incomplete chord), d4 is played as g4 (wrong), e4 as e5 (octave), c4 is missed.
        val summary = summarise(listOf(note(c4, 0), note(g4, 1000), note(MidiNote(76), 2000)), chords)

        assertEquals(1, summary.incompleteChords)
        assertEquals(2, summary.wrongNotes)
        assertEquals(1, summary.missedNotes)
    }

    @Test
    fun waitModeSlipsComeFromTheAttempt() {
        val wait = attempt(emptyList(), tempoBpm = null).copy(wrongNotes = 2, incompleteChords = 1)
        val summary = ResultsSummary.of(cde, wait, analysis = null)

        assertEquals(2, summary.wrongNotes)
        assertEquals(1, summary.incompleteChords)
        assertEquals(0, summary.missedNotes)
    }

    @Test
    fun notesAreNumberedInScoreOrderAndNamedByValue() {
        val ticks = TICKS_PER_QUARTER
        val passage =
            Passage(
                id = "test:p",
                title = "p",
                steps =
                    listOf(
                        Step(0, setOf(ScoreNote(e4, ticks), ScoreNote(c4, 4 * ticks))),
                        Step(4 * ticks, setOf(ScoreNote(d4, ticks / 2))),
                    ),
                timeSignature = TimeSignature(4, 4),
                tempoBpm = 80,
            )

        assertEquals(1, noteNumber(passage, ref(0, c4)))
        assertEquals(2, noteNumber(passage, ref(0, e4)))
        assertEquals(3, noteNumber(passage, ref(1, d4)))
        assertEquals(NoteValue.WHOLE, noteValue(passage, ref(0, c4)))
        assertEquals(NoteValue.QUARTER, noteValue(passage, ref(0, e4)))
        assertEquals(NoteValue.HALF, noteValue(Passages.CDE, ref(2, e4)))
        assertNull(noteValue(passage, ref(1, d4)))
    }

    @Test
    fun aTimedAttemptWithAnUntimedAnalysisHasNoDeviationToAverage() {
        val notes = listOf(note(c4, 0), note(d4, 1000), note(e4, 2000))
        val analysis = PerformanceAnalyser.analyse(cde, notes, TempoReference.Untimed)

        assertEquals(0, ResultsSummary.of(cde, attempt(notes), analysis).timing?.meanAbsDeviationMs)
    }

    @Test
    fun waitModeAttemptHasNoTimingOrLength() {
        val f4 = MidiNote(65)
        val passage = passageOf(setOf(c4), setOf(d4), setOf(e4), setOf(f4), setOf(g4))
        val notes =
            listOf(note(c4, 0), note(d4, 1000), note(e4, 2000), note(f4, 8000), note(g4, 9000))
        val wait = attempt(notes, tempoBpm = null, passage = passage)
        val summary =
            ResultsSummary.of(passage, wait, PerformanceAnalyser.analyse(passage, notes, TempoReference.Untimed))

        assertNull(summary.timing)
        assertNull(summary.length)
        assertEquals(5, summary.correctPitches)
        assertEquals(5, summary.scoreNotes)
        assertEquals(1, summary.hesitation?.count)
    }

    @Test
    fun oldAttemptWithoutNotesHasNoHesitation() {
        val summary = ResultsSummary.of(cde, attempt(emptyList(), tempoBpm = null), analysis = null)

        assertEquals(ResultsSummary(3, 3, null, null, null), summary)
    }

    @Test
    fun timedPitchCountsNotesWithoutPitchFindings() {
        val chords = passageOf(setOf(c4, e4), setOf(d4), setOf(e4))
        val summary = summarise(listOf(note(c4, 0), note(g4, 1000), note(e4, 2000), note(e4, 0)), chords)

        assertEquals(4, summary.scoreNotes)
        assertEquals(3, summary.correctPitches)
        assertNotNull(summary.timing)
    }
}
