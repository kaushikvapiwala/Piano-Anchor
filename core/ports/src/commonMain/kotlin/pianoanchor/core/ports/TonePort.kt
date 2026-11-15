package pianoanchor.core.ports

import pianoanchor.core.model.Tone

/**
 * Driven port: plays simple synthesised tones on the device's speaker.
 * Contract: `pianoanchor.testing.TonePortContractTest`.
 */
public interface TonePort {
    /**
     * Plays [tones] (start offsets relative to the call) and returns once the last one has ended. Cancelling stops
     * the sound at once.
     */
    public suspend fun play(tones: List<Tone>)
}
