package pianoanchor.feature.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.engine.identity.IdentityCodec
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeInstrumentRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** The monitor when storing a piano fails: the piano is shown but not saved, and MIDI keeps flowing. */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorStorageFailureTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(1_000_000_000)
    private val piano = FakePiano(clock)
    private val instruments = FakeInstrumentRepository()
    private val connectedAt = Instant.parse("2026-11-12T10:00:00Z")
    private val wallClock = FakeWallClock(connectedAt)
    private val c4 = MidiNote(60)
    private val identity = InstrumentIdentity(listOf(0x41), family = 0x19, model = 2, version = listOf(1, 0, 3, 0))
    private val identityReply =
        listOf(0xF0, 0x7E, 0x10, 0x06, 0x02, 0x41, 0x19, 0x00, 0x02, 0x00, 1, 0, 3, 0, 0xF7)
            .map { it.toByte() }
            .toByteArray()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun answersIdentity() {
        piano.replyTo = { request -> identityReply.takeIf { request.contentEquals(IdentityCodec.REQUEST) } }
    }

    private fun unidentified() = Instrument("fake-piano", "Fake Piano", null, connectedAt, connectedAt)

    private fun TestScope.selected(repository: InstrumentRepositoryPort = instruments): MonitorViewModel =
        MonitorViewModel(piano, piano, piano, repository, clock, wallClock).also {
            runCurrent()
            it.select(piano.device)
            runCurrent()
        }

    private fun TestScope.play(action: () -> Unit) {
        action()
        runCurrent()
    }

    @Test
    fun storageFailureDuringIdentifyKeepsMidiFlowingAndShowsTheUnsavedInstrument() =
        runTest(dispatcher) {
            instruments.failing = true
            answersIdentity()
            val vm = selected()

            assertEquals(
                Instrument("fake-piano", "Fake Piano", identity, connectedAt, connectedAt),
                vm.state.value.instrument,
            )
            play { piano.press(c4, velocity = 100) }
            assertEquals(
                LogLine("C4 ↓ 100", 0),
                vm.state.value.log
                    .first(),
            )
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
        }

    @Test
    fun storageFailureSetsMonitorErrorStorage() =
        runTest(dispatcher) {
            instruments.failing = true
            answersIdentity()
            val vm = selected()

            assertEquals(MonitorError.STORAGE, vm.state.value.error)
        }

    @Test
    fun storageFailureOnlyOnTheReadAlsoShowsTheUnsavedInstrument() =
        runTest(dispatcher) {
            val readFails =
                object : InstrumentRepositoryPort by instruments {
                    override suspend fun get(deviceId: String): Instrument? =
                        throw StorageUnavailableException("get failed")
                }
            piano.inputOnly = true
            val vm = selected(readFails)

            assertEquals(unidentified(), vm.state.value.instrument)
            assertEquals(MonitorError.STORAGE, vm.state.value.error)
        }

    @Test
    fun successfulIdentifySetsNoError() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()

            assertNull(vm.state.value.error)
        }

    @Test
    fun aStoreThatKeepsNothingStillShowsTheInstrumentAsSaved() =
        runTest(dispatcher) {
            val forgetful =
                object : InstrumentRepositoryPort by instruments {
                    override suspend fun get(deviceId: String): Instrument? = null
                }
            piano.inputOnly = true
            val vm = selected(forgetful)

            assertEquals(unidentified(), vm.state.value.instrument)
            assertNull(vm.state.value.error)
        }
}
