package pianoanchor.testing

import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Release
import pianoanchor.core.ports.UpdateCheckPort
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeUpdateCheckContractTest : UpdateCheckPortContractTest() {
    override fun createPublishing(release: Release): UpdateCheckPort = FakeUpdateCheck(release)

    override fun createFailing(): List<UpdateCheckPort> = listOf(FakeUpdateCheck(null))

    @Test
    fun countsItsRequests() =
        runTest {
            val fake = FakeUpdateCheck()
            fake.latest()
            fake.latest()
            assertEquals(2, fake.calls)
        }
}
