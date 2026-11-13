package pianoanchor.testing

import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.ports.InstrumentRepositoryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Behaviour every [InstrumentRepositoryPort] adapter must satisfy. Subclass it once per adapter. */
abstract class InstrumentRepositoryPortContractTest {
    protected abstract fun createSubject(): InstrumentRepositoryPort

    private val identity = InstrumentIdentity(listOf(0x41), family = 1, model = 2, version = listOf(1, 0, 0, 0))
    private val first = Instant.fromEpochSeconds(1_000)
    private val later = Instant.fromEpochSeconds(2_000)
    private val piano = Instrument("usb-1", "Piano", identity, firstSeen = first, lastSeen = first)

    @Test
    fun unknownDeviceIsNull() =
        runTest {
            assertNull(createSubject().get("usb-1"))
        }

    @Test
    fun upsertThenGetReturnsTheInstrument() =
        runTest {
            val repository = createSubject()
            repository.upsert(piano)
            assertEquals(piano, repository.get("usb-1"))
        }

    @Test
    fun secondUpsertKeepsFirstSeenAndUpdatesTheRest() =
        runTest {
            val repository = createSubject()
            repository.upsert(piano)
            repository.upsert(Instrument("usb-1", "Renamed", null, firstSeen = later, lastSeen = later))
            assertEquals(
                Instrument("usb-1", "Renamed", null, firstSeen = first, lastSeen = later),
                repository.get("usb-1"),
            )
        }

    @Test
    fun upsertWithAnEarlierFirstSeenKeepsTheEarlierOne() =
        runTest {
            val repository = createSubject()
            val earlier = Instant.fromEpochSeconds(500)
            repository.upsert(piano)
            repository.upsert(piano.copy(firstSeen = earlier, lastSeen = earlier))
            assertEquals(piano.copy(firstSeen = earlier, lastSeen = earlier), repository.get("usb-1"))
        }

    @Test
    fun distinctDevicesAreIndependent() =
        runTest {
            val repository = createSubject()
            val other = Instrument("ble-1", "Other", null, firstSeen = later, lastSeen = later)
            repository.upsert(piano)
            repository.upsert(other)
            assertEquals(piano, repository.get("usb-1"))
            assertEquals(other, repository.get("ble-1"))
        }
}
