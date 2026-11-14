package pianoanchor.platform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.testing.InstrumentRepositoryPortContractTest
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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

    @Test
    fun upsertThrowsStorageUnavailableExceptionWhenTheDriverFails() =
        runTest {
            val driver = FailingDriver(drivers.open()).apply { failing = true }
            val seen = Instant.fromEpochMilliseconds(1_234)
            val e =
                assertFailsWith<StorageUnavailableException> {
                    SqlInstrumentRepositoryAdapter(driver, Dispatchers.Unconfined)
                        .upsert(Instrument("usb-1", "Piano", identity = null, firstSeen = seen, lastSeen = seen))
                }
            assertIs<SQLException>(e.cause)
        }

    @Test
    fun getThrowsStorageUnavailableExceptionWhenTheDriverFails() =
        runTest {
            val driver = FailingDriver(drivers.open()).apply { failing = true }
            assertFailsWith<StorageUnavailableException> {
                SqlInstrumentRepositoryAdapter(driver, Dispatchers.Unconfined).get("usb-1")
            }
        }
}
