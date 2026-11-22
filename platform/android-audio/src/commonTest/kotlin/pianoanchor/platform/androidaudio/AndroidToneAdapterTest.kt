package pianoanchor.platform.androidaudio

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Tone
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidToneAdapterTest {
    @Test
    fun `plays the rendered tones to the sink and stops it afterwards`() =
        runTest {
            val sink = FakeAudioSink(virtualClock(this))
            val tones = listOf(Tone(MidiNote(69), 0, 100))
            AndroidToneAdapter(sink, StandardTestDispatcher(testScheduler)).play(tones)
            val rendered = ToneRenderer(sink.sampleRate).render(tones)
            val heard = sink.runs.single().frames
            assertEquals(rendered.toList(), heard.take(rendered.size))
            assertEquals(true, heard.drop(rendered.size).all { it.toInt() == 0 })
            assertEquals(1, sink.stops)
        }

    @Test
    fun `playing nothing does not touch the sink`() =
        runTest {
            val sink = FakeAudioSink(virtualClock(this))
            AndroidToneAdapter(sink, StandardTestDispatcher(testScheduler)).play(emptyList())
            assertEquals(0, sink.runs.size)
            assertEquals(0, sink.stops)
        }
}
