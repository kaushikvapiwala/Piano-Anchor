package pianoanchor.platform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.CalibrationRepositoryPort
import pianoanchor.testing.CalibrationRepositoryPortContractTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SqlCalibrationRepositoryAdapterTest : CalibrationRepositoryPortContractTest() {
    private val drivers = InMemoryDrivers()

    override fun createSubject(): CalibrationRepositoryPort =
        SqlCalibrationRepositoryAdapter(drivers.open(), Dispatchers.Unconfined)

    @AfterTest
    fun closeDrivers() = drivers.close()

    @Test
    fun getAndSetThrowStorageUnavailableExceptionWhenTheDriverFails() =
        runTest {
            val driver = FailingDriver(drivers.open()).apply { failing = true }
            val repository = SqlCalibrationRepositoryAdapter(driver, Dispatchers.Unconfined)
            assertFailsWith<StorageUnavailableException> { repository.latencyOffsetNanos("usb-1") }
            assertFailsWith<StorageUnavailableException> { repository.setLatencyOffsetNanos("usb-1", 1) }
        }
}
