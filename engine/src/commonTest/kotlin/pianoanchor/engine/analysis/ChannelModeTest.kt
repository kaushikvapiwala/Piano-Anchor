package pianoanchor.engine.analysis

import pianoanchor.core.model.ControlChange
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelModeTest {
    private fun cc(controller: Int) = ControlChange(0, 0, controller, 0)

    @Test
    fun allSoundOffReleasesAndSilences() {
        assertEquals(true to true, cc(120).let { it.releasesAllKeys() to it.silencesAll() })
    }

    @Test
    fun allNotesOffAndModeChangesReleaseButDoNotSilence() {
        listOf(123, 124, 127).forEach { controller ->
            assertEquals(
                true to false,
                cc(controller).let { it.releasesAllKeys() to it.silencesAll() },
                "CC$controller",
            )
        }
    }

    @Test
    fun otherControllersDoNeither() {
        listOf(64, 122, 0).forEach { controller ->
            assertEquals(
                false to false,
                cc(controller).let { it.releasesAllKeys() to it.silencesAll() },
                "CC$controller",
            )
        }
    }
}
