package pianoanchor.testing

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.ScoreNoteRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClassificationScoreTest {
    private val c4 = MidiNote(60)
    private val e4 = MidiNote(64)
    private val passage = passageOf(setOf(c4), setOf(e4))
    private val clean = TakeGenerator(1).clean(passage, 60)

    private fun finding(
        kind: ErrorKind,
        step: Int,
        pitch: MidiNote,
    ) = Finding(
        kind,
        ScoreNoteRef(step, pitch),
        played = if (kind == ErrorKind.MISSED_NOTE || kind == ErrorKind.INCOMPLETE_CHORD) null else clean.notes.first(),
    )

    private fun labelled(vararg labels: Finding) = clean.copy(labels = labels.toList())

    @Test
    fun perfectFindingsScoreOne() {
        val octave = finding(ErrorKind.OCTAVE, 0, c4)
        val late = finding(ErrorKind.LATE, 1, e4)
        val score =
            ClassificationScore.of(
                listOf(
                    labelled(octave) to listOf(octave),
                    labelled(octave) to listOf(octave),
                    labelled(late) to listOf(late),
                    clean to emptyList(),
                ),
            )
        assertEquals(1.0, score.overall)
        assertEquals(1.0, score.kindRate(ErrorKind.OCTAVE))
        assertEquals(0.0, score.falseAlarmRate)
        assertEquals(mapOf(ErrorKind.OCTAVE to (2 to 2), ErrorKind.LATE to (1 to 1)), score.perKind)
    }

    @Test
    fun aWrongKindAtTheRightNoteDoesNotMatch() {
        val score =
            ClassificationScore.of(
                listOf(labelled(finding(ErrorKind.EARLY, 1, e4)) to listOf(finding(ErrorKind.LATE, 1, e4))),
            )
        assertEquals(0.0, score.overall)
        assertEquals(0 to 1, score.perKind[ErrorKind.EARLY])
    }

    @Test
    fun theRightKindAtAnotherNoteDoesNotMatch() {
        val score =
            ClassificationScore.of(
                listOf(labelled(finding(ErrorKind.EARLY, 1, e4)) to listOf(finding(ErrorKind.EARLY, 0, c4))),
            )
        assertEquals(0.0, score.kindRate(ErrorKind.EARLY))
    }

    @Test
    fun oneFindingMatchesAtMostOneLabel() {
        val miss = finding(ErrorKind.MISSED_NOTE, 0, c4)
        val score = ClassificationScore.of(listOf(labelled(miss, miss) to listOf(miss)))
        assertEquals(1 to 2, score.perKind[ErrorKind.MISSED_NOTE])
        assertEquals(0.5, score.overall)
    }

    @Test
    fun falseAlarmsCountOnlyOnCleanTakes() {
        val miss = finding(ErrorKind.MISSED_NOTE, 0, c4)
        val extra = finding(ErrorKind.EXTRA_NOTE, 1, e4)
        val score =
            ClassificationScore.of(
                listOf(
                    labelled(miss) to listOf(miss, extra),
                    clean to listOf(extra),
                    clean to emptyList(),
                ),
            )
        assertEquals(1, score.falseAlarms)
        assertEquals(4, score.cleanNotes)
        assertEquals(0.25, score.falseAlarmRate)
        assertEquals(1.0, score.overall)
    }

    @Test
    fun ratesOverNothingThrow() {
        val score = ClassificationScore.of(emptyList())
        assertFailsWith<IllegalStateException> { score.overall }
        assertFailsWith<IllegalStateException> { score.kindRate(ErrorKind.OCTAVE) }
        assertFailsWith<IllegalStateException> { score.falseAlarmRate }
    }

    @Test
    fun rejectsInconsistentCounts() {
        assertFailsWith<IllegalArgumentException> { ClassificationScore(mapOf(ErrorKind.LATE to (2 to 1)), 0, 0) }
        assertFailsWith<IllegalArgumentException> { ClassificationScore(mapOf(ErrorKind.LATE to (-1 to 1)), 0, 0) }
        assertFailsWith<IllegalArgumentException> { ClassificationScore(mapOf(ErrorKind.LATE to (0 to 0)), 0, 0) }
        assertFailsWith<IllegalArgumentException> { ClassificationScore(emptyMap(), -1, 0) }
        assertFailsWith<IllegalArgumentException> { ClassificationScore(emptyMap(), 0, -1) }
    }
}
