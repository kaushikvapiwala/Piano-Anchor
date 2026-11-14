package pianoanchor.engine.grading

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.engine.analysis.NoteMatch
import pianoanchor.engine.analysis.PerformanceAnalysis
import kotlin.math.abs
import kotlin.math.roundToInt

private const val NANOS_PER_MILLI = 1_000_000L
private const val NANOS_PER_MINUTE = 60_000_000_000.0
private const val PERCENT = 100

/** Findings that spoil a score note's pitch (extra and brushed keys belong to no score note). */
private val PITCH_KINDS =
    setOf(ErrorKind.WRONG_NOTE, ErrorKind.MISSED_NOTE, ErrorKind.INCOMPLETE_CHORD, ErrorKind.OCTAVE)
private val OFF_BEAT_KINDS = setOf(ErrorKind.EARLY, ErrorKind.LATE)
private val LENGTH_KINDS = setOf(ErrorKind.TOO_SHORT, ErrorKind.TOO_LONG)

/**
 * Timing of a timed run: the mean absolute onset deviation of the exact-pitch notes in whole milliseconds, whether any
 * note was early or late, and the [worst] early/late finding (largest deviation), null when none.
 */
public data class TimingSummary(
    val meanAbsDeviationMs: Int,
    val offBeat: Boolean,
    val worst: Finding?,
)

/** Note length of a timed run: the share of exact-pitch notes held right, and the [worst] length finding by ratio. */
public data class LengthSummary(
    val heldRightPercent: Int,
    val worst: Finding?,
)

/** Pauses of a run: how many, and the longest one's ms and the score note it came before (null if unknown). */
public data class HesitationSummary(
    val count: Int,
    val longestMs: Int?,
    val longestAt: ScoreNoteRef?,
)

/**
 * What the Results tiles show (`docs/algorithms/grading.md`): [correctPitches] of [scoreNotes], and the [timing] and
 * [length] of a timed run (null for wait mode, or when no note had its exact pitch), and the [hesitation] (null when
 * the attempt has no played notes). [wrongNotes], [missedNotes] and [incompleteChords] explain what is not correct: a
 * timed run counts score notes and chord steps, wait mode takes the attempt's counts.
 */
public data class ResultsSummary(
    val correctPitches: Int,
    val scoreNotes: Int,
    val timing: TimingSummary?,
    val length: LengthSummary?,
    val hesitation: HesitationSummary?,
    val wrongNotes: Int = 0,
    val missedNotes: Int = 0,
    val incompleteChords: Int = 0,
) {
    public companion object {
        /**
         * Summarises [attempt] of [passage] from [analysis], null when the attempt was not analysed. A timed attempt
         * (tempo set) with an analysis counts pitch by score notes and gets timing and length; a wait-mode attempt
         * counts its steps, all correct.
         */
        public fun of(
            passage: Passage,
            attempt: Attempt,
            analysis: PerformanceAnalysis?,
        ): ResultsSummary {
            val hesitation = analysis?.let(::hesitation)
            val tempoBpm = attempt.tempoBpm
            if (analysis == null || tempoBpm == null) {
                return ResultsSummary(
                    correctPitches = attempt.stepCount,
                    scoreNotes = attempt.stepCount,
                    timing = null,
                    length = null,
                    hesitation = hesitation,
                    wrongNotes = attempt.wrongNotes,
                    incompleteChords = attempt.incompleteChords,
                )
            }
            val scoreNotes = passage.steps.sumOf { it.notes.size }
            val spoiled = analysis.findings.filter { it.kind in PITCH_KINDS }.mapTo(HashSet()) { it.at }
            val exact = exactMatches(analysis)
            return ResultsSummary(
                correctPitches = scoreNotes - spoiled.size,
                scoreNotes = scoreNotes,
                timing = if (exact.isEmpty()) null else timing(analysis, exact),
                length = if (exact.isEmpty()) null else length(passage, tempoBpm, analysis, exact),
                hesitation = hesitation,
                wrongNotes = refs(analysis, ErrorKind.WRONG_NOTE, ErrorKind.OCTAVE).size,
                missedNotes = refs(analysis, ErrorKind.MISSED_NOTE).size,
                incompleteChords = refs(analysis, ErrorKind.INCOMPLETE_CHORD).mapTo(HashSet()) { it.stepIndex }.size,
            )
        }

        /** The distinct score notes with a finding of one of [kinds]. */
        private fun refs(
            analysis: PerformanceAnalysis,
            vararg kinds: ErrorKind,
        ): Set<ScoreNoteRef> = analysis.findings.filter { it.kind in kinds }.mapTo(HashSet()) { it.at }

        /** The matches whose played key is the score note's pitch, each with its played note. */
        private fun exactMatches(analysis: PerformanceAnalysis): List<Pair<NoteMatch, PlayedNote>> =
            analysis.matches.mapNotNull { m -> m.played?.takeIf { it.pitch == m.at.pitch }?.let { m to it } }

        private fun timing(
            analysis: PerformanceAnalysis,
            exact: List<Pair<NoteMatch, PlayedNote>>,
        ): TimingSummary {
            // An untimed analysis has no deviations: nothing to average.
            val deviations =
                exact
                    .mapNotNull { (m, _) -> m.onsetDeviationNanos?.let { m.at to abs(it) } }
                    .toMap()
            val absolute = deviations.values
            val mean = if (absolute.isEmpty()) 0.0 else absolute.average() / NANOS_PER_MILLI
            val offBeat = analysis.findings.filter { it.kind in OFF_BEAT_KINDS }
            return TimingSummary(
                meanAbsDeviationMs = mean.roundToInt(),
                offBeat = offBeat.isNotEmpty(),
                worst = offBeat.maxByOrNull { deviations.getValue(it.at) },
            )
        }

        private fun length(
            passage: Passage,
            tempoBpm: Int,
            analysis: PerformanceAnalysis,
            exact: List<Pair<NoteMatch, PlayedNote>>,
        ): LengthSummary {
            val faults = analysis.findings.filter { it.kind in LENGTH_KINDS }
            val right = exact.size - faults.map { it.at }.toSet().size
            val held = exact.associate { (m, played) -> m.at to (played.upNanos - played.downNanos).toDouble() }
            val percent = (right * PERCENT.toDouble() / exact.size).roundToInt()
            return LengthSummary(percent, faults.maxByOrNull { badness(passage, tempoBpm, it, held.getValue(it.at)) })
        }

        /** How far a length finding is from the written length: held ÷ written when long, written ÷ held when short. */
        private fun badness(
            passage: Passage,
            tempoBpm: Int,
            finding: Finding,
            held: Double,
        ): Double {
            val written = lengthTicks(passage, finding.at) * NANOS_PER_MINUTE / tempoBpm / TICKS_PER_QUARTER
            return if (finding.kind == ErrorKind.TOO_SHORT) written / held else held / written
        }

        private fun hesitation(analysis: PerformanceAnalysis): HesitationSummary {
            val pauses = analysis.findings.filter { it.kind == ErrorKind.HESITATION }
            val gaps = analysis.matches.associate { it.at to it.gapBeforeNanos }
            val longest = pauses.mapNotNull { f -> gaps[f.at]?.let { f.at to it } }.maxByOrNull { it.second }
            return HesitationSummary(
                count = pauses.size,
                longestMs = longest?.let { (it.second.toDouble() / NANOS_PER_MILLI).roundToInt() },
                longestAt = longest?.first,
            )
        }
    }
}
