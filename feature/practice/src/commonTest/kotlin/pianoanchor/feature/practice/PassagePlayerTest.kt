package pianoanchor.feature.practice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeTone
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PassagePlayerTest {
    private val piano = FakePiano()
    private val tone = FakeTone()
    private val tones =
        listOf(
            Tone(MidiNote(60), 0, 500),
            Tone(MidiNote(62), 750, 500),
            Tone(MidiNote(64), 1500, 500),
        )

    /** A piano output that accepts [accept] messages, then fails as a device that went away. */
    private fun flaky(accept: Int) =
        object : MidiOutputPort by piano {
            private var left = accept

            override suspend fun send(
                device: pianoanchor.core.model.MidiDevice,
                bytes: ByteArray,
            ) {
                if (left-- <= 0) throw MidiDeviceUnavailableException(device.id)
                piano.send(device, bytes)
            }
        }

    @Test
    fun aPianoThatFailsMidwayHandsOnlyTheRestToTheSpeakerOnTheSameTimeline() =
        runTest {
            val player = PassagePlayer(piano.device, flaky(accept = 2), tone) // C on, C off, then D fails

            player.play(tones)
            advanceUntilIdle()

            assertEquals(listOf(listOf(Tone(MidiNote(62), 0, 500), Tone(MidiNote(64), 750, 500))), tone.played)
        }

    @Test
    fun aFailureAfterTheLastNoteWasStruckLeavesNothingForTheSpeaker() =
        runTest {
            val player = PassagePlayer(piano.device, flaky(accept = 5), tone) // all three on, two off, last off fails

            player.play(tones)

            assertEquals(emptyList(), tone.played)
        }

    @Test
    fun onceFailedLaterPlaysGoStraightToTheSpeaker() =
        runTest {
            val player = PassagePlayer(piano.device, flaky(accept = 0), tone)

            player.play(tones)
            player.play(tones)

            assertEquals(listOf(tones, tones), tone.played)
        }
}
