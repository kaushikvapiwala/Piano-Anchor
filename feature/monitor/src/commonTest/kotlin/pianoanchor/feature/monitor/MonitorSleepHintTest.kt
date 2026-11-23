package pianoanchor.feature.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.MidiDevice
import pianoanchor.engine.Tunables
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeInstrumentRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The "pianos turn Bluetooth off when idle" hint after a scan that finds nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorSleepHintTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(1_000_000_000)
    private val piano = FakePiano(clock)
    private val bluetoothPiano = MidiDevice(id = "ble-piano", name = "BLE Piano")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.monitor(): MonitorViewModel =
        MonitorViewModel(
            piano,
            piano,
            piano,
            FakeInstrumentRepository(),
            FakeSettingsRepository(),
            clock,
            FakeWallClock(Instant.parse("2026-11-12T10:00:00Z")),
            "test",
        ).also { runCurrent() }

    private suspend fun TestScope.finishScan() = advanceTimeBy(Tunables.BLE_SCAN_TIMEOUT_MS + 1)

    @Test
    fun aScanThatEndsWithNothingSetsTheHint() =
        runTest(dispatcher) {
            piano.unplug()
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            assertFalse(vm.state.value.sleepHint)
            finishScan()

            assertTrue(vm.state.value.sleepHint)
        }

    @Test
    fun aScanThatFindsAPianoDoesNotSetTheHint() =
        runTest(dispatcher) {
            piano.unplug()
            piano.advertised = listOf(bluetoothPiano)
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            finishScan()

            assertFalse(vm.state.value.sleepHint)
        }

    @Test
    fun aListedPianoMeansNoHint() =
        runTest(dispatcher) {
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            finishScan()

            assertFalse(vm.state.value.sleepHint)
        }

    @Test
    fun aFailedScanDoesNotSetTheHint() =
        runTest(dispatcher) {
            piano.unplug()
            piano.bluetoothProblem = BluetoothUnavailableException.Reason.BLUETOOTH_OFF
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()

            assertFalse(vm.state.value.sleepHint)
        }

    @Test
    fun startingAScanClearsTheHint() =
        runTest(dispatcher) {
            piano.unplug()
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            finishScan()
            vm.scanBluetooth()
            runCurrent()

            assertFalse(vm.state.value.sleepHint)
        }

    @Test
    fun aListedDeviceClearsTheHint() =
        runTest(dispatcher) {
            piano.unplug()
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            finishScan()
            piano.plugIn()
            runCurrent()

            assertFalse(vm.state.value.sleepHint)
        }
}
