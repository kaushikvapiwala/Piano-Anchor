package pianoanchor.testing

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.TonePort
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeToneContractTest : TonePortContractTest() {
    override fun createHarness(scope: TestScope): ToneHarness =
        object : ToneHarness {
            private val fake = FakeTone()
            override val tone: TonePort = fake
            override val sounding: Boolean get() = fake.playing
        }

    @Test
    fun recordsEveryCall() =
        runTest {
            val fake = FakeTone()
            val tones = listOf(Tone(MidiNote(60), 0, 100))
            fake.play(tones)
            fake.play(emptyList())
            assertEquals(listOf(tones, emptyList()), fake.played)
        }
}
