package pianoanchor.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ChordMark
import pianoanchor.core.model.ChordQuality
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.guidance.slice
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.playalong.Backing
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeCalibrationRepository
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakeMetronome
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeTone
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The play-along backing of a timed run (`docs/algorithms/play-along.md`). CDE with a C chord over its bar, at 60 %: 48
 * BPM, a beat is 1250 ms, so bar 1 beat 1 sounds the lead plus one count-in bar after the run arms.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelBackingTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock).apply { inputOnly = true }
    private val attempts = FakeAttemptRepository()
    private val metronome = FakeMetronome(clock)
    private val tone = FakeTone()
    private val learner = LearnerModel(Curriculum.graph, FakeLearnerRepository())
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val f4 = MidiNote(65)
    private val ms = 1_000_000L
    private val beatMs = 1_250L
    private val leadMs = Tunables.TEMPO_COUNT_IN_LEAD_MS
    private val countInMs = leadMs + 4 * beatMs
    private val autoStartMs = Tunables.TEMPO_AUTO_START_DELAY_MS
    private val pauseMs = Tunables.TEMPO_RESTART_PAUSE_MS
    private var ids = 0
    private val opened = mutableListOf<PracticeViewModel>()

    private val chorded = Passages.CDE.copy(chords = listOf(ChordMark(0, 0, ChordQuality.MAJOR)))
    private val expectedTones get() = Backing.tones(chorded, 48)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.practice(
        passage: Passage = chorded,
        backing: Boolean = true,
        speaker: FakeTone? = tone,
        rung: TempoRung = TempoRung.WAIT,
        spot: BarRange? = null,
    ): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            spot?.let { passage.slice(it) } ?: passage,
            piano,
            piano,
            attempts,
            FakeCalibrationRepository(),
            metronome,
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            learner,
            TimeZone.UTC,
            FakeSettingsRepository(),
            NoteLabelsUseCase(FakeSettingsRepository(), attempts, learner, TimeZone.UTC),
            initialRung = rung,
            spot = spot,
            pieceId = passage.id,
            backingTone = speaker,
            initialBacking = backing,
        ) {
            "attempt-${++ids}"
        }.also {
            opened += it
            runCurrent()
        }

    private fun runPractice(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                opened.forEach { it.close() }
            }
        }

    private fun TestScope.advance(ms: Long) {
        repeat((ms / 50).toInt()) {
            clock.advanceNanos(50 * this@PracticeViewModelBackingTest.ms)
            advanceTimeBy(50)
            runCurrent()
        }
    }

    private fun TestScope.press(note: MidiNote) {
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    /** Lets the run arm: the count-in begins now; returns that moment. */
    private fun TestScope.arm(vm: PracticeViewModel): Long {
        vm.selectRung(TempoRung.P60)
        runCurrent()
        advance(autoStartMs)
        return clock.nowNanos()
    }

    @Test
    fun `the chords start on bar 1 beat 1, one bar after the click begins`() =
        runPractice {
            val vm = practice()
            val armedAt = arm(vm)

            assertEquals(armedAt + leadMs * ms, metronome.running.value?.firstBeatNanos)
            advance(countInMs - 50)
            assertTrue(tone.played.isEmpty())
            advance(50)
            assertEquals(listOf(expectedTones), tone.played)
            assertTrue(tone.playing)
            assertTrue(vm.state.value.backingOn)
        }

    @Test
    fun `with the chip off no chords play and the click runs as before`() =
        runPractice {
            val vm = practice()
            vm.toggleBacking()
            arm(vm)
            advance(countInMs + 50)

            assertFalse(vm.state.value.backingOn)
            assertTrue(tone.played.isEmpty())
            assertNotNull(metronome.running.value)
        }

    @Test
    fun `normal practice starts with the backing off but the chip on offer`() =
        runPractice {
            val vm = practice(backing = false)
            arm(vm)
            advance(countInMs + 50)

            assertTrue(vm.state.value.hasChords)
            assertFalse(vm.state.value.backingOn)
            assertTrue(tone.played.isEmpty())

            vm.toggleBacking()
            assertTrue(vm.state.value.backingOn)
        }

    @Test
    fun `the wait rung of the play-along opens on the slowest timed rung`() =
        runPractice {
            assertEquals(TempoRung.P60, practice().state.value.rung)
            assertEquals(TempoRung.P85, practice(rung = TempoRung.P85).state.value.rung)
            assertEquals(TempoRung.WAIT, practice(backing = false).state.value.rung)
            assertEquals(TempoRung.WAIT, practice(passage = Passages.CDE).state.value.rung)
        }

    @Test
    fun `the play-along run arms and plays by itself`() =
        runPractice {
            practice()
            advance(autoStartMs + countInMs)

            assertEquals(listOf(expectedTones), tone.played)
        }

    @Test
    fun `stopping the run stops the chords and the click`() =
        runPractice {
            val vm = practice()
            arm(vm)
            advance(countInMs + 50)
            assertTrue(tone.playing)

            vm.selectRung(TempoRung.WAIT)
            runCurrent()

            assertFalse(tone.playing)
            assertNull(metronome.running.value)
        }

    @Test
    fun `leaving stops the chords`() =
        runPractice {
            val vm = practice()
            arm(vm)
            advance(countInMs + 50)

            vm.close()

            assertFalse(tone.playing)
            assertNull(metronome.running.value)
        }

    @Test
    fun `the chip turned off mid-run stops the chords`() =
        runPractice {
            val vm = practice()
            arm(vm)
            advance(countInMs + 50)
            assertTrue(tone.playing)

            vm.toggleBacking()
            runCurrent()

            assertFalse(tone.playing)
            assertNotNull(metronome.running.value)
        }

    @Test
    fun `a wrong note restarts the chords with the new count-in`() =
        runPractice {
            val vm = practice()
            arm(vm)
            advance(countInMs)
            assertTrue(tone.playing)

            press(f4) // C is due: the run stops and starts again from the top
            assertFalse(tone.playing)

            advance(pauseMs + autoStartMs)
            assertEquals(1, tone.played.size)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
            advance(countInMs - 50)
            assertEquals(1, tone.played.size)
            advance(50)
            assertEquals(2, tone.played.size)
            assertTrue(tone.playing)
        }

    @Test
    fun `a clean run plays the chords once, grades as usual and ends with the chords`() =
        runPractice {
            val vm = practice()
            arm(vm)
            advance(countInMs)
            press(c4)
            advance(beatMs)
            press(d4)
            advance(beatMs)
            press(e4)
            advance(3_750)

            assertEquals(1, tone.played.size)
            assertFalse(tone.playing)
            assertNotNull(vm.state.value.finishedAttemptId)
            assertNotNull(attempts.get("attempt-1"))
        }

    @Test
    fun `a passage without chords has no chip and ignores the toggle`() =
        runPractice {
            val vm = practice(passage = Passages.CDE)
            vm.toggleBacking()
            arm(vm)
            advance(countInMs + 50)

            assertFalse(vm.state.value.hasChords)
            assertFalse(vm.state.value.backingOn)
            assertTrue(tone.played.isEmpty())
        }

    @Test
    fun `a spot loop slice has no chords to play`() =
        runPractice {
            val vm = practice(spot = BarRange(1, 1))
            arm(vm)
            advance(countInMs + 50)

            assertFalse(vm.state.value.hasChords)
            assertTrue(tone.played.isEmpty())
        }

    @Test
    fun `without a speaker the run goes on with the click alone`() =
        runPractice {
            val vm = practice(speaker = null)
            assertEquals(TempoRung.WAIT, vm.state.value.rung)
            vm.toggleBacking()
            arm(vm)
            advance(countInMs + 50)

            assertTrue(tone.played.isEmpty())
            assertNotNull(metronome.running.value)
        }

    @Test
    fun `a chord line that ends before it sounds plays nothing`() =
        runPractice {
            val late = Passages.CDE.copy(chords = listOf(ChordMark(3_840, 0, ChordQuality.MAJOR)))
            val vm = practice(passage = late)
            arm(vm)
            advance(countInMs + 50)

            assertTrue(tone.played.isEmpty())
        }
}
