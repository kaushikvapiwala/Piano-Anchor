package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class InstrumentTest {
    private val version = listOf(1, 0, 0, 0)
    private val start = Instant.fromEpochSeconds(1_000)

    private fun identity(
        manufacturerId: List<Int> = listOf(0x41),
        family: Int = 1,
        model: Int = 2,
        version: List<Int> = this.version,
    ) = InstrumentIdentity(manufacturerId, family, model, version)

    @Test
    fun acceptsOneByteAndThreeByteManufacturerIds() {
        assertEquals(listOf(0x41), identity(manufacturerId = listOf(0x41)).manufacturerId)
        assertEquals(listOf(0, 0x20, 0x33), identity(manufacturerId = listOf(0, 0x20, 0x33)).manufacturerId)
    }

    @Test
    fun acceptsTheFourteenBitLimits() {
        val identity = identity(family = 0, model = 16_383)
        assertEquals(0, identity.family)
        assertEquals(16_383, identity.model)
    }

    @Test
    fun rejectsBadManufacturerIds() {
        for (bad in listOf(emptyList(), listOf(0), listOf(0x41, 1, 2), listOf(0, 1), listOf(0, 1, 2, 3), listOf(128))) {
            assertFailsWith<IllegalArgumentException>("$bad") { identity(manufacturerId = bad) }
        }
        assertFailsWith<IllegalArgumentException> { identity(manufacturerId = listOf(0, 1, -1)) }
    }

    @Test
    fun rejectsFamilyAndModelOutsideFourteenBits() {
        assertFailsWith<IllegalArgumentException> { identity(family = -1) }
        assertFailsWith<IllegalArgumentException> { identity(family = 16_384) }
        assertFailsWith<IllegalArgumentException> { identity(model = -1) }
        assertFailsWith<IllegalArgumentException> { identity(model = 16_384) }
    }

    @Test
    fun rejectsBadVersions() {
        assertFailsWith<IllegalArgumentException> { identity(version = listOf(1, 0, 0)) }
        assertFailsWith<IllegalArgumentException> { identity(version = listOf(1, 0, 0, 0, 0)) }
        assertFailsWith<IllegalArgumentException> { identity(version = listOf(1, 0, 0, 128)) }
        assertFailsWith<IllegalArgumentException> { identity(version = listOf(1, 0, 0, -1)) }
    }

    @Test
    fun keepsAnInstrumentWithOrWithoutIdentity() {
        val later = Instant.fromEpochSeconds(2_000)
        val instrument = Instrument("usb-1", "Piano", identity(), firstSeen = start, lastSeen = later)
        assertEquals(identity(), instrument.identity)
        assertEquals(later, instrument.lastSeen)
        assertEquals(null, Instrument("usb-1", "Piano", null, start, start).identity)
    }

    @Test
    fun rejectsABlankDeviceId() {
        assertFailsWith<IllegalArgumentException> { Instrument(" ", "Piano", null, start, start) }
    }

    @Test
    fun rejectsLastSeenBeforeFirstSeen() {
        assertFailsWith<IllegalArgumentException> {
            Instrument("usb-1", "Piano", null, firstSeen = start, lastSeen = Instant.fromEpochSeconds(999))
        }
    }
}
