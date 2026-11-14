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
 * [length] of a timed run (null for wait mode), and the [hesitation] (null when the attempt has no played notes).
 */
public data class ResultsSummary(
    val correctPitches: Int,
    val scoreNotes: Int,
    val timing: TimingSummary?,
    val length: LengthSummary?,
    val hesitation: HesitationSummary?,
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
                return ResultsSummary(attempt.stepCount, attempt.stepCount, null, null, hesitation)
            }
            val scoreNotes = passage.steps.sumOf { it.notes.size }
            val spoiled = analysis.findings.filter { it.kind in PITCH_KINDS }.mapTo(HashSet()) { it.at }
            return ResultsSummary(
                correctPitches = scoreNotes - spoiled.size,
                scoreNotes = scoreNotes,
                timing = timing(analysis),
                length = length(passage, tempoBpm, analysis),
                hesitation = hesitation,
            )
        }

        /** The matches whose played key is the score note's pitch, each with its played note. */
        private fun exactMatches(analysis: PerformanceAnalysis): List<Pair<NoteMatch, PlayedNote>> =
            analysis.matches.mapNotNull { m -> m.played?.takeIf { it.pitch == m.at.pitch }?.let { m to it } }

        private fun timing(analysis: PerformanceAnalysis): TimingSummary {
            // An untimed analysis has no deviations: nothing to average.
            val deviations =
                exactMatches(analysis)
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
        ): LengthSummary {
            val exact = exactMatches(analysis)
            val faults = analysis.findings.filter { it.kind in LENGTH_KINDS }
            val right = exact.size - faults.map { it.at }.toSet().size
            val held = exact.associate { (m, played) -> m.at to (played.upNanos - played.downNanos).toDouble() }
            val percent = if (exact.isEmpty()) 0 else (right * PERCENT.toDouble() / exact.size).roundToInt()
            return LengthSummary(percent, faults.maxByOrNull { badness(passage, tempoBpm, it, held.getValue(it.at)) })
        }

        /** How far a length finding is from the written length: held ÷ written when long, written ÷ held when short. */
        private fun badness(
            passage: Passage,
            tempoBpm: Int,
            finding: Finding,
            held: Double,
        ): Double {
            val ticks =
                passage.steps[finding.at.stepIndex]
                    .notes
                    .first { it.pitch == finding.at.pitch }
                    .lengthTicks
            val written = ticks * NANOS_PER_MINUTE / tempoBpm / TICKS_PER_QUARTER
            return if (finding.kind == ErrorKind.TOO_SHORT) written / held else held / written
        }

        private fun hesitation(analysis: PerformanceAnalysis): HesitationSummary {
            val pauses = analysis.findings.filter { it.kind == ErrorKind.HESITATION }
            val gaps = analysis.matches.associate { it.at to it.gapBeforeNanos }
            val longest = pauses.mapNotNull { f -> gaps[f.at]?.let { f.at to it } }.maxByOrNull { it.second }
            return HesitationSummary(
                count = pauses.size,
                longestMs = longest?.let { (it.second / NANOS_PER_MILLI).toInt() },
                longestAt = longest?.first,
            )
        }
    }
}
