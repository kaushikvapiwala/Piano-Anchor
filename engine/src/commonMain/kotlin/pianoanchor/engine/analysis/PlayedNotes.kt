package pianoanchor.engine.analysis

import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.PLAYED_NOTE_ORDER
import pianoanchor.core.model.PlayedNote

/** The sustain (damper) pedal's controller number (MIDI 1.0 specification, control change 64). */
private const val SUSTAIN_CONTROLLER = 64

/** Sustain pedal values at or above this are "down" (MIDI 1.0: 0–63 off, 64–127 on). */
private const val SUSTAIN_DOWN_MIN = 64

/** Turns MIDI events into played notes, with the sustain pedal (`docs/algorithms/performance-analysis.md`). */
public object PlayedNotes {
    /**
     * Pairs NoteOn/NoteOff per pitch on every channel (the parser already turns NoteOn velocity 0 into NoteOff).
     * CC64 >= 64 holds sounding notes until the pedal drops below 64. Striking a pitch that is still down or still
     * sounding ends the earlier note there. A note still down or held at the end of [events] ends at the last
     * event's time. Events are taken in timestamp order (a stable sort keeps equal times in list order). Notes come
     * in [PLAYED_NOTE_ORDER]; other events are ignored.
     */
    public fun from(events: List<MidiEvent>): List<PlayedNote> {
        val ordered = events.sortedBy { it.timestampNanos }
        val extraction = Extraction()
        ordered.forEach(extraction::apply)
        return extraction.finish(ordered.lastOrNull()?.timestampNanos)
    }
}

private class Extraction {
    private class Down(
        val velocity: Int,
        val downNanos: Long,
    )

    private val down = HashMap<MidiNote, Down>()
    private val held = HashMap<MidiNote, PlayedNote>()
    private val done = ArrayList<PlayedNote>()
    private var pedalDown = false

    fun apply(event: MidiEvent) {
        val t = event.timestampNanos
        when (event) {
            is NoteOn -> strike(event.note, event.velocity, t)
            is NoteOff -> release(event.note, t)
            is ControlChange -> if (event.controller == SUSTAIN_CONTROLLER) pedal(event.value >= SUSTAIN_DOWN_MIN, t)
            else -> Unit
        }
    }

    fun finish(lastNanos: Long?): List<PlayedNote> {
        lastNanos?.let { end ->
            down.keys.toList().forEach { release(it, end) }
            pedal(false, end)
        }
        return done.sortedWith(PLAYED_NOTE_ORDER)
    }

    private fun strike(
        pitch: MidiNote,
        velocity: Int,
        t: Long,
    ) {
        release(pitch, t)
        held.remove(pitch)?.let { done += it.copy(soundingEndNanos = t) }
        down[pitch] = Down(velocity, t)
    }

    private fun release(
        pitch: MidiNote,
        t: Long,
    ) {
        val note = down.remove(pitch) ?: return
        val played = PlayedNote(pitch, note.velocity, note.downNanos, t, t)
        if (pedalDown) held[pitch] = played else done += played
    }

    private fun pedal(
        isDown: Boolean,
        t: Long,
    ) {
        pedalDown = isDown
        if (!isDown) {
            held.values.forEach { done += it.copy(soundingEndNanos = t) }
            held.clear()
        }
    }
}
