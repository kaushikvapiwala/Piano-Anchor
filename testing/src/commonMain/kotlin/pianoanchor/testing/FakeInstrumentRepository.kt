package pianoanchor.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import pianoanchor.core.model.Instrument
import pianoanchor.core.ports.InstrumentRepositoryPort

/**
 * In-memory [InstrumentRepositoryPort]: safe to call from any thread, forgets everything when the process ends.
 * Also the app's store until the SQLDelight adapter lands (roadmap slice 1.8).
 */
class FakeInstrumentRepository : InstrumentRepositoryPort {
    private val instruments = MutableStateFlow(emptyMap<String, Instrument>())

    override suspend fun upsert(instrument: Instrument) {
        instruments.update { all ->
            val stored = all[instrument.deviceId]
            val merged =
                when (stored) {
                    null -> instrument
                    else -> instrument.copy(firstSeen = minOf(stored.firstSeen, instrument.firstSeen))
                }
            all + (instrument.deviceId to merged)
        }
    }

    override suspend fun get(deviceId: String): Instrument? = instruments.value[deviceId]
}
