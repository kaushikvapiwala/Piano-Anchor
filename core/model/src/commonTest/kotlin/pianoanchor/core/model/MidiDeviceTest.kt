package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MidiDeviceTest {
    @Test
    fun rejectsBlankId() {
        assertFailsWith<IllegalArgumentException> { MidiDevice(id = " ", name = "x") }
    }

    @Test
    fun keepsIdAndName() {
        val device = MidiDevice(id = "d1", name = "Piano")
        assertEquals("d1", device.id)
        assertEquals("Piano", device.name)
    }

    @Test
    fun unavailableExceptionNamesTheDevice() {
        val e = MidiDeviceUnavailableException("abc")
        assertEquals("abc", e.deviceId)
        assertEquals("MIDI device 'abc' is not available", e.message)
    }
}
