package pianoanchor.feature.practice

import kotlinx.coroutines.CompletableDeferred
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

    /** Taps every click but the last on time and waits for the last click's half beat; returns the clicks. */
    private fun TestScope.runUntilLastHalfBeat(vm: CalibrationViewModel): List<Long> {
        val clicks = LatencyCalibration.clickTimes(clock.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS * ms)
        vm.start()
        runCurrent()
        clicks.dropLast(1).forEach { click ->
            advanceTo(click + 38 * ms)
            tap()
        }
        advanceTo(clicks.last() + beat / 2)
        return clicks
    }

    private val grace = Tunables.CALIBRATION_LAST_TAP_GRACE_MS * ms

    @Test
    fun aLastTapTimestampedInTimeButDeliveredLateStillCounts() =
        runTest(dispatcher) {
            val vm = calibration()
            val clicks = runUntilLastHalfBeat(vm)
            val before = vm.state.value.countedTaps

            advanceTo(clock.nowNanos() + 100 * ms)
            piano.press(c4, stampNanos = clicks.last() + 30 * ms)
            runCurrent()
            assertEquals(before + 1, vm.state.value.countedTaps)

            advanceTo(clock.nowNanos() + grace)
            val accepted = vm.state.value.result as CalibrationResult.Accepted
            assertEquals(38 * ms, accepted.offsetNanos)
            assertEquals(CalibrationState.COUNTED_CLICKS, vm.state.value.countedTaps)
            assertEquals(accepted.offsetNanos, repository.latencyOffsetNanos(piano.device.id))
        }

    @Test
    fun aTapDeliveredAfterTheGraceIsNotCounted() =
        runTest(dispatcher) {
            val vm = calibration()
            val clicks = runUntilLastHalfBeat(vm)
            val before = vm.state.value.countedTaps

            advanceTo(clock.nowNanos() + 250 * ms)
            piano.press(c4, stampNanos = clicks.last() + 30 * ms)
            runCurrent()

            assertEquals(CalibrationPhase.DONE, vm.state.value.phase)
            assertEquals(before, vm.state.value.countedTaps)
            assertEquals(CalibrationResult.Accepted(38 * ms, 0), vm.state.value.result)
        }

    @Test
    fun theGraceDoesNotWidenTheWindow() =
        runTest(dispatcher) {
            val vm = calibration()
            val clicks = runUntilLastHalfBeat(vm)
            val before = vm.state.value.countedTaps

            advanceTo(clock.nowNanos() + 100 * ms)
            piano.press(c4, stampNanos = clicks.last() + beat / 2 + 50 * ms)
            runCurrent()

            assertEquals(before, vm.state.value.countedTaps)
            advanceTo(clock.nowNanos() + grace)
            assertEquals(CalibrationResult.Accepted(38 * ms, 0), vm.state.value.result)
        }

    @Test
    fun aPianoThatDropsDuringTheGraceEndsTheRunWithoutAResult() =
        runTest(dispatcher) {
            val vm = calibration()
            runUntilLastHalfBeat(vm)
            advanceTo(clock.nowNanos() + 100 * ms)

            piano.unplug()
            runCurrent()

            assertTrue(vm.state.value.disconnected)
            assertNull(vm.state.value.result)
            assertNull(repository.latencyOffsetNanos(piano.device.id))
            assertNull(metronome.running.value)
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
    fun theTapCounterCountsEachAnsweredClickOnce() =
        runTest(dispatcher) {
            val vm = calibration()
            val clicks = LatencyCalibration.clickTimes(clock.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS * ms)
            vm.start()
            runCurrent()
            val first = Tunables.CALIBRATION_WARMUP_CLICKS

            advanceTo(clicks[first] + 38 * ms)
            tap()
            advanceTo(clicks[first] + 90 * ms)
            tap() // a second tap on the same click
            assertEquals(1, vm.state.value.countedTaps)

            advanceTo(clicks[first] + beat / 2 + 20 * ms) // nearer the next click: that one's tap
            tap()
            assertEquals(2, vm.state.value.countedTaps)
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

    /** A run accepted at 38 ms whose save failed; the store then recovers (not failing). */
    private fun TestScope.saveFailedRun(): CalibrationViewModel {
        repository.failing = true
        val vm = calibration()
        runWithTaps(vm, lateMs = 38)
        assertTrue(vm.state.value.saveFailed)
        repository.failing = false
        return vm
    }

    @Test
    fun retryingAFailedSaveStoresTheSameOffset() =
        runTest(dispatcher) {
            val vm = saveFailedRun()

            vm.retrySave()
            runCurrent()

            assertFalse(vm.state.value.saveFailed)
            assertEquals(CalibrationResult.Accepted(38 * ms, 0), vm.state.value.result)
            assertEquals(38 * ms, repository.latencyOffsetNanos(piano.device.id))
        }

    @Test
    fun aRetryThatFailsAgainCanBeRetriedAgain() =
        runTest(dispatcher) {
            val vm = saveFailedRun()
            repository.failing = true

            vm.retrySave()
            runCurrent()
            assertTrue(vm.state.value.saveFailed)

            repository.failing = false
            vm.retrySave()
            runCurrent()
            assertFalse(vm.state.value.saveFailed)
            assertEquals(38 * ms, repository.latencyOffsetNanos(piano.device.id))
        }

    @Test
    fun retryingWithNothingToRetryDoesNotTouchTheStore() =
        runTest(dispatcher) {
            val ready = calibration()
            ready.retrySave()
            runCurrent()
            assertEquals(0, repository.setCalls)

            runWithTaps(ready, lateMs = 38)
            assertEquals(1, repository.setCalls)
            ready.retrySave()
            runCurrent()
            assertEquals(1, repository.setCalls)
        }

    @Test
    fun retryingAfterAnUnevenResultDoesNotTouchTheStore() =
        runTest(dispatcher) {
            val vm = calibration()
            val clicks = LatencyCalibration.clickTimes(clock.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS * ms)
            vm.start()
            runCurrent()
            clicks.forEachIndexed { i, click ->
                advanceTo(click + (if (i % 2 == 0) 5 else 70) * ms)
                tap()
            }
            advanceTo(clicks.last() + beat)
            assertTrue(vm.state.value.result is CalibrationResult.TooUneven)

            vm.retrySave()
            runCurrent()

            assertEquals(0, repository.setCalls)
        }

    @Test
    fun aSecondRetryWhileOneRunsIsIgnored() =
        runTest(dispatcher) {
            val vm = saveFailedRun()
            val before = repository.setCalls
            repository.gate = CompletableDeferred()

            vm.retrySave()
            runCurrent()
            vm.retrySave()
            runCurrent()
            assertEquals(before + 1, repository.setCalls)

            repository.gate?.complete(Unit)
            runCurrent()
            assertFalse(vm.state.value.saveFailed)
            assertEquals(before + 1, repository.setCalls)
        }

    @Test
    fun startDuringARetryIsIgnored() =
        runTest(dispatcher) {
            val vm = saveFailedRun()
            repository.gate = CompletableDeferred()
            vm.retrySave()
            runCurrent()

            vm.start()
            runCurrent()

            assertEquals(CalibrationPhase.DONE, vm.state.value.phase)
            repository.gate?.complete(Unit)
            runCurrent()
            assertFalse(vm.state.value.saveFailed)
        }

    @Test
    fun closingDuringARetryCancelsIt() =
        runTest(dispatcher) {
            val vm = saveFailedRun()
            repository.gate = CompletableDeferred()
            vm.retrySave()
            runCurrent()

            vm.close()
            repository.gate?.complete(Unit)
            runCurrent()

            assertNull(repository.latencyOffsetNanos(piano.device.id))
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
