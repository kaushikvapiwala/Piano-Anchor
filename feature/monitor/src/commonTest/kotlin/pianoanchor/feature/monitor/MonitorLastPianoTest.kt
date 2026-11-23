package pianoanchor.feature.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Remembering the last connected piano and forgetting it from the monitor. */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorLastPianoTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(1_000_000_000)
    private val piano = FakePiano(clock)
    private val settings = FakeSettingsRepository()
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
            settings,
            clock,
            FakeWallClock(Instant.parse("2026-11-12T10:00:00Z")),
            "test",
        ).also { runCurrent() }

    @Test
    fun aConnectedBluetoothPianoIsRememberedAndShownAsLastPiano() =
        runTest(dispatcher) {
            piano.advertised = listOf(bluetoothPiano)
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            vm.connect(bluetoothPiano)
            runCurrent()

            assertEquals(bluetoothPiano, settings.settings.first().lastPiano)
            assertEquals(bluetoothPiano, vm.state.value.lastPiano)
        }

    @Test
    fun aFailedConnectRemembersNothing() =
        runTest(dispatcher) {
            val vm = monitor()
            vm.connect(bluetoothPiano)
            runCurrent()

            assertNull(settings.settings.first().lastPiano)
        }

    @Test
    fun anUnwritableStoreStillSelectsTheConnectedPiano() =
        runTest(dispatcher) {
            piano.advertised = listOf(bluetoothPiano)
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            settings.failing = true
            vm.connect(bluetoothPiano)
            runCurrent()

            assertEquals(bluetoothPiano, vm.state.value.selected)
            assertNull(vm.state.value.lastPiano)
        }

    @Test
    fun forgettingDisconnectsTheRememberedPianoAndClearsIt() =
        runTest(dispatcher) {
            piano.advertised = listOf(bluetoothPiano)
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            vm.connect(bluetoothPiano)
            runCurrent()

            vm.forget()
            runCurrent()

            assertNull(settings.settings.first().lastPiano)
            assertNull(vm.state.value.lastPiano)
            assertEquals(listOf(piano.device), vm.state.value.devices)
        }

    @Test
    fun forgettingTheSelectedPianoEndsItsSessionWithoutAnError() =
        runTest(dispatcher) {
            piano.advertised = listOf(bluetoothPiano)
            val vm = monitor()
            vm.scanBluetooth()
            runCurrent()
            vm.connect(bluetoothPiano)
            runCurrent()
            assertEquals(bluetoothPiano, vm.state.value.selected)

            vm.forget()
            advanceTimeBy(Tunables.MONITOR_RECONNECT_GIVE_UP_MS + 1)

            assertNull(vm.state.value.selected)
            assertEquals(Connection.NONE, vm.state.value.connection)
            assertNull(vm.state.value.error)
        }

    @Test
    fun forgettingWithNothingRememberedDoesNothing() =
        runTest(dispatcher) {
            val vm = monitor()
            vm.forget()
            runCurrent()

            assertEquals(listOf(piano.device), vm.state.value.devices)
        }
}
