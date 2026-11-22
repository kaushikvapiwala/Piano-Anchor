package pianoanchor.platform.androidaudio

import kotlin.test.Test
import kotlin.test.assertEquals

class FramesTest {
    @Test
    fun `converts milliseconds to whole frames`() {
        assertEquals(480, framesOf(48_000, 10))
        assertEquals(0, framesOf(48_000, 0))
    }

    @Test
    fun `truncates a partial frame`() {
        assertEquals(44, framesOf(44_100, 1))
    }
}
