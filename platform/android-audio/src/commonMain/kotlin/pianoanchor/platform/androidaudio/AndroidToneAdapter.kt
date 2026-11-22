package pianoanchor.platform.androidaudio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.TonePort
import pianoanchor.engine.Tunables

/**
 * Silence queued after the last tone. A write returns once the audio is queued, not played; this tail is longer than
 * the output's queue, so the last tone has been played by the time the final write returns.
 */
private const val TAIL_MS = 250L

/**
 * [TonePort] that renders the tones with [ToneRenderer] and streams the PCM to an [AudioSink] in
 * `METRONOME_CHUNK_MS` chunks. Rendering and streaming run on [dispatcher], not the caller's. Cancelling stops the
 * sink, which drops the queued audio.
 * One call plays at a time. Create it on Android with `AndroidToneAdapter(context)`; it needs its own sink: the
 * metronome and tones never play at once (the ear screen has no click).
 * Contract: `TonePortContractTest`; details in `docs/protocols/android-audio.md`.
 */
public class AndroidToneAdapter internal constructor(
    private val sink: AudioSink,
    private val dispatcher: CoroutineDispatcher,
) : TonePort {
    private val renderer = ToneRenderer(sink.sampleRate)
    private val chunkFrames = framesOf(sink.sampleRate, Tunables.METRONOME_CHUNK_MS)
    private val tail = ShortArray(framesOf(sink.sampleRate, TAIL_MS))
    private val lock = Mutex()

    override suspend fun play(tones: List<Tone>) {
        if (tones.isEmpty()) return
        withContext(dispatcher) {
            val pcm = renderer.render(tones) + tail
            lock.withLock {
                try {
                    var from = 0
                    while (from < pcm.size) {
                        sink.write(pcm.copyOfRange(from, minOf(from + chunkFrames, pcm.size)))
                        from += chunkFrames
                    }
                } finally {
                    sink.stop()
                }
            }
        }
    }
}
