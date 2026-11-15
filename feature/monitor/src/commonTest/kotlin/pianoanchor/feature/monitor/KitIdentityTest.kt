package pianoanchor.feature.monitor

import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class KitIdentityTest {
    private val identity = InstrumentIdentity(listOf(0x41), family = 25, model = 1, version = listOf(0, 1, 0, 0))
    private val seen = Instant.fromEpochSeconds(0)
    private val pianoA = Instrument("piano-a", "Piano A", identity, seen, seen)

    @Test
    fun theRecordedPianosIdentityGoesIntoTheKit() = assertEquals(identity, kitIdentity(pianoA, "piano-a"))

    @Test
    fun anotherPianosIdentityIsLeftOut() = assertNull(kitIdentity(pianoA, "piano-b"))

    @Test
    fun noInstrumentMeansNoIdentity() = assertNull(kitIdentity(null, "piano-a"))
}
