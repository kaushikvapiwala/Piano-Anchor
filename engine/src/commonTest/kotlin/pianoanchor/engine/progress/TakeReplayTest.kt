package pianoanchor.engine.progress

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.Tone
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private const val MS = 1_000_000L

class TakeReplayTest {
    private fun take(vararg notes: PlayedNote) =
        Attempt(
            id = "a",
            passageId = "p",
            deviceId = "d",
            startedAt = Instant.fromEpochSeconds(0),
            durationNanos = 1,
            stepCount = 1,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = listOf(PassageComplete(0, 0)),
            tempoBpm = null,
            playedNotes = notes.toList(),
        )

    private fun note(
        pitch: Int,
        downMs: Long,
        upMs: Long = downMs + 100,
        endMs: Long = upMs,
        velocity: Int = 64,
    ) = PlayedNote(MidiNote(pitch), velocity, downMs * MS, upMs * MS, endMs * MS)

    @Test
    fun offsetsStartAtZeroFromTheFirstNote() {
        val tones = TakeReplay.tones(take(note(60, 5_000), note(62, 5_500)))
        assertEquals(listOf(0L, 500L), tones.map { it.startMs })
    }

    @Test
    fun lengthIsSoundingEndMinusKeyDown() {
        val tones = TakeReplay.tones(take(note(60, 0, upMs = 100, endMs = 700)))
        assertEquals(listOf(Tone(MidiNote(60), 0, 700, 64)), tones)
    }

    @Test
    fun velocityIsKept() {
        assertEquals(listOf(99), TakeReplay.tones(take(note(60, 0, velocity = 99))).map { it.velocity })
    }

    @Test
    fun aTakeWithoutNotesIsEmpty() {
        assertEquals(emptyList(), TakeReplay.tones(take()))
    }

    @Test
    fun aZeroLengthNoteStillPlaysForAMillisecond() {
        assertEquals(1L, TakeReplay.tones(take(note(60, 0, upMs = 0))).single().lengthMs)
    }

    @Test
    fun notesAreOrderedByKeyDownWhateverTheirStoredOrder() {
        val tones = TakeReplay.tones(take(note(64, 2_000), note(60, 1_000)))
        assertEquals(listOf(60, 64), tones.map { it.note.number })
        assertEquals(listOf(0L, 1_000L), tones.map { it.startMs })
    }

    @Test
    fun notesPastTheCapAreDroppedAndOneCrossingItIsCut() {
        val cap = Tunables.TAKE_REPLAY_MAX_MS
        val tones =
            TakeReplay.tones(
                take(note(60, 0), note(62, cap - 50, upMs = cap + 500), note(64, cap), note(65, cap + 1_000)),
            )
        assertEquals(listOf(0L, cap - 50), tones.map { it.startMs })
        assertEquals(50L, tones.last().lengthMs)
    }
}
