package pianoanchor.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.SysEx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FakePianoTest {
    private val c4 = MidiNote.MIDDLE_C

    @Test
    fun pressAndReleaseEmitNoteEvents() =
        runTest {
            val piano = FakePiano()
            val events = collect(piano)
            piano.press(c4, velocity = 90, channel = 2)
            piano.release(c4, velocity = 30, channel = 2)
            piano.press(c4)
            piano.release(c4)
            assertEquals(NoteOn(0, 2, c4, 90), events[0])
            assertEquals(NoteOff(0, 2, c4, 30), events[1])
            assertEquals(DEFAULT_VELOCITY, (events[2] as NoteOn).velocity)
            assertEquals(DEFAULT_VELOCITY, (events[3] as NoteOff).velocity)
        }

    @Test
    fun eventsAreStampedWithTheFakeClock() =
        runTest {
            val piano = FakePiano()
            val events = collect(piano)
            piano.press(c4)
            piano.clock.advanceNanos(1_000_000)
            piano.press(c4)
            assertEquals(1_000_000, events[1].timestampNanos - events[0].timestampNanos)
        }

    @Test
    fun replyIsPlayedBackAsInput() =
        runTest {
            val piano = FakePiano()
            val reply = listOf(0x7E, 0x10, 0x06, 0x02)
            piano.replyTo = { request ->
                if (request.size == IDENTITY_REQUEST.size) {
                    byteArrayOf(0xF0.toByte()) + reply.map { it.toByte() } + byteArrayOf(0xF7.toByte())
                } else {
                    null
                }
            }
            val events = collect(piano)
            piano.send(piano.device, byteArrayOf(0x90.toByte(), 60, 100))
            assertTrue(events.isEmpty())
            piano.send(piano.device, IDENTITY_REQUEST)
            assertEquals(reply, assertIs<SysEx>(events.single()).payload)
        }

    @Test
    fun sentKeepsCopies() =
        runTest {
            val piano = FakePiano()
            val bytes = byteArrayOf(0x90.toByte(), 60, 100)
            piano.send(piano.device, bytes)
            bytes[1] = 61
            piano.sent[0][2] = 1
            assertEquals(listOf<Byte>(0x90.toByte(), 60, 100), piano.sent[0].toList())
        }

    @Test
    fun plugInListsDeviceAgainAndResetsParser() =
        runTest {
            val piano = FakePiano()
            piano.play(byteArrayOf(0x90.toByte(), 60))
            piano.unplug()
            piano.plugIn()
            assertEquals(listOf(piano.device), piano.inputDevices.first())
            val events = collect(piano)
            piano.play(byteArrayOf(0x90.toByte(), 62, 100))
            assertEquals(1, events.size)
            assertEquals(MidiNote(62), (events.single() as NoteOn).note)
        }

    @Test
    fun bytesPlayedWithoutCollectorAreDropped() =
        runTest {
            val piano = FakePiano()
            piano.press(c4)
            val events = collect(piano)
            assertTrue(events.isEmpty())
            piano.release(c4)
            assertEquals(1, events.size)
        }

    @Test
    fun bytesPlayedWhileUnpluggedAreIgnored() =
        runTest {
            val piano = FakePiano()
            piano.unplug()
            piano.press(c4)
            piano.plugIn()
            val events = collect(piano)
            piano.release(c4)
            assertEquals(1, events.size)
        }

    @Test
    fun unpluggedDeviceCannotBeOpenedOrSentTo() =
        runTest {
            val piano = FakePiano()
            piano.unplug()
            assertFailsWith<MidiDeviceUnavailableException> { piano.events(piano.device).first() }
            assertFailsWith<MidiDeviceUnavailableException> {
                piano.send(piano.device, byteArrayOf(0x90.toByte(), 60, 100))
            }
            assertTrue(piano.sent.isEmpty())
        }

    @Test
    fun badBytesRejectedEvenWhenUnplugged() =
        runTest {
            val piano = FakePiano()
            piano.unplug()
            assertFailsWith<IllegalArgumentException> { piano.send(piano.device, byteArrayOf()) }
            assertFailsWith<IllegalArgumentException> { piano.send(piano.device, byteArrayOf(60, 100)) }
        }

    @Test
    fun customDeviceIsListed() =
        runTest {
            val custom = MidiDevice("x", "X")
            assertEquals(listOf(custom), FakePiano(device = custom).outputDevices.first())
        }

    private fun TestScope.collect(piano: FakePiano): MutableList<MidiEvent> {
        val events = mutableListOf<MidiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            piano.events(piano.device).collect { events.add(it) }
        }
        return events
    }

    private companion object {
        val IDENTITY_REQUEST = byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte())
    }
}
