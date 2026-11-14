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
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.model.MidiNote
import pianoanchor.engine.Tunables
import pianoanchor.engine.identity.IdentityCodec
import pianoanchor.testing.FakeClock
import pianoanchor.testing.FakeInstrumentRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeWallClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The monitor asking a connected piano who it is (docs/protocols/midi-identity.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorIdentityTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(1_000_000_000)
    private val piano = FakePiano(clock)
    private val instruments = FakeInstrumentRepository()
    private val connectedAt = Instant.parse("2026-11-12T10:00:00Z")
    private val wallClock = FakeWallClock(connectedAt)
    private val c4 = MidiNote(60)
    private val identity = InstrumentIdentity(listOf(0x41), family = 0x19, model = 2, version = listOf(1, 0, 3, 0))
    private val identityReply = bytes(0xF0, 0x7E, 0x10, 0x06, 0x02, 0x41, 0x19, 0x00, 0x02, 0x00, 1, 0, 3, 0, 0xF7)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun TestScope.selected(): MonitorViewModel =
        MonitorViewModel(piano, piano, piano, instruments, clock, wallClock).also {
            runCurrent()
            it.select(piano.device)
            runCurrent()
        }

    private fun TestScope.play(action: () -> Unit) {
        action()
        runCurrent()
    }

    private fun answersIdentity() {
        piano.replyTo = { request -> identityReply.takeIf { request.contentEquals(IdentityCodec.REQUEST) } }
    }

    private fun unidentified(at: Instant = connectedAt) = Instrument("fake-piano", "Fake Piano", null, at, at)

    @Test
    fun replyWithinTheTimeoutStoresTheIdentity() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()

            val expected = Instrument("fake-piano", "Fake Piano", identity, connectedAt, connectedAt)
            assertEquals(expected, vm.state.value.instrument)
            assertEquals(expected, instruments.get("fake-piano"))
            assertContentEquals(IdentityCodec.REQUEST, piano.sent.single())
        }

    @Test
    fun replyIsStillLogged() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()
            assertEquals(listOf(LogLine("SysEx 15 bytes", 0)), vm.state.value.log)
        }

    @Test
    fun noReplyStoresNoIdentityAfterTheTimeout() =
        runTest(dispatcher) {
            val vm = selected()
            advanceTimeBy(Tunables.IDENTITY_REPLY_TIMEOUT_MS - 1)
            runCurrent()
            assertNull(vm.state.value.instrument)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(unidentified(), vm.state.value.instrument)
            assertEquals(unidentified(), instruments.get("fake-piano"))
        }

    @Test
    fun otherSysExIsNotTakenAsTheReply() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.play(bytes(0xF0, 0x41, 0x10, 0xF7)) }
            play { piano.press(c4) }
            assertNull(vm.state.value.instrument)
            advanceTimeBy(Tunables.IDENTITY_REPLY_TIMEOUT_MS)
            runCurrent()
            assertEquals(unidentified(), vm.state.value.instrument)
        }

    @Test
    fun noOutputDeviceStoresNoIdentityWithoutSending() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = selected()
            assertEquals(unidentified(), vm.state.value.instrument)
            assertTrue(piano.sent.isEmpty())
            assertNull(vm.state.value.error)
        }

    @Test
    fun failedSendStoresNoIdentityAndShowsNoError() =
        runTest(dispatcher) {
            piano.failSends = true
            val vm = selected()
            assertEquals(unidentified(), vm.state.value.instrument)
            assertNull(vm.state.value.error)
            assertEquals(Connection.CONNECTED, vm.state.value.connection)
        }

    @Test
    fun reconnectAsksAgainAndAdvancesLastSeen() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()
            wallClock.advance(30.seconds)
            play { piano.unplug() }
            play { piano.plugIn() }

            assertEquals(2, piano.sent.size)
            val later = connectedAt + 30.seconds
            assertEquals(
                Instrument("fake-piano", "Fake Piano", identity, connectedAt, later),
                vm.state.value.instrument,
            )
        }

    @Test
    fun aReconnectWithoutAnIdentityReplyStillShowsTheStoredModel() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()
            piano.replyTo = { null }
            wallClock.advance(30.seconds)
            play { piano.unplug() }
            play { piano.plugIn() }
            advanceTimeBy(Tunables.IDENTITY_REPLY_TIMEOUT_MS)
            runCurrent()

            val expected = Instrument("fake-piano", "Fake Piano", identity, connectedAt, connectedAt + 30.seconds)
            assertEquals(expected, vm.state.value.instrument)
            assertEquals(expected, instruments.get("fake-piano"))
        }

    @Test
    fun dropBeforeTheReplyCancelsTheWait() =
        runTest(dispatcher) {
            val vm = selected()
            play { piano.unplug() }
            advanceTimeBy(Tunables.IDENTITY_REPLY_TIMEOUT_MS)
            runCurrent()
            assertNull(vm.state.value.instrument)
            assertNull(instruments.get("fake-piano"))
        }

    @Test
    fun selectingAgainClearsTheShownInstrument() =
        runTest(dispatcher) {
            answersIdentity()
            val vm = selected()
            piano.replyTo = { null }
            vm.select(piano.device)
            runCurrent()
            assertNull(vm.state.value.instrument)
        }
}
