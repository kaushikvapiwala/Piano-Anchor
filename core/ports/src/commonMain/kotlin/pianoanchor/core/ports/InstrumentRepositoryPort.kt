package pianoanchor.core.ports

import pianoanchor.core.model.Instrument
import pianoanchor.core.model.StorageUnavailableException

/**
 * Driven port: the pianos the app has seen, kept across app restarts and keyed by [Instrument.deviceId].
 * Contract: `pianoanchor.testing.InstrumentRepositoryPortContractTest`.
 */
public interface InstrumentRepositoryPort {
    /**
     * Stores [instrument]. When its device id is already stored, replaces name and lastSeen and keeps the earlier of
     * the stored and the given firstSeen. For a stable device id (`isStableDeviceId`) the identity is replaced only
     * when the given one is non-null: a null identity keeps the stored one, so a lost Identity Reply doesn't erase it.
     * For an unstable id (`sys:`, which may later name another piano) the given identity always replaces the stored
     * one, so null clears it. Throws [StorageUnavailableException] when the store can't be read or written.
     */
    public suspend fun upsert(instrument: Instrument)

    /**
     * The stored instrument with [deviceId], or null if none is stored. Throws [StorageUnavailableException] when the
     * store can't be read or written.
     */
    public suspend fun get(deviceId: String): Instrument?
}
