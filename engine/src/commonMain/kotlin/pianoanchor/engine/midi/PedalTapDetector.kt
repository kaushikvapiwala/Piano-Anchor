package pianoanchor.engine.midi

import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiEvent
import pianoanchor.engine.Tunables

/** The MIDI controller number of the sustain pedal. */
public const val SUSTAIN_CONTROLLER: Int = 64
private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Spots a tap of the sustain pedal: the pedal goes down (value at least `PEDAL_DOWN_VALUE`) and comes back up (value
 * below `PEDAL_UP_VALUE`) within `PEDAL_TAP_MAX_MS`. A longer press is a hold (music), not a tap. Values between the
 * two thresholds change nothing, so a half-pedal wobble counts once.
 */
public class PedalTapDetector {
    private var downAtNanos: Long? = null

    /**
     * Feeds one event; returns true when it completes a tap (down then up within PEDAL_TAP_MAX_MS). Other events:
     * false.
     */
    public fun onEvent(event: MidiEvent): Boolean {
        if (event !is ControlChange || event.controller != SUSTAIN_CONTROLLER) return false
        val downAt = downAtNanos
        return when {
            downAt == null -> {
                if (event.value >= Tunables.PEDAL_DOWN_VALUE) downAtNanos = event.timestampNanos
                false
            }

            event.value < Tunables.PEDAL_UP_VALUE -> {
                downAtNanos = null
                event.timestampNanos - downAt <= Tunables.PEDAL_TAP_MAX_MS * NANOS_PER_MILLI
            }

            else -> {
                false
            }
        }
    }

    /** Forgets a press in progress. */
    public fun reset() {
        downAtNanos = null
    }
}
