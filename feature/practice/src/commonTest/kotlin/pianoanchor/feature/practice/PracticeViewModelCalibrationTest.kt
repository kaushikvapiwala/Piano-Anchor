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
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Passages
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeCalibrationRepository
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeMetronome
import pianoanchor.testing.FakePiano
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

/** The latency offset in practice: the calibrate card, and what a stored offset does to a timed run. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelCalibrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)
    private val attempts = FakeAttemptRepository()
    private val calibration = FakeCalibrationRepository()
    private val skips = CalibrationSkips()
    private val metronome = FakeMetronome(clock)
    private val wallClock = FakeWallClock(Instant.parse("2026-11-13T09:00:00Z"))
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val ms = 1_000_000L
    private val opened = mutableListOf<PracticeViewModel>()

    // CDE is written at 80 BPM, so the 60 % rung runs at 48 BPM: a beat lasts 1250 ms.
    private val beatMs = 1_250L
    private val countInMs = Tunables.TEMPO_COUNT_IN_LEAD_MS + 4 * beatMs
    private val runMs = 6_250L

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.practice(): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            attempts,
            calibration,
            metronome,
            clock,
            wallClock,
            calibrationSkips = skips,
        ) {
            "attempt-1"
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

    /** Moves the clock and virtual time together, 50 ms at a time, so the ViewModel's ticker sees every step. */
    private fun TestScope.advance(ms: Long) {
        repeat((ms / 50).toInt()) {
            clock.advanceNanos(50 * this@PracticeViewModelCalibrationTest.ms)
            advanceTimeBy(50)
            runCurrent()
        }
    }

    private fun TestScope.press(note: MidiNote) {
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    /** Holds [note] for [heldMs] (long enough to be neither short nor long), then lets [restMs] pass. */
    private fun TestScope.hold(
        note: MidiNote,
        heldMs: Long,
        restMs: Long,
    ) {
        piano.press(note)
        runCurrent()
        advance(heldMs)
        piano.release(note)
        runCurrent()
        advance(restMs)
    }

    /** Selects the 60 % rung, starts the run with a key and plays C D E, each [lateMs] after its written time. */
    private fun TestScope.playCde(
        vm: PracticeViewModel,
        lateMs: Long,
    ) {
        vm.selectRung(TempoRung.P60)
        runCurrent()
        press(c4) // the starting key
        advance(countInMs + lateMs)
        hold(c4, heldMs = 900, restMs = beatMs - 900)
        hold(d4, heldMs = 900, restMs = beatMs - 900)
        hold(e4, heldMs = 1_800, restMs = runMs)
    }

    @Test
    fun uncalibratedTimedRungShowsTheCalibrateCard() =
        runPractice {
            val vm = practice()
            assertFalse(vm.state.value.calibrationOffered) // wait mode has no offset to ask for

            vm.selectRung(TempoRung.P60)
            runCurrent()

            assertTrue(vm.state.value.calibrationOffered)
            assertEquals(RunPhase.READY, vm.state.value.phase)
            press(c4) // no reader is open while the card waits
            assertNull(metronome.running.value)

            vm.selectRung(TempoRung.WAIT)
            runCurrent()
            assertFalse(vm.state.value.calibrationOffered)
        }

    @Test
    fun aCalibratedPianoGetsNoCard() =
        runPractice {
            calibration.setLatencyOffsetNanos(piano.device.id, 38 * ms)
            val vm = practice()

            vm.selectRung(TempoRung.P60)
            runCurrent()

            assertFalse(vm.state.value.calibrationOffered)
            press(c4)
            assertNotNull(metronome.running.value)
        }

    @Test
    fun skipHidesItForTheSession() =
        runPractice {
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            runCurrent()

            vm.skipCalibration()
            runCurrent()

            assertFalse(vm.state.value.calibrationOffered)
            assertEquals(TempoRung.P60, vm.state.value.rung)
            press(c4)
            assertNotNull(metronome.running.value) // the run starts, uncalibrated

            // A new screen for the same piano in the same session: still no card.
            val again = practice()
            again.selectRung(TempoRung.P70)
            runCurrent()
            assertFalse(again.state.value.calibrationOffered)
        }

    @Test
    fun aStoreThatCannotBeReadPlaysUncalibratedWithoutTheCard() =
        runPractice {
            calibration.failing = true
            val vm = practice()

            vm.selectRung(TempoRung.P60)
            runCurrent()
            press(c4)
            advance(countInMs + runMs)

            assertFalse(vm.state.value.calibrationOffered)
            assertNull(attempts.get("attempt-1")?.latencyOffsetNanos)
        }

    @Test
    fun offsetShiftsLiveVerdictsAndGrading() =
        runPractice {
            calibration.setLatencyOffsetNanos(piano.device.id, 200 * ms)
            val vm = practice()

            // Every key reaches the app 200 ms late, as this piano's does: on time once the offset is subtracted.
            playCde(vm, lateMs = 200)

            assertEquals(
                ScoreNoteRef(0, c4) to LiveMark.Correct,
                vm.state.value.liveMarks.entries
                    .first()
                    .toPair(),
            )
            val attempt = assertNotNull(attempts.get("attempt-1"))
            assertEquals(1.0, attempt.accuracy)
            assertEquals(pianoanchor.core.model.Band.CLEAN, attempt.band)
        }

    @Test
    fun withoutAnOffsetTheSameTakeIsLate() =
        runPractice {
            skips.skip(piano.device.id)
            val vm = practice()

            playCde(vm, lateMs = 200)

            val mark =
                vm.state.value.liveMarks
                    .getValue(ScoreNoteRef(0, c4))
            assertEquals(LiveMark.TimingOff(200), mark)
            assertTrue(assertNotNull(attempts.get("attempt-1")).accuracy < 1.0)
        }

    @Test
    fun attemptStoresTheOffsetUsed() =
        runPractice {
            calibration.setLatencyOffsetNanos(piano.device.id, 38 * ms)
            val calibrated = practice()
            playCde(calibrated, lateMs = 0)
            assertEquals(38 * ms, attempts.get("attempt-1")?.latencyOffsetNanos)
        }

    @Test
    fun anAttemptAfterSkippingStoresNoOffset() =
        runPractice {
            skips.skip(piano.device.id)
            val vm = practice()
            playCde(vm, lateMs = 0)
            assertNull(assertNotNull(attempts.get("attempt-1")).latencyOffsetNanos)
        }

    @Test
    fun waitModeIgnoresTheOffset() =
        runPractice {
            calibration.setLatencyOffsetNanos(piano.device.id, 38 * ms)
            val vm = practice()
            listOf(c4, d4, e4).forEach {
                clock.advanceNanos(300 * ms)
                press(it)
            }
            assertNull(assertNotNull(attempts.get("attempt-1")).latencyOffsetNanos)
        }
}
