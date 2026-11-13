package pianoanchor.platform.androidmidi

import pianoanchor.core.ports.ClockPort

/**
 * [ClockPort] over `System.nanoTime()`: the clock domain of the timestamps Android's MIDI service passes to
 * `MidiReceiver.onSend`, so events from [AndroidMidiAdapter] and readings of this clock can be compared.
 */
public class AndroidClockAdapter : ClockPort {
    override fun nowNanos(): Long = System.nanoTime()
}
