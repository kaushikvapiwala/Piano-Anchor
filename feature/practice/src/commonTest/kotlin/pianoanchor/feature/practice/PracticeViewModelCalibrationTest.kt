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
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.MidiTransport
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/** The link offset of a timed run: stored tap result, else measured, else the transport default. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelCalibrationTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private var piano = FakePiano(clock)
    private val identityReply = intArrayOf(0xF0, 0x7E, 0x10, 0x06, 0x02, 0x41, 0x19, 0x00, 0x02, 0x00, 0, 1, 2, 3, 0xF7)
    private val attempts = FakeAttemptRepository()
    private val calibration = FakeCalibrationRepository()
    private val learnerRepository = FakeLearnerRepository()
    private val learner = LearnerModel(Curriculum.graph, learnerRepository)
    private val metronome = FakeMetronome(clock)
    private val settings = FakeSettingsRepository()
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

    /** The piano answers each Identity Request [roundTripMs] later, so the measured link delay is half of that. */
    private fun answerWithRoundTrip(roundTripMs: Long) {
        piano.replyTo = {
            piano.play(
                identityReply.map { it.toByte() }.toByteArray(),
                stampNanos = clock.nowNanos() + roundTripMs * ms,
            )
            null
        }
    }

    private fun TestScope.practice(initialRung: TempoRung = TempoRung.WAIT): PracticeViewModel =
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            piano,
            attempts,
            calibration,
            metronome,
            clock,
            wallClock,
            learner,
            TimeZone.UTC,
            settings,
            initialRung = initialRung,
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

    private suspend fun attemptOffset() = assertNotNull(attempts.get("attempt-1")).latencyOffsetNanos

    @Test
    fun aStoredTapResultWinsAndNothingIsMeasured() =
        runPractice {
            calibration.setLatencyOffsetNanos(piano.device.id, 38 * ms)
            answerWithRoundTrip(40)
            val vm = practice()

            playCde(vm, lateMs = 38)

            assertEquals(38 * ms, attemptOffset())
            assertEquals(0, piano.sent.size)
        }

    @Test
    fun aMeasuredLinkDelayReachesTheJudgeAndTheAttempt() =
        runPractice {
            answerWithRoundTrip(40) // half is 20 ms
            val vm = practice()

            // Every key reaches the app 20 ms late: on time once the measured delay is subtracted.
            playCde(vm, lateMs = 20)

            assertEquals(
                ScoreNoteRef(0, c4) to LiveMark.Correct,
                vm.state.value.liveMarks.entries
                    .first()
                    .toPair(),
            )
            val attempt = assertNotNull(attempts.get("attempt-1"))
            assertEquals(20 * ms, attempt.latencyOffsetNanos)
            assertEquals(1.0, attempt.accuracy)
            assertEquals(Band.CLEAN, attempt.band)
        }

    @Test
    fun theMeasurementClosesItsReaderBeforeTheRunOpensTheInput() =
        runPractice {
            answerWithRoundTrip(40)
            val vm = practice()

            vm.selectRung(TempoRung.P60)
            runCurrent()
            press(c4) // the starting key: read by the run, so the measurement's reader is gone

            assertNotNull(metronome.running.value)
        }

    @Test
    fun aSilentPianoWaitsTwoTimeoutsThenRunsOnTheTransportDefault() =
        runPractice {
            val vm = practice(initialRung = TempoRung.P60)
            press(c4) // dropped: the link is still being measured
            assertNull(metronome.running.value)

            advanceTimeBy(2 * Tunables.CALIBRATION_PING_TIMEOUT_MS) // the warm-up and the first timed ping
            runCurrent()
            press(c4)

            assertNotNull(metronome.running.value)
            assertEquals(TempoRung.P60, vm.state.value.rung)
        }

    @Test
    fun aBluetoothPianoWithoutAnOutputUsesTheBluetoothDefault() =
        runPractice {
            piano = FakePiano(clock, MidiDevice("ble:AA", "Bluetooth piano", MidiTransport.BLUETOOTH))
            piano.inputOnly = true
            val vm = practice()

            playCde(vm, lateMs = 0)
            assertEquals(15 * ms, attemptOffset())
        }

    @Test
    fun aUsbPianoWithoutAnOutputUsesNoOffset() =
        runPractice {
            piano = FakePiano(clock, MidiDevice("usb:A", "USB piano", MidiTransport.USB))
            piano.inputOnly = true
            val vm = practice()

            playCde(vm, lateMs = 200)

            assertEquals(0L, attemptOffset())
            assertEquals(
                LiveMark.TimingOff(200),
                vm.state.value.liveMarks
                    .getValue(ScoreNoteRef(0, c4)),
            )
        }

    @Test
    fun theLinkIsMeasuredOnceForTwoTakes() =
        runPractice {
            answerWithRoundTrip(40)
            val vm = practice()
            playCde(vm, lateMs = 20)
            val sentAfterFirst = piano.sent.size

            vm.restart()
            runCurrent()
            vm.selectRung(TempoRung.P60)
            runCurrent()

            assertEquals(1 + Tunables.CALIBRATION_PING_COUNT, sentAfterFirst) // a warm-up and the timed pings
            assertEquals(sentAfterFirst, piano.sent.size)
        }

    @Test
    fun waitModeMeasuresNothing() =
        runPractice {
            answerWithRoundTrip(40)
            val vm = practice()
            listOf(c4, d4, e4).forEach {
                clock.advanceNanos(300 * ms)
                press(it)
            }
            assertEquals(0, piano.sent.size)
            assertNull(assertNotNull(attempts.get("attempt-1")).latencyOffsetNanos)
            assertEquals(RunPhase.READY, vm.state.value.phase)
        }

    @Test
    fun aStoreThatCannotBeReadFallsBackToMeasuring() =
        runPractice {
            calibration.failing = true
            answerWithRoundTrip(40)
            val vm = practice()

            playCde(vm, lateMs = 20)

            assertEquals(20 * ms, attemptOffset())
        }

    @Test
    fun aPositiveOffsetExtendsTheRunByTheOffset() =
        runPractice {
            calibration.setLatencyOffsetNanos(piano.device.id, 200 * ms)
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            runCurrent()
            press(c4) // the starting key
            advance(countInMs + 200)
            hold(c4, heldMs = 900, restMs = beatMs - 900)
            hold(d4, heldMs = 900, restMs = beatMs - 900)
            piano.press(e4)
            runCurrent()
            advance(3_650) // 6350 ms after tick 0 on the wire: past the judge's end at 6250 ms, within the offset
            piano.release(e4)
            runCurrent()
            assertNull(attempts.get("attempt-1")) // still running

            advance(300)

            val attempt = assertNotNull(attempts.get("attempt-1"))
            assertEquals(6_350 * ms, attempt.playedNotes.last().upNanos)
        }

    @Test
    fun aStoredOffsetShiftsLiveVerdictsAndGrading() =
        runPractice {
            calibration.setLatencyOffsetNanos(piano.device.id, 200 * ms)
            val vm = practice()

            playCde(vm, lateMs = 200)

            assertEquals(
                LiveMark.Correct,
                vm.state.value.liveMarks
                    .getValue(ScoreNoteRef(0, c4)),
            )
            assertEquals(1.0, assertNotNull(attempts.get("attempt-1")).accuracy)
        }
}
