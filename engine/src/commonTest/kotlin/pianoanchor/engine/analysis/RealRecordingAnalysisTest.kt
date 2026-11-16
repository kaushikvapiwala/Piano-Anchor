package pianoanchor.engine.analysis

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.MidiNote
import pianoanchor.engine.content.Passages
import pianoanchor.engine.fixture.CDE_FRP_NUVOLA_BLE
import pianoanchor.engine.fixture.FixtureCodec
import pianoanchor.engine.midi.MidiParser
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where the recording's second take begins: the gap between takes is 4.8 s. */
private const val SECOND_TAKE_NANOS = 5_000_000_000L

/** M1's exit check on a real recording: the analyser on both C-D-E takes of `fixtures/cde-frp-nuvola-ble.jsonl`. */
class RealRecordingAnalysisTest {
    private val notes =
        MidiParser().let { parser ->
            PlayedNotes.from(FixtureCodec.decode(CDE_FRP_NUVOLA_BLE).messages.flatMap { parser.feed(it.bytes, it.t) })
        }

    private fun take(second: Boolean) = notes.filter { (it.downNanos >= SECOND_TAKE_NANOS) == second }

    @Test
    fun bothTakesMatchEveryNoteInWaitMode() {
        listOf(false, true).forEach { second ->
            val analysis = PerformanceAnalyser.analyse(Passages.CDE, take(second), TempoReference.Untimed)

            assertEquals(emptyList(), analysis.findings)
            assertEquals(listOf(60, 62, 64), analysis.matches.map { it.played?.pitch?.number })
        }
    }

    @Test
    fun aFittedTakeFlagsTheShortHeldNotesAndNothingElse() {
        listOf(false, true).forEach { second ->
            val analysis = PerformanceAnalyser.analyse(Passages.CDE, take(second), TempoReference.Fitted)

            // Take 2's C is held 265 ms, under half its fitted quarter; take 1's is held 345 ms, just over.
            val short = if (second) listOf(60, 62, 64) else listOf(62, 64)
            assertEquals(
                short.map { ErrorKind.TOO_SHORT to MidiNote(it) },
                analysis.findings.map { it.kind to it.at.pitch },
            )
            assertEquals(if (second) 92 else 87, analysis.fittedTempoBpm?.roundToInt())
        }
    }
}
