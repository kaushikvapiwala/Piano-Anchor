package pianoanchor.core.ports

import pianoanchor.core.model.StorageUnavailableException

/**
 * Driven port: each piano's latency offset, kept across restarts and keyed by MidiDevice.id. Positive means the
 * piano's notes arrive later than the click they answer. Throws [StorageUnavailableException] when the store can't be
 * read or written (#55). Contract: `pianoanchor.testing.CalibrationRepositoryPortContractTest`.
 */
public interface CalibrationRepositoryPort {
    /** The stored offset for [deviceId], or null if it was never calibrated. */
    public suspend fun latencyOffsetNanos(deviceId: String): Long?

    /** Stores [offsetNanos] for [deviceId], replacing an earlier one. */
    public suspend fun setLatencyOffsetNanos(
        deviceId: String,
        offsetNanos: Long,
    )
}
