package pianoanchor.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.guidance.slice
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeCalibrationRepository
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakeMetronome
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeWallClock
import pianoanchor.testing.passageOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Spot loops (`docs/algorithms/guidance.md`): a 2-bar slice of a 3-bar test passage, looped at WAIT or a rung. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelSpotTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)

    // No MIDI output: a timed run takes the transport default at once instead of measuring the link.
    private val piano = FakePiano(clock).apply { inputOnly = true }
    private val attempts = FakeAttemptRepository()
    private val learnerRepository = FakeLearnerRepository()
    private val ms = 1_000_000L
    private var ids = 0

    // Twelve quarter notes (three bars of 4/4), pitch 60 + index; bars 2-3 are pitches 64..71.
    private val whole = passageOf(*Array(12) { setOf(MidiNote(60 + it)) }, id = "test:whole")
    private val range = BarRange(2, 3)
    private val wrongKey = MidiNote(40)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.loop(
        rung: TempoRung = TempoRung.WAIT,
        spot: BarRange? = range,
    ): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            spot?.let { whole.slice(it) } ?: whole,
            piano,
            piano,
            attempts,
            FakeCalibrationRepository(),
            FakeMetronome(clock),
            clock,
            FakeWallClock(Instant.parse("2026-11-13T09:00:00Z")),
            LearnerModel(Curriculum.graph, learnerRepository),
            TimeZone.UTC,
            FakeSettingsRepository(),
            rung,
            RunMode.PERFORM,
            spot,
            pieceId = whole.id,
        ) {
            "attempt-${++ids}"
        }.also { runCurrent() }

    private fun TestScope.tap(pitch: Int) {
        clock.advanceNanos(300 * ms)
        piano.press(MidiNote(pitch))
        piano.release(MidiNote(pitch))
        runCurrent()
    }

    /** Plays the eight notes of the slice, each of [wrong] first preceded by a wrong key. */
    private fun TestScope.playLoop(wrong: Int = 0) {
        repeat(wrong) { tap(wrongKey.number) }
        (64..71).forEach { tap(it) }
    }

    @Test
    fun `the loop starts one rung below the run's`() =
        runTest(dispatcher) {
            val vm = loop(TempoRung.P85)
            assertEquals(TempoRung.P70, vm.state.value.rung)
            assertEquals(
                TempoRung.P85,
                vm.state.value.spotLoop
                    ?.fullRung,
            )
            assertEquals(TempoRung.WAIT, loop(TempoRung.WAIT).state.value.rung)
        }

    @Test
    fun `a resumed link restarts a timed loop and keeps its streak`() =
        runTest(dispatcher) {
            val vm = loop(TempoRung.P85)
            try {
                val before = vm.state.value.spotLoop
                tap(60) // the starting key: the count-in begins
                assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)

                piano.dropLink()
                runCurrent()

                assertEquals(RunPhase.READY, vm.state.value.phase)
                assertEquals(TempoRung.P70, vm.state.value.rung)
                assertEquals(before, vm.state.value.spotLoop)
                assertTrue(vm.state.value.linkResumed)
            } finally {
                vm.close() // a timed run's ticker never ends on a standing fake clock
            }
        }

    @Test
    fun `a loop practises the slice, in practice mode`() =
        runTest(dispatcher) {
            val vm = loop(TempoRung.P85)
            assertEquals("test:whole#bars2-3", vm.state.value.passage.id)
            assertEquals(RunMode.PRACTICE, vm.state.value.mode)
            vm.selectMode(RunMode.PERFORM)
            runCurrent()
            assertEquals(RunMode.PRACTICE, vm.state.value.mode)
        }

    @Test
    fun spotLoopSavesUnderThePieceIdWithItsSpot() =
        runTest(dispatcher) {
            loop()
            playLoop()

            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(whole.id, saved.passageId)
            assertEquals(range, saved.spot)
            assertEquals(saved.startedAt, attempts.lastPlayed(whole.id))
        }

    @Test
    fun spotLoopShowsThePieceDoneRungs() =
        runTest(dispatcher) {
            val bpm = checkNotNull(TempoRung.P60.bpm(whole))
            attempts.save(
                Attempt(
                    id = "old-1",
                    passageId = whole.id,
                    deviceId = piano.device.id,
                    startedAt = Instant.fromEpochSeconds(1_000),
                    durationNanos = 1,
                    stepCount = 12,
                    wrongNotes = 0,
                    incompleteChords = 0,
                    accuracy = 1.0,
                    band = Band.CLEAN,
                    events = emptyList(),
                    tempoBpm = bpm,
                    playedNotes = emptyList(),
                ),
            )
            val vm = loop()
            playLoop()

            assertEquals(setOf(TempoRung.P60), vm.state.value.doneRungs)
        }

    @Test
    fun `loops restart without Results and carry spot`() =
        runTest(dispatcher) {
            val vm = loop()
            playLoop(wrong = 3)

            assertNull(vm.state.value.finishedAttemptId)
            val saved = assertNotNull(attempts.get("attempt-1"))
            assertEquals(range, saved.spot)
            assertEquals("test:whole", saved.passageId)
            assertEquals(RunMode.PRACTICE, saved.mode)
            // The next loop is already waiting for its first step.
            assertEquals(
                StepStatus.WAITING,
                vm.state.value.steps
                    .first()
                    .status,
            )
            assertEquals(
                LoopResult(clean = false, wrongNotes = 3, incompleteChords = 0),
                vm.state.value.spotLoop
                    ?.lastLoop,
            )
            playLoop()
            assertNotNull(attempts.get("attempt-2"))
            assertNull(vm.state.value.finishedAttemptId)
        }

    @Test
    fun `two clean loops in a row offer the full piece`() =
        runTest(dispatcher) {
            val vm = loop()
            playLoop()
            assertEquals(
                1,
                vm.state.value.spotLoop
                    ?.cleanLoops,
            )
            assertFalse(
                vm.state.value.spotLoop
                    ?.offerFull == true,
            )
            assertEquals(
                LoopResult(clean = true, wrongNotes = 0, incompleteChords = 0),
                vm.state.value.spotLoop
                    ?.lastLoop,
            )
            playLoop()
            assertEquals(
                2,
                vm.state.value.spotLoop
                    ?.cleanLoops,
            )
            assertTrue(
                vm.state.value.spotLoop
                    ?.offerFull == true,
            )
            assertNull(vm.state.value.finishedAttemptId)
            // No third loop starts by itself: the card waits.
            assertEquals(2, attemptCount())
        }

    @Test
    fun `a non-clean loop resets the streak`() =
        runTest(dispatcher) {
            val vm = loop()
            playLoop()
            playLoop(wrong = 3)
            assertEquals(
                0,
                vm.state.value.spotLoop
                    ?.cleanLoops,
            )
            playLoop()
            assertEquals(
                1,
                vm.state.value.spotLoop
                    ?.cleanLoops,
            )
            assertFalse(
                vm.state.value.spotLoop
                    ?.offerFull == true,
            )
            assertEquals(Band.CLEAN, attempts.get("attempt-3")?.band)
        }

    @Test
    fun `keep looping starts again from zero clean loops`() =
        runTest(dispatcher) {
            val vm = loop()
            playLoop()
            playLoop()
            vm.keepLooping()
            runCurrent()
            assertEquals(
                0,
                vm.state.value.spotLoop
                    ?.cleanLoops,
            )
            assertFalse(
                vm.state.value.spotLoop
                    ?.offerFull == true,
            )
            playLoop()
            assertEquals(
                1,
                vm.state.value.spotLoop
                    ?.cleanLoops,
            )
            assertEquals(3, attemptCount())
        }

    @Test
    fun `a new rung mid-loop starts the clean streak again`() =
        runTest(dispatcher) {
            val vm = loop()
            playLoop()
            playLoop()
            vm.selectRung(TempoRung.WAIT)
            runCurrent()
            assertEquals(
                0,
                vm.state.value.spotLoop
                    ?.cleanLoops,
            )
            assertFalse(
                vm.state.value.spotLoop
                    ?.offerFull == true,
            )
        }

    @Test
    fun `keep looping does nothing outside a spot loop`() =
        runTest(dispatcher) {
            val vm = loop(spot = null)
            vm.keepLooping()
            runCurrent()
            assertNull(vm.state.value.spotLoop)
        }

    @Test
    fun `loops are never cold checks`() =
        runTest(dispatcher) {
            loop().also { playLoop() }
            assertTrue(learnerRepository.states().isEmpty())
        }

    @Test
    fun `a loop that cannot be saved offers retry and does not count`() =
        runTest(dispatcher) {
            val vm = loop()
            attempts.failing = true
            playLoop()
            assertTrue(vm.state.value.saveFailed)
            attempts.failing = false
            vm.retrySave()
            runCurrent()
            assertFalse(vm.state.value.saveFailed)
            assertEquals(
                1,
                vm.state.value.spotLoop
                    ?.cleanLoops,
            )
        }

    @Test
    fun `a full run is unchanged`() =
        runTest(dispatcher) {
            val vm = loop(spot = null)
            (60..71).forEach { tap(it) }
            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
            assertNull(attempts.get("attempt-1")?.spot)
        }

    private suspend fun attemptCount() = (1..ids).count { attempts.get("attempt-$it") != null }
}
