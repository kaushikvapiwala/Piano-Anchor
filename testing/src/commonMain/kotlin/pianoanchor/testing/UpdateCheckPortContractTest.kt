package pianoanchor.testing

import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Release
import pianoanchor.core.ports.UpdateCheckPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Behaviour every [UpdateCheckPort] adapter must satisfy. Subclass it once per adapter. */
abstract class UpdateCheckPortContractTest {
    /** A port whose source publishes [release] as the latest one. */
    protected abstract fun createPublishing(release: Release): UpdateCheckPort

    /** A port whose source fails in every way it can: offline, an error status, garbage. */
    protected abstract fun createFailing(): List<UpdateCheckPort>

    @Test
    fun returnsAReleaseOrNull() =
        runTest {
            val release = Release("0.2.0", "https://github.com/kaushikvapiwala/Piano-Anchor/releases/tag/v0.2.0")
            assertEquals(release, createPublishing(release).latest())
        }

    @Test
    fun neverThrowsForErrors() =
        runTest {
            createFailing().forEach { assertNull(it.latest()) }
        }
}
