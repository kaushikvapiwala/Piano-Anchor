package pianoanchor.testing

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.ports.MidiOutputPort
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behaviour every [MidiOutputPort] adapter must satisfy. Subclass it once per adapter. */
abstract class MidiOutputPortContractTest {
    protected interface OutputHarness {
        val port: MidiOutputPort
        val device: MidiDevice
        val received: List<ByteArray> // what reached the instrument, in order

        /** Disconnects the device; returns after it left the device list. */
        suspend fun unplug()
    }

    protected abstract fun createHarness(): OutputHarness

    @Test
    fun listsTheConnectedDevice() =
        runTest {
            val h = createHarness()
            assertTrue(h.device in h.port.outputDevices.first())
        }

    @Test
    fun unplugRemovesTheDevice() =
        runTest {
            val h = createHarness()
            h.unplug()
            assertFalse(h.device in h.port.outputDevices.first())
        }

    @Test
    fun sendsBytesInOrder() =
        runTest {
            val h = createHarness()
            val noteOn = NOTE_ON_C4
            val identityRequest = IDENTITY_REQUEST
            h.port.send(h.device, noteOn)
            h.port.send(h.device, identityRequest)
            assertEquals(2, h.received.size)
            assertContentEquals(noteOn, h.received[0])
            assertContentEquals(identityRequest, h.received[1])
        }

    @Test
    fun rejectsEmptyMessage() =
        runTest {
            val h = createHarness()
            assertFailsWith<IllegalArgumentException> { h.port.send(h.device, byteArrayOf()) }
        }

    @Test
    fun rejectsMessageWithoutStatusByte() =
        runTest {
            val h = createHarness()
            assertFailsWith<IllegalArgumentException> { h.port.send(h.device, byteArrayOf(DATA_ONLY, DATA_ONLY)) }
        }

    @Test
    fun sendingToAnAbsentDeviceFails() =
        runTest {
            val h = createHarness()
            val missing = MidiDevice("missing", "x")
            assertFailsWith<MidiDeviceUnavailableException> { h.port.send(missing, NOTE_ON_C4) }
        }

    private companion object {
        const val DATA_ONLY: Byte = 60
        val NOTE_ON_C4 = byteArrayOf(0x90.toByte(), 60, 100)
        val IDENTITY_REQUEST = byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte())
    }
}
