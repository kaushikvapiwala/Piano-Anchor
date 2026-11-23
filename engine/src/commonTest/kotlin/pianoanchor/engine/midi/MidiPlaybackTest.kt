package pianoanchor.engine.midi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Tone
import pianoanchor.engine.Tunables
import pianoanchor.testing.FakePiano
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MidiPlaybackTest {
    private val piano = FakePiano()
    private val log = ArrayList<String>()

    /** Logs each delivered message as "on60@0" or "off60@400", at the test's virtual time. */
    private fun TestScope.logSends() {
        piano.replyTo = { bytes ->
            val kind = if ((bytes[0].toInt() and 0xF0) == 0x90) "on" else "off"
            log += "$kind${bytes[1]}@$currentTime"
            null
        }
    }

    private fun tone(
        note: Int,
        start: Long,
        length: Long,
    ) = Tone(MidiNote(note), start, length)

    private val two = listOf(tone(60, 0, 400), tone(64, 500, 400))

    @Test
    fun canPlayOnADeviceTheOutputListsAndNotOneItLacks() =
        runTest {
            assertTrue(piano.canPlayOn(piano.device))
            assertFalse(piano.canPlayOn(MidiDevice(id = "other", name = "Other")))
            piano.inputOnly = true
            assertFalse(piano.canPlayOn(piano.device))
        }

    @Test
    fun playsEachTonesNoteOnAndNoteOffAtItsTimes() =
        runTest {
            logSends()
            piano.playTones(piano.device, two)
            assertEquals(listOf("on60@0", "off60@400", "on64@500", "off64@900"), log)
        }

    @Test
    fun usesThePlaybackVelocityOnChannelZero() =
        runTest {
            piano.playTones(piano.device, listOf(tone(60, 0, 10)))
            assertEquals(
                listOf(listOf(0x90, 60, Tunables.PLAYBACK_VELOCITY), listOf(0x80, 60, 0)),
                piano.sent.map { message -> message.map { it.toInt() and 0xFF } },
            )
        }

    @Test
    fun releasesBeforeStrikingARepeatedNoteAtTheSameTime() =
        runTest {
            logSends()
            piano.playTones(piano.device, listOf(tone(60, 0, 500), tone(60, 500, 400)))
            assertEquals(listOf("on60@0", "off60@500", "on60@500", "off60@900"), log)
        }

    @Test
    fun chordTonesSoundTogether() =
        runTest {
            logSends()
            piano.playTones(piano.device, listOf(60, 64, 67).map { tone(it, 0, 800) })
            assertEquals(listOf("on60@0", "on64@0", "on67@0", "off60@800", "off64@800", "off67@800"), log)
        }

    @Test
    fun cancellingReleasesTheKeysStillDown() =
        runTest {
            logSends()
            val job = launch { piano.playTones(piano.device, two) }
            advanceTimeBy(200)
            job.cancel()
            runCurrent()
            assertEquals(listOf("on60@0", "off60@200"), log)
            advanceTimeBy(1000)
            assertEquals(2, log.size)
        }

    @Test
    fun aDeviceThatGoesAwayThrows() =
        runTest {
            logSends()
            val job = launch { assertFailsWith<MidiDeviceUnavailableException> { piano.playTones(piano.device, two) } }
            advanceTimeBy(200)
            piano.failSends = true
            advanceTimeBy(1000)
            assertTrue(job.isCompleted)
            assertEquals(listOf("on60@0"), log)
        }

    @Test
    fun onStruckCountsTheNotesBegun() =
        runTest {
            var struck = 0
            piano.playTones(piano.device, two) { struck++ }
            assertEquals(2, struck)
        }

    @Test
    fun cancellingAfterTheDeviceWentAwayStopsQuietly() =
        runTest {
            logSends()
            val job = launch { piano.playTones(piano.device, listOf(60, 64).map { tone(it, 0, 800) }) }
            advanceTimeBy(200)
            piano.failSends = true
            job.cancel()
            runCurrent()
            assertTrue(job.isCancelled && job.isCompleted)
            assertEquals(listOf("on60@0", "on64@0"), log)
        }

    @Test
    fun emptyTonesSendNothing() =
        runTest {
            piano.playTones(piano.device, emptyList())
            assertEquals(emptyList(), piano.sent)
            assertEquals(0, currentTime)
        }
}
