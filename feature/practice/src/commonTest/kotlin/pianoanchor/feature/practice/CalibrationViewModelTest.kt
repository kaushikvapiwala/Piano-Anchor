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
import pianoanchor.engine.Tunables
import pianoanchor.engine.calibration.CalibrationResult
import pianoanchor.engine.calibration.LatencyCalibration
import pianoanchor.testing.FakeCalibrationRepository
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeMetronome
import pianoanchor.testing.FakePiano
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)
    private val metronome = FakeMetronome(clock)
    private val repository = FakeCalibrationRepository()
    private val c4 = MidiNote(60)
    private val ms = 1_000_000L
    private val beat = 60_000_000_000L / Tunables.CALIBRATION_BPM

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.calibration(): CalibrationViewModel =
        CalibrationViewModel(piano.device, piano, metronome, clock, repository).also { runCurrent() }

    /** Moves the clock and virtual time together, a millisecond at a time, so the ViewModel's waits see every step. */
    private fun TestScope.advanceTo(nanos: Long) {
        while (clock.nowNanos() < nanos) {
            clock.advanceNanos(minOf(ms, nanos - clock.nowNanos()))
            advanceTimeBy(1)
            runCurrent()
        }
    }

    private fun TestScope.tap() {
        piano.press(c4)
        piano.release(c4)
        runCurrent()
    }

    /** Starts the run and taps every click [lateMs] late, skipping the clicks in [skipped]; returns when it is over. */
    private fun TestScope.runWithTaps(
        vm: CalibrationViewModel,
        lateMs: Long,
        skipped: Set<Int> = emptySet(),
    ) {
        val clicks = LatencyCalibration.clickTimes(clock.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS * ms)
        vm.start()
        runCurrent()
        clicks.forEachIndexed { i, click ->
            advanceTo(click + lateMs * ms)
            if (i !in skipped) tap()
        }
        advanceTo(clicks.last() + beat)
    }

    @Test
    fun startPlaysThePattern() =
        runTest(dispatcher) {
            val vm = calibration()
            val first = clock.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS * ms

            vm.start()
            runCurrent()
            vm.start() // ignored while running

            assertEquals(FakeMetronome.Pattern(first, beat, beatsPerBar = 1), metronome.running.value)
            assertEquals(CalibrationPhase.RUNNING, vm.state.value.phase)
            assertEquals(0, vm.state.value.click)
            assertTrue(vm.state.value.warmingUp)
            assertEquals(1, metronome.clicksUntil(first + beat).count { it.nanos == first })
            vm.close()
        }

    @Test
    fun theCountersFollowTheClicksAndTaps() =
        runTest(dispatcher) {
            val vm = calibration()
            val clicks = LatencyCalibration.clickTimes(clock.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS * ms)
            vm.start()
            runCurrent()

            advanceTo(clicks[0] + ms)
            assertEquals(1, vm.state.value.click)
            assertTrue(vm.state.value.pulse)
            advanceTo(clicks[0] + beat / 2 + ms)
            assertFalse(vm.state.value.pulse)

            advanceTo(clicks[Tunables.CALIBRATION_WARMUP_CLICKS - 1] + 38 * ms)
            tap()
            assertEquals(0, vm.state.value.countedTaps) // a warm-up tap
            assertTrue(vm.state.value.warmingUp)

            advanceTo(clicks[Tunables.CALIBRATION_WARMUP_CLICKS] + 38 * ms)
            tap()
            assertEquals(1, vm.state.value.countedTaps)
            assertFalse(vm.state.value.warmingUp)
            vm.close()
        }

    @Test
    fun acceptedResultIsSaved() =
        runTest(dispatcher) {
            val vm = calibration()

            runWithTaps(vm, lateMs = 38)

            val state = vm.state.value
            assertEquals(CalibrationPhase.DONE, state.phase)
            assertEquals(CalibrationResult.Accepted(38 * ms, 0), state.result)
            assertEquals(CalibrationState.COUNTED_CLICKS, state.countedTaps)
            assertEquals(Tunables.CALIBRATION_CLICKS, state.click)
            assertFalse(state.saveFailed)
            assertEquals(38 * ms, repository.latencyOffsetNanos(piano.device.id))
            assertNull(metronome.running.value)
        }

    @Test
    fun failedResultIsNotSavedAndOffersTryAgain() =
        runTest(dispatcher) {
            val vm = calibration()

            runWithTaps(vm, lateMs = 38, skipped = (0 until Tunables.CALIBRATION_CLICKS).toSet())

            assertEquals(CalibrationResult.TooFewTaps(0), vm.state.value.result)
            assertEquals(CalibrationPhase.DONE, vm.state.value.phase)
            assertNull(repository.latencyOffsetNanos(piano.device.id))

            vm.start() // Try again
            runCurrent()
            assertEquals(CalibrationPhase.RUNNING, vm.state.value.phase)
            assertNull(vm.state.value.result)
            vm.close()
        }

    @Test
    fun anOffsetThatCannotBeStoredIsReported() =
        runTest(dispatcher) {
            repository.failing = true
            val vm = calibration()

            runWithTaps(vm, lateMs = 38)

            assertEquals(CalibrationResult.Accepted(38 * ms, 0), vm.state.value.result)
            assertTrue(vm.state.value.saveFailed)
        }

    @Test
    fun cancelStopsTheMetronomeAndSavesNothing() =
        runTest(dispatcher) {
            val vm = calibration()
            val clicks = LatencyCalibration.clickTimes(clock.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS * ms)
            vm.start()
            runCurrent()
            advanceTo(clicks[Tunables.CALIBRATION_WARMUP_CLICKS] + 38 * ms)
            tap()

            vm.close()

            assertNull(metronome.running.value)
            assertNull(repository.latencyOffsetNanos(piano.device.id))
            assertEquals(CalibrationPhase.RUNNING, vm.state.value.phase)
        }

    @Test
    fun closingWithoutARunDoesNothing() =
        runTest(dispatcher) {
            calibration().close()
            assertNull(metronome.running.value)
        }

    @Test
    fun aPianoThatDropsMidRunEndsTheRunWithoutAResult() =
        runTest(dispatcher) {
            val vm = calibration()
            vm.start()
            runCurrent()
            advanceTo(clock.nowNanos() + 2_000 * ms)

            piano.unplug()
            runCurrent()

            assertTrue(vm.state.value.disconnected)
            assertEquals(CalibrationPhase.READY, vm.state.value.phase)
            assertNull(vm.state.value.result)
            assertNull(metronome.running.value)
        }

    @Test
    fun aPianoThatIsGoneWhenTheRunOpensItEndsTheRun() =
        runTest(dispatcher) {
            val vm = calibration()
            piano.unplug()

            vm.start()
            runCurrent()

            assertTrue(vm.state.value.disconnected)
            assertNull(metronome.running.value)
        }
}
