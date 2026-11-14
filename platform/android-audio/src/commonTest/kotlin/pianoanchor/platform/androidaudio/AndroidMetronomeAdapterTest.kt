package pianoanchor.platform.androidaudio

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val FRAMES_PER_MS = 48 // FakeAudioSink's 48 kHz

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMetronomeAdapterTest {
    private class Subject(
        val scope: TestScope,
    ) {
        val sink = FakeAudioSink(virtualClock(scope))
        val renderer = ClickRenderer(sink.sampleRate)
        val metronome = AndroidMetronomeAdapter(sink, virtualClock(scope), scope.backgroundScope)

        fun runUntilMs(millis: Long) {
            scope.advanceTimeBy(millis - scope.currentTime)
            scope.runCurrent()
        }

        fun onsets(run: Int = 0) = sink.onsets(sink.runs[run], renderer)
    }

    @Test
    fun clicksLandAtTheFramesMappedFromBeatTimes() =
        runTest {
            val subject = Subject(this)
            subject.sink.nextTimestamp = FrameTime(framePosition = 0, nanoTime = ms(2))
            subject.metronome.start(ms(100), ms(100), 4)
            subject.runUntilMs(350)
            assertEquals(
                listOf(98 * FRAMES_PER_MS to true, 198 * FRAMES_PER_MS to false, 298 * FRAMES_PER_MS to false),
                subject.onsets(),
            )
        }

    @Test
    fun accentEveryBeatsPerBarStartingWithTheFirst() =
        runTest {
            val subject = Subject(this)
            subject.metronome.start(ms(10), ms(20), 3)
            subject.runUntilMs(200)
            assertEquals(
                listOf(true, false, false, true, false, false, true, false, false, true),
                subject.onsets().map { it.second },
            )
        }

    @Test
    fun pastBeatsAreSkippedKeepingThePhase() =
        runTest {
            val subject = Subject(this)
            subject.runUntilMs(100)
            subject.metronome.start(ms(30), ms(20), 3)
            subject.runUntilMs(200)
            val run = subject.sink.runs.single()
            assertEquals(
                listOf(ms(110) to false, ms(130) to false, ms(150) to true, ms(170) to false, ms(190) to false),
                subject.onsets().map { (frame, accent) -> subject.sink.timeOf(run, frame) to accent },
            )
        }

    @Test
    fun mappingUsesTheLatestTimestamp() =
        runTest {
            val subject = Subject(this)
            subject.metronome.start(ms(100), ms(100), 1)
            subject.runUntilMs(150)
            // Frame 7200 (150 ms after the first write) was heard at 153 ms: later clicks move 3 ms earlier.
            subject.sink.nextTimestamp = FrameTime(framePosition = 150 * FRAMES_PER_MS.toLong(), nanoTime = ms(153))
            subject.runUntilMs(350)
            assertEquals(
                listOf(100 * FRAMES_PER_MS, 197 * FRAMES_PER_MS, 297 * FRAMES_PER_MS),
                subject.onsets().map { it.first },
            )
        }

    @Test
    fun aBeatTheTimestampMovesIntoThePastIsSkipped() =
        runTest {
            val subject = Subject(this)
            subject.metronome.start(ms(100), ms(100), 1)
            subject.runUntilMs(95)
            // Frame 4320 (90 ms after the first write) was heard at 100 ms: the 100 ms beat's frame is already written.
            subject.sink.nextTimestamp = FrameTime(framePosition = 90 * FRAMES_PER_MS.toLong(), nanoTime = ms(100))
            subject.runUntilMs(250)
            assertEquals(listOf(190 * FRAMES_PER_MS), subject.onsets().map { it.first })
        }

    @Test
    fun beforeAnyTimestampTheFirstChunkIsTimeZero() =
        runTest {
            val subject = Subject(this)
            subject.runUntilMs(40)
            subject.metronome.start(ms(50), ms(1_000), 4)
            subject.runUntilMs(100)
            assertEquals(
                ms(40),
                subject.sink.runs
                    .single()
                    .startNanos,
            )
            assertEquals(listOf(10 * FRAMES_PER_MS to true), subject.onsets())
        }

    @Test
    fun stopWritesNoMoreClicks() =
        runTest {
            val subject = Subject(this)
            subject.metronome.start(ms(10), ms(20), 4)
            subject.runUntilMs(100)
            subject.metronome.stop()
            val written =
                subject.sink.runs
                    .single()
                    .frames.size
            subject.runUntilMs(1_000)
            subject.metronome.stop()
            assertEquals(
                written,
                subject.sink.runs
                    .single()
                    .frames.size,
            )
            assertEquals(1, subject.sink.stops)
        }

    @Test
    fun startAfterStopBeginsANewStreamAtFrameZero() =
        runTest {
            val subject = Subject(this)
            subject.metronome.start(ms(10), ms(20), 4)
            subject.runUntilMs(100)
            subject.metronome.stop()
            subject.runUntilMs(1_100)
            subject.metronome.start(ms(1_105), ms(1_000), 4)
            subject.runUntilMs(1_150)
            assertEquals(ms(1_100), subject.sink.runs[1].startNanos)
            assertEquals(listOf(5 * FRAMES_PER_MS to true), subject.onsets(run = 1))
        }

    @Test
    fun aFailedWriterIsReplacedByTheNextStart() =
        runTest {
            val failures = mutableListOf<Throwable>()
            val scope =
                CoroutineScope(
                    StandardTestDispatcher(testScheduler) + SupervisorJob() +
                        CoroutineExceptionHandler { _, e -> failures += e },
                )
            val sink = FakeAudioSink(virtualClock(this))
            val metronome = AndroidMetronomeAdapter(sink, virtualClock(this), scope)
            sink.failNextWrite = true
            metronome.start(ms(50), ms(1_000), 4)
            advanceTimeBy(20)
            assertEquals(1, failures.size)
            metronome.start(ms(50), ms(1_000), 4)
            advanceTimeBy(80)
            runCurrent()
            metronome.stop()
            assertEquals(2, sink.stops) // the reset before the new writer, then stop
            assertEquals(
                listOf(30 * FRAMES_PER_MS to true),
                sink.onsets(sink.runs.single(), ClickRenderer(sink.sampleRate)),
            )
        }

    private fun ms(millis: Long) = millis * NANOS_PER_MS
}
