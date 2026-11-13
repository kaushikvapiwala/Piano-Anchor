package pianoanchor.feature.monitor

import pianoanchor.core.model.MidiEvent
import pianoanchor.engine.Tunables
import pianoanchor.engine.fixture.Fixture
import pianoanchor.engine.fixture.FixtureCodec
import pianoanchor.engine.midi.MidiEncoder

internal const val NANOS_PER_MILLI = 1_000_000L

/** Collects one recording for [MonitorViewModel]: canonical bytes, times relative to the first recorded event. */
internal class FixtureRecorder {
    private val messages = mutableListOf<Fixture.Message>()
    private var firstNanos: Long? = null
    private var device = ""
    private var recordedAt = ""

    val count: Int get() = messages.size

    /** Discards the previous recording and starts one for the piano named [deviceName], at wall-clock [recordedAt]. */
    fun start(
        deviceName: String,
        recordedAt: String,
    ) {
        messages.clear()
        firstNanos = null
        device = deviceName
        this.recordedAt = recordedAt
    }

    /** Records [event]; false (and not recorded) once it lies past `Tunables.MONITOR_RECORDING_MAX_MS`. */
    fun add(event: MidiEvent): Boolean {
        val first = firstNanos ?: event.timestampNanos.also { firstNanos = it }
        val t = event.timestampNanos - first
        if (t > Tunables.MONITOR_RECORDING_MAX_MS * NANOS_PER_MILLI) return false
        messages += Fixture.Message(t, MidiEncoder.encode(event))
        return true
    }

    /** The recording as fixture JSONL, or null when it is empty. */
    fun jsonl(): String? =
        messages.takeIf { it.isNotEmpty() }?.let {
            FixtureCodec.encode(Fixture(Fixture.Header(FixtureCodec.SCHEMA, device, recordedAt), it.toList()))
        }
}
