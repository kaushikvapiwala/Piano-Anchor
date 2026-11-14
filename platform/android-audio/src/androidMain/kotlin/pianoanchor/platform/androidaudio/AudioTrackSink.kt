package pianoanchor.platform.androidaudio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import pianoanchor.core.model.ExcludeFromCoverage
import pianoanchor.core.ports.ClockPort
import pianoanchor.engine.Tunables

private const val GLUE = "thin android.media.AudioTrack glue; checked by ear on a device (owner check, M1.4a)"
private const val TAG = "PianoAnchorAudio"
private const val MS_PER_SECOND = 1_000
private const val NANOS_PER_MS = 1_000_000L
private const val BYTES_PER_FRAME = 2 // mono 16-bit

/** Output rate when the device does not report its native one; the rate of most phone audio paths. */
private const val FALLBACK_SAMPLE_RATE = 48_000

/** Wait before retrying a write the full track could not take; short against a 10 ms chunk. */
private const val WRITE_RETRY_MS = 2L

/**
 * The track's buffer holds at least this many chunks, so a late wake-up of the writer doesn't underrun it. A deeper
 * queue only delays when a new pattern is heard; the timestamp mapping keeps clicks on time.
 */
private const val BUFFER_CHUNKS = 4

/**
 * How often a timestamp is re-read once playback has one. The `AudioTrack` documentation advises against frequent
 * polling; the mapping drifts slowly, so a fresh reading each second is plenty.
 */
private const val TIMESTAMP_REFRESH_MS = 1_000L

/**
 * Creates the metronome over a low-latency `AudioTrack` on [context]'s audio output. It lives as long as the app; a
 * writer that fails is logged and replaced by the next `start`, never crashing the app.
 * [clock] must read `System.nanoTime()` (`CLOCK_MONOTONIC`, like `AndroidClockAdapter`): `AudioTimestamp.nanoTime`
 * uses that clock, and beat times come from it.
 */
@ExcludeFromCoverage(GLUE)
public fun AndroidMetronomeAdapter(
    context: Context,
    clock: ClockPort,
): AndroidMetronomeAdapter {
    val onFailure = CoroutineExceptionHandler { _, e -> Log.w(TAG, "metronome writer failed", e) }
    return AndroidMetronomeAdapter(
        AudioTrackSink(context),
        clock,
        CoroutineScope(SupervisorJob() + Dispatchers.Default + onFailure),
    )
}

/**
 * [AudioSink] over a streaming `AudioTrack`: mono 16-bit PCM at the device's native rate,
 * `PERFORMANCE_MODE_LOW_LATENCY` and `USAGE_GAME`, created on the first write and kept open. Writes are non-blocking
 * with short suspending retries, so cancelling the writer never waits for the track. A track whose write fails (for
 * example after the audio route changed) is replaced; frame positions carry on. Called from one coroutine at a time
 * (the adapter's writer, then `stop` after it has ended).
 */
@ExcludeFromCoverage(GLUE)
internal class AudioTrackSink(
    context: Context,
) : AudioSink {
    override val sampleRate: Int =
        context
            .getSystemService(AudioManager::class.java)
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: FALLBACK_SAMPLE_RATE

    private var track: AudioTrack? = null
    private val reading = AudioTimestamp()
    private var latest: FrameTime? = null
    private var playing = false
    private var playStartedNanos = 0L
    private var written = 0L // frames since the last stop
    private var trackOffset = 0L // frames written before the current track (after a replacement)

    override suspend fun write(frames: ShortArray) {
        val track = track ?: createTrack().also { track = it }
        if (!playing) {
            track.play()
            playing = true
            playStartedNanos = System.nanoTime()
            latest = null
        }
        var offset = 0
        while (offset < frames.size) {
            val count = track.write(frames, offset, frames.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            if (count < 0) {
                replaceTrack(nextFrame = written + frames.size)
                break // the rest of this chunk is lost, like the audio the failed track had queued
            }
            offset += count
            if (offset < frames.size) delay(WRITE_RETRY_MS)
        }
        written += frames.size
    }

    override fun timestamp(): FrameTime? {
        val track = track
        if (track != null && playing && isStale(latest)) {
            // A timestamp from before the latest play() describes audio that was flushed or lost.
            if (track.getTimestamp(reading) && reading.nanoTime >= playStartedNanos) {
                latest = FrameTime(trackOffset + reading.framePosition, reading.nanoTime)
            }
        }
        return latest
    }

    private fun isStale(time: FrameTime?) =
        time == null || System.nanoTime() - time.nanoTime >= TIMESTAMP_REFRESH_MS * NANOS_PER_MS

    override fun stop() {
        track?.run {
            pause()
            flush() // also resets the track's frame position to 0
        }
        playing = false
        latest = null
        written = 0
        trackOffset = 0
    }

    /** Replaces the failed track; its frame 0 will be the sink's frame [nextFrame]. */
    private fun replaceTrack(nextFrame: Long) {
        track?.release()
        track = null
        playing = false
        trackOffset = nextFrame
    }

    private fun createTrack(): AudioTrack {
        val minimum =
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val chunks =
            (sampleRate * Tunables.METRONOME_CHUNK_MS / MS_PER_SECOND).toInt() * BUFFER_CHUNKS * BYTES_PER_FRAME
        return AudioTrack
            .Builder()
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).setAudioFormat(
                AudioFormat
                    .Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            ).setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(maxOf(minimum, chunks))
            .build()
    }
}
