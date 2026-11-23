package pianoanchor.engine.grading

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.analysis.PerformanceAnalyser
import pianoanchor.engine.analysis.TempoReference
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val MS = 1_000_000L

class ResultsWordsTest {
    private val at = ScoreNoteRef(0, MidiNote(60))

    private fun summary(
        wrong: Int = 0,
        missed: Int = 0,
        incomplete: Int = 0,
        timing: TimingSummary? = TimingSummary(10, false, null, 0),
        length: LengthSummary? = LengthSummary(100, null),
        pauses: Int = 0,
    ) = ResultsSummary(
        correctPitches = 3,
        scoreNotes = 3,
        timing = timing,
        length = length,
        hesitation = HesitationSummary(pauses, null, null),
        wrongNotes = wrong,
        missedNotes = missed,
        incompleteChords = incomplete,
    )

    private fun shortFinding(kind: ErrorKind) = Finding(kind, at, PlayedNote(MidiNote(60), 70, 0, MS, MS))

    @Test
    fun theOwnersShiftedRunSaysStartedLateAndNotesShort() {
        val cde = passageOf(setOf(MidiNote(60)), setOf(MidiNote(62)), setOf(MidiNote(64)))
        val notes =
            listOf(60, 62, 64).mapIndexed { i, p ->
                val down = (1700 + i * 1250) * MS
                PlayedNote(MidiNote(p), 70, down, down + 300 * MS, down + 300 * MS)
            }
        val attempt =
            Attempt(
                id = "a",
                passageId = cde.id,
                deviceId = "d",
                startedAt = Instant.fromEpochSeconds(1),
                durationNanos = 4_000 * MS,
                stepCount = 3,
                wrongNotes = 0,
                incompleteChords = 0,
                accuracy = 1.0,
                band = Band.GOOD,
                events = emptyList(),
                tempoBpm = 48,
                playedNotes = notes,
            )
        val analysis = PerformanceAnalyser.analyse(cde, notes, TempoReference.Target(48, startNanos = 0))
        val summary = ResultsSummary.of(cde, attempt, analysis)

        val words = ResultsWordsRule.of(summary, 48, Band.GOOD)

        assertEquals(listOf(Observation.STARTED_LATE, Observation.NOTES_SHORT), words.observations)
        assertEquals(Tip.WAIT_FOR_COUNT_IN, words.tip)
        assertEquals(1.5, ResultsWordsRule.shiftBeats(summary.timing!!.medianSignedDeviationMs, 48))
    }

    @Test
    fun shiftRoundsToTheNearestHalfBeat() {
        assertEquals(1.5, ResultsWordsRule.shiftBeats(1700, 48))
        assertEquals(1.0, ResultsWordsRule.shiftBeats(-1250, 48))
        assertEquals(0.5, ResultsWordsRule.shiftBeats(700, 60))
    }

    @Test
    fun anEarlyShiftSaysStartedEarly() {
        val words = ResultsWordsRule.of(summary(timing = TimingSummary(900, true, null, -900)), 60, Band.GOOD)

        assertEquals(listOf(Observation.STARTED_EARLY), words.observations)
        assertEquals(Tip.WAIT_FOR_COUNT_IN, words.tip)
    }

    @Test
    fun wrongNotesOnlyGivesSlowDown() {
        val words = ResultsWordsRule.of(summary(wrong = 1), 60, Band.GOOD)

        assertEquals(listOf(Observation.WRONG_NOTES), words.observations)
        assertEquals(Tip.SLOW_DOWN, words.tip)
        assertEquals(
            listOf(Observation.WRONG_NOTES),
            ResultsWordsRule.of(summary(missed = 1), 60, Band.GOOD).observations,
        )
        assertEquals(
            listOf(Observation.WRONG_NOTES),
            ResultsWordsRule.of(summary(incomplete = 1), 60, Band.GOOD).observations,
        )
    }

    @Test
    fun threeApplicableObservationsKeepTheFirstTwo() {
        val words =
            ResultsWordsRule.of(
                summary(wrong = 1, length = LengthSummary(50, shortFinding(ErrorKind.TOO_SHORT)), pauses = 1),
                60,
                Band.GOOD,
            )

        assertEquals(listOf(Observation.WRONG_NOTES, Observation.NOTES_SHORT), words.observations)
        assertEquals(Tip.SLOW_DOWN, words.tip)
    }

    @Test
    fun longNotesGiveLetGoSooner() {
        val words =
            ResultsWordsRule.of(summary(length = LengthSummary(50, shortFinding(ErrorKind.TOO_LONG))), 60, Band.GOOD)

        assertEquals(listOf(Observation.NOTES_LONG), words.observations)
        assertEquals(Tip.LET_GO_SOONER, words.tip)
        val short = ResultsWordsRule.of(summary(length = LengthSummary(50, null)), 60, Band.GOOD)
        assertEquals(Tip.HOLD_FULL_CLICKS, short.tip)
    }

    @Test
    fun offBeatAndPausedFollowAndOffBeatNeedsNoShift() {
        val off = TimingSummary(80, true, shortFinding(ErrorKind.LATE), 30)
        val words = ResultsWordsRule.of(summary(timing = off, pauses = 1), 60, Band.GOOD)

        assertEquals(listOf(Observation.OFF_BEAT, Observation.PAUSED), words.observations)
        assertEquals(Tip.LISTEN_TO_CLICK, words.tip)
        assertEquals(Tip.PRACTISE_THE_PAUSE, ResultsWordsRule.of(summary(pauses = 1), 60, Band.GOOD).tip)
    }

    @Test
    fun offBeatIsSuppressedWhenTheRunStartedLateOrEarly() {
        val shifted = TimingSummary(1700, true, shortFinding(ErrorKind.LATE), 1700)

        val words = ResultsWordsRule.of(summary(timing = shifted, pauses = 1), 48, Band.GOOD)

        assertEquals(listOf(Observation.STARTED_LATE, Observation.PAUSED), words.observations)
    }

    @Test
    fun aCleanBandIsShownAlone() {
        val words = ResultsWordsRule.of(summary(wrong = 1), 60, Band.CLEAN)

        assertEquals(listOf(Observation.CLEAN), words.observations)
        assertEquals(Tip.NEXT_TEMPO, words.tip)
        assertEquals(listOf(Observation.CLEAN), ResultsWordsRule.of(summary(), 60, Band.GOOD).observations)
    }

    @Test
    fun waitModeNeverGivesTimingObservations() {
        val timing = TimingSummary(2000, true, null, 2000)
        val length = LengthSummary(0, null)

        val words = ResultsWordsRule.of(summary(timing = timing, length = length, pauses = 1), null, Band.GOOD)

        assertEquals(listOf(Observation.PAUSED), words.observations)
        assertEquals(
            listOf(Observation.WRONG_NOTES, Observation.PAUSED),
            ResultsWordsRule.of(summary(wrong = 2, pauses = 1), null, Band.GOOD).observations,
        )
    }

    @Test
    fun aRunWithNoTimingOrLengthOrHesitationSaysClean() {
        val bare = ResultsSummary(3, 3, null, null, null)

        assertEquals(listOf(Observation.CLEAN), ResultsWordsRule.of(bare, 60, Band.GOOD).observations)
    }
}
