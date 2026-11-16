package pianoanchor.engine.midi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MidiPlaybackTest {
    private val device = MidiDevice("piano", "Piano")

    /** Logs "on60@0" or "off60@400" per send; from send [failFrom] on it throws as a vanished device does. */
    private class RecordingOutput(
        private val failFrom: Int = Int.MAX_VALUE,
        private val time: () -> Long,
    ) : MidiOutputPort {
        val log = ArrayList<String>()
        private var count = 0

        override val outputDevices: Flow<List<MidiDevice>> = flowOf(emptyList())

        override suspend fun send(
            device: MidiDevice,
            bytes: ByteArray,
        ) {
            count++
            if (count >= failFrom) throw MidiDeviceUnavailableException(device.id)
            val kind = if ((bytes[0].toInt() and 0xF0) == 0x90) "on" else "off"
            log += "$kind${bytes[1]}@${time()}"
        }
    }

    private fun tone(
        note: Int,
        start: Long,
        length: Long,
    ) = Tone(MidiNote(note), start, length)

    private val two = listOf(tone(60, 0, 400), tone(64, 500, 400))

    @Test
    fun playsEachTonesNoteOnAndNoteOffAtItsTimes() =
        runTest {
            val out = RecordingOutput { currentTime }
            out.playTones(device, two)
            assertEquals(listOf("on60@0", "off60@400", "on64@500", "off64@900"), out.log)
        }

    @Test
    fun usesThePlaybackVelocityOnChannelZero() =
        runTest {
            var sent: ByteArray? = null
            val out =
                object : MidiOutputPort {
                    override val outputDevices: Flow<List<MidiDevice>> = flowOf(emptyList())

                    override suspend fun send(
                        device: MidiDevice,
                        bytes: ByteArray,
                    ) {
                        if (sent == null) sent = bytes
                    }
                }
            out.playTones(device, listOf(tone(60, 0, 10)))
            assertEquals(listOf(0x90, 60, Tunables.PLAYBACK_VELOCITY), sent!!.map { it.toInt() and 0xFF })
        }

    @Test
    fun releasesBeforeStrikingARepeatedNoteAtTheSameTime() =
        runTest {
            val out = RecordingOutput { currentTime }
            out.playTones(device, listOf(tone(60, 0, 500), tone(60, 500, 400)))
            assertEquals(listOf("on60@0", "off60@500", "on60@500", "off60@900"), out.log)
        }

    @Test
    fun chordTonesSoundTogether() =
        runTest {
            val out = RecordingOutput { currentTime }
            out.playTones(device, listOf(60, 64, 67).map { tone(it, 0, 800) })
            assertEquals(
                listOf("on60@0", "on64@0", "on67@0", "off60@800", "off64@800", "off67@800"),
                out.log,
            )
        }

    @Test
    fun cancellingReleasesTheKeysStillDown() =
        runTest {
            val out = RecordingOutput { currentTime }
            val job = launch { out.playTones(device, two) }
            advanceTimeBy(200)
            runCurrent()
            job.cancel()
            runCurrent()
            assertEquals(listOf("on60@0", "off60@200"), out.log)
            advanceTimeBy(1000)
            assertEquals(2, out.log.size)
        }

    @Test
    fun aDeviceThatGoesAwayThrowsAndReleasingIgnoresIt() =
        runTest {
            val out = RecordingOutput(failFrom = 2) { currentTime }
            assertFailsWith<MidiDeviceUnavailableException> { out.playTones(device, two) }
            assertEquals(listOf("on60@0"), out.log)
            assertTrue(currentTime >= 400)
        }

    @Test
    fun emptyTonesSendNothing() =
        runTest {
            val out = RecordingOutput { currentTime }
            out.playTones(device, emptyList())
            assertEquals(emptyList(), out.log)
            assertEquals(0, currentTime)
        }
}
