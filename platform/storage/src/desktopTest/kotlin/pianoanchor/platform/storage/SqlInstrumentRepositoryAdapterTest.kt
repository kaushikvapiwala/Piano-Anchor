package pianoanchor.platform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.testing.InstrumentRepositoryPortContractTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SqlInstrumentRepositoryAdapterTest : InstrumentRepositoryPortContractTest() {
    private val drivers = InMemoryDrivers()

    override fun createSubject(): InstrumentRepositoryPort =
        SqlInstrumentRepositoryAdapter(drivers.open(), Dispatchers.Unconfined)

    @AfterTest
    fun closeDrivers() = drivers.close()

    @Test
    fun threeByteManufacturerIdAndHighVersionBytesRoundTrip() =
        runTest {
            val repository = createSubject()
            val seen = Instant.fromEpochMilliseconds(1_234)
            val identity =
                InstrumentIdentity(
                    listOf(0x00, 0x21, 0x7F),
                    family = 16_383,
                    model = 0,
                    version = listOf(0x0F, 0x10, 0, 127),
                )
            val piano = Instrument("ble-1", "Piano", identity, firstSeen = seen, lastSeen = seen)
            repository.upsert(piano)
            assertEquals(piano, repository.get("ble-1"))
        }
}
