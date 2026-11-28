package pianoanchor.engine.progress

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.PlayedNote
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val MS = 1_000_000L

class TakeLaneTest {
    // Two quarter notes at 60 BPM: one second apart; the early/late band is 125 ms.
    private val passage = passageOf(setOf(MidiNote(60)), setOf(MidiNote(62)))

    private fun note(
        pitch: Int,
        downMs: Long,
    ) = PlayedNote(MidiNote(pitch), 64, downMs * MS, (downMs + 100) * MS, (downMs + 100) * MS)

    private fun take(
        tempoBpm: Int?,
        vararg notes: PlayedNote,
        latencyOffsetNanos: Long? = null,
    ) = Attempt(
        id = "a",
        passageId = passage.id,
        deviceId = "d",
        startedAt = Instant.fromEpochSeconds(0),
        durationNanos = 1,
        stepCount = 2,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = Band.CLEAN,
        events = listOf(PassageComplete(0, 1)),
        tempoBpm = tempoBpm,
        playedNotes = notes.toList(),
        latencyOffsetNanos = latencyOffsetNanos,
    )

    @Test
    fun aTimedNoteWithinTheBandIsOnTheBeatAndOneOutsideIsNot() {
        val dots = TakeLane.dots(passage, take(60, note(60, 0), note(62, 1_300)))
        assertEquals(listOf(LaneDot(0, true), LaneDot(1_300, false)), dots)
    }

    @Test
    fun theLatencyOffsetIsTakenOutBeforeJudging() {
        val offset = 300 * MS
        val dots = TakeLane.dots(passage, take(60, note(60, 300), note(62, 1_300), latencyOffsetNanos = offset))
        assertEquals(listOf(true, true), dots.map { it.onBeat })
    }

    @Test
    fun aWaitModeTakeHasNoBeatSoEveryNoteIsOnIt() {
        val dots = TakeLane.dots(passage, take(null, note(60, 0), note(62, 9_000)))
        assertEquals(listOf(true, true), dots.map { it.onBeat })
        assertEquals(listOf(0L, 9_000L), dots.map { it.offsetMs })
    }

    @Test
    fun aNoteMatchingNoScoreNoteIsOnTheBeat() {
        val dots = TakeLane.dots(passage, take(60, note(60, 0), note(62, 1_000), note(90, 5_000)))
        assertEquals(true, dots.last().onBeat)
    }

    @Test
    fun aMissedScoreNoteLeavesTheOtherDotsJudged() {
        val dots = TakeLane.dots(passage, take(60, note(60, 0)))
        assertEquals(listOf(LaneDot(0, true)), dots)
    }

    @Test
    fun aTakeWithoutNotesHasNoDots() {
        assertEquals(emptyList(), TakeLane.dots(passage, take(60)))
    }
}
