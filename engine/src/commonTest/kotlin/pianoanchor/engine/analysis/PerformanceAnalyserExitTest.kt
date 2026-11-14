package pianoanchor.engine.analysis

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.testing.ClassificationScore
import pianoanchor.testing.ErrorInjector
import pianoanchor.testing.LabelledTake
import pianoanchor.testing.TAKE_START_NANOS
import pianoanchor.testing.TakeGenerator
import kotlin.test.Test
import kotlin.test.assertTrue

private const val Q = TICKS_PER_QUARTER
private const val TAKES_PER_SET = 30

/**
 * M1's exit check: on seeded takes of three original passages, the analyser classifies >= 95 % of injected errors
 * overall and >= 90 % of each kind, with false alarms on <= 5 % of the notes of clean takes.
 */
class PerformanceAnalyserExitTest {
    /** Each step is (onset in eighths, length in eighths, MIDI pitches). */
    private fun passage(
        id: String,
        tempoBpm: Int,
        vararg steps: Triple<Int, Int, List<Int>>,
    ) = Passage(
        id = id,
        title = id,
        steps =
            steps.map { (onset, length, pitches) ->
                Step(onset * Q / 2, pitches.mapTo(LinkedHashSet()) { ScoreNote(MidiNote(it), length * Q / 2) })
            },
        timeSignature = TimeSignature(4, 4),
        tempoBpm = tempoBpm,
    )

    /** A stepwise melody in quarter notes, ending on a half note. */
    private val cde =
        passage(
            "exit:cde",
            80,
            *listOf(60, 62, 64, 62, 60, 64, 62, 64, 65, 64, 62, 60)
                .mapIndexed { i, pitch -> Triple(i * 2, 2, listOf(pitch)) }
                .toTypedArray(),
            Triple(24, 4, listOf(60)),
        )

    /** Left-hand triads under a right-hand line, with single notes between the chords. */
    private val chords =
        passage(
            "exit:chords",
            72,
            Triple(0, 2, listOf(48, 52, 55, 64)),
            Triple(2, 2, listOf(62)),
            Triple(4, 2, listOf(60)),
            Triple(6, 2, listOf(62)),
            Triple(8, 2, listOf(53, 57, 60, 65)),
            Triple(10, 2, listOf(64)),
            Triple(12, 2, listOf(62)),
            Triple(14, 2, listOf(64)),
            Triple(16, 2, listOf(43, 47, 50, 62)),
            Triple(18, 2, listOf(65)),
            Triple(20, 4, listOf(48, 52, 55, 60)),
        )

    /** Half, quarter, dotted-quarter and eighth notes in one line. */
    private val mixed =
        passage(
            "exit:mixed",
            90,
            Triple(0, 4, listOf(67)),
            Triple(4, 2, listOf(65)),
            Triple(6, 2, listOf(64)),
            Triple(8, 3, listOf(62)),
            Triple(11, 1, listOf(64)),
            Triple(12, 1, listOf(65)),
            Triple(13, 1, listOf(64)),
            Triple(14, 2, listOf(62)),
            Triple(16, 2, listOf(60)),
            Triple(18, 1, listOf(62)),
            Triple(19, 1, listOf(64)),
            Triple(20, 2, listOf(65)),
            Triple(22, 2, listOf(62)),
            Triple(24, 8, listOf(60)),
        )

    private val passages = listOf(cde, chords, mixed)

    private fun eligible(kind: ErrorKind) = if (kind == ErrorKind.INCOMPLETE_CHORD) listOf(chords) else passages

    private fun analyse(take: LabelledTake) =
        take to
            PerformanceAnalyser
                .analyse(take.passage, take.notes, TempoReference.Target(take.tempoBpm, TAKE_START_NANOS))
                .findings

    @Test
    fun classifiesInjectedErrorsWithFewFalseAlarms() {
        val generator = TakeGenerator(seed = 1)
        val injector = ErrorInjector(seed = 2)
        val cleanPassages = (0 until TAKES_PER_SET).map { passages[it % passages.size] }
        val clean = cleanPassages.map { generator.clean(it, it.tempoBpm) }
        val faulty =
            ErrorKind.entries.flatMap { kind ->
                val choices = eligible(kind)
                (0 until TAKES_PER_SET).map {
                    val passage = choices[it % choices.size]
                    injector.inject(generator.clean(passage, passage.tempoBpm), kind)
                }
            }
        val score = ClassificationScore.of((clean + faulty).map(::analyse))
        val table =
            ErrorKind.entries.joinToString("\n", postfix = "\n") { "$it: ${score.perKind[it]}" } +
                "overall ${score.overall}, false alarms ${score.falseAlarms} of ${score.cleanNotes} clean notes"
        assertTrue(score.overall >= 0.95, table)
        ErrorKind.entries.forEach { assertTrue(score.kindRate(it) >= 0.90, "$it below 90 %\n$table") }
        assertTrue(score.falseAlarmRate <= 0.05, table)
    }
}
