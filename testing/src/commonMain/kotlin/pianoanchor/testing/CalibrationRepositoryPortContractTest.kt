package pianoanchor.testing

import kotlinx.coroutines.test.runTest
import pianoanchor.core.ports.CalibrationRepositoryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val SOME_OFFSET = 38_000_000L
private const val OTHER_OFFSET = 52_000_000L
private const val EARLY_OFFSET = -25_000_000L

/** Behaviour every [CalibrationRepositoryPort] adapter must satisfy. Subclass it once per adapter. */
abstract class CalibrationRepositoryPortContractTest {
    protected abstract fun createSubject(): CalibrationRepositoryPort

    @Test
    fun unknownDeviceHasNoOffset() =
        runTest {
            assertNull(createSubject().latencyOffsetNanos("usb-1"))
        }

    @Test
    fun setThenGetRoundTrips() =
        runTest {
            val repository = createSubject()
            repository.setLatencyOffsetNanos("usb-1", SOME_OFFSET)
            assertEquals(SOME_OFFSET, repository.latencyOffsetNanos("usb-1"))
            assertNull(repository.latencyOffsetNanos("ble-1"))
        }

    @Test
    fun setReplaces() =
        runTest {
            val repository = createSubject()
            repository.setLatencyOffsetNanos("usb-1", SOME_OFFSET)
            repository.setLatencyOffsetNanos("usb-1", OTHER_OFFSET)
            assertEquals(OTHER_OFFSET, repository.latencyOffsetNanos("usb-1"))
        }

    @Test
    fun negativeOffsetsAreKept() =
        runTest {
            val repository = createSubject()
            repository.setLatencyOffsetNanos("usb-1", EARLY_OFFSET)
            assertEquals(EARLY_OFFSET, repository.latencyOffsetNanos("usb-1"))
        }
}
