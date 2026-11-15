package pianoanchor.testing

import kotlinx.coroutines.delay
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.TonePort

/**
 * In-memory [TonePort] for tests: records each [play] call in [played] and takes virtual time (until the last tone
 * ends) like a speaker would. [playing] is true while a call runs, and drops at once when it is cancelled.
 */
class FakeTone : TonePort {
    private val calls = mutableListOf<List<Tone>>()

    /** The tones of every [play] call so far, in order. */
    val played: List<List<Tone>> get() = calls.toList()

    /** True while a [play] call is sounding. */
    var playing: Boolean = false
        private set

    override suspend fun play(tones: List<Tone>) {
        calls += tones
        playing = true
        try {
            delay(tones.fold(0L) { end, tone -> maxOf(end, tone.startMs + tone.lengthMs) })
        } finally {
            playing = false
        }
    }
}
