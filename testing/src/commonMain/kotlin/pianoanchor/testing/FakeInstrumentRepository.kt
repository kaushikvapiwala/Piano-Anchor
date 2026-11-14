package pianoanchor.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.InstrumentRepositoryPort

/** In-memory [InstrumentRepositoryPort]: safe to call from any thread, forgets everything when the process ends. */
class FakeInstrumentRepository : InstrumentRepositoryPort {
    private val instruments = MutableStateFlow(emptyMap<String, Instrument>())

    /** While true, every call throws [StorageUnavailableException]. */
    var failing: Boolean = false

    override suspend fun upsert(instrument: Instrument) {
        failIfFailing("upsert")
        instruments.update { all ->
            val stored = all[instrument.deviceId]
            val merged =
                if (stored == null) {
                    instrument
                } else {
                    instrument.copy(
                        identity = instrument.identity ?: stored.identity,
                        firstSeen = minOf(stored.firstSeen, instrument.firstSeen),
                    )
                }
            all + (instrument.deviceId to merged)
        }
    }

    override suspend fun get(deviceId: String): Instrument? {
        failIfFailing("get")
        return instruments.value[deviceId]
    }

    private fun failIfFailing(operation: String) {
        if (failing) throw StorageUnavailableException("$operation failed")
    }
}
