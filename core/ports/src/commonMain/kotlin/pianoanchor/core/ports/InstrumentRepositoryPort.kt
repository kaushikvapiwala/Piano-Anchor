package pianoanchor.core.ports

import pianoanchor.core.model.Instrument

/**
 * Driven port: the pianos the app has seen, kept across app restarts and keyed by [Instrument.deviceId].
 * Contract: `pianoanchor.testing.InstrumentRepositoryPortContractTest`.
 */
public interface InstrumentRepositoryPort {
    /**
     * Stores [instrument]. When its device id is already stored, replaces name, identity and lastSeen and keeps the
     * earlier of the stored and the given firstSeen.
     */
    public suspend fun upsert(instrument: Instrument)

    /** The stored instrument with [deviceId], or null if none is stored. */
    public suspend fun get(deviceId: String): Instrument?
}
