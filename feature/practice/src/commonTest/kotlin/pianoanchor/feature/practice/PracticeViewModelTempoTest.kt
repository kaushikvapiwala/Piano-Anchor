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
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.WrongNote
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Passages
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTempoTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)
    private val attempts = FakeAttemptRepository()
    private val metronome = FakeMetronome(clock)
    private val clickSetting = ClickSetting()
    private val openedAt = Instant.parse("2026-11-13T09:00:00Z")
    private val wallClock = FakeWallClock(openedAt)
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val f4 = MidiNote(65)
    private val ms = 1_000_000L
    private var ids = 0
    private val opened = mutableListOf<PracticeViewModel>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.practice(repository: AttemptRepositoryPort = attempts): PracticeViewModel =
        PracticeViewModel(piano.device, Passages.CDE, piano, repository, metronome, clock, wallClock, clickSetting) {
            "attempt-${++ids}"
        }.also {
            opened += it
            runCurrent()
        }

    private fun TestScope.playCde() {
        listOf(c4, d4, e4).forEach {
            clock.advanceNanos(300 * ms)
            piano.press(it)
            piano.release(it)
            runCurrent()
        }
    }

    private fun PracticeViewModel.statuses() = state.value.steps.map { it.status }

    // Tempo mode. CDE is written at 80 BPM, so the 60 % rung runs at 48 BPM: a beat lasts 1250 ms, the count-in is four
    // beats after the lead, and the three notes sound at 0, 1250 and 2500 ms after tick 0.

    /** Like runTest, but closes every ViewModel afterwards (a run's ticker never ends on a standing fake clock). */
    private fun runPractice(body: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            try {
                body()
            } finally {
                opened.forEach { it.close() }
            }
        }

    private val beatMs = 1_250L
    private val leadMs = Tunables.TEMPO_COUNT_IN_LEAD_MS
    private val countInMs = leadMs + 4 * beatMs
    private val runMs = 6_250L

    /** Moves the clock and virtual time together, 50 ms at a time, so the ViewModel's ticker sees every step. */
    private fun TestScope.advance(ms: Long) {
        repeat((ms / 50).toInt()) {
            clock.advanceNanos(50 * this@PracticeViewModelTempoTest.ms)
            advanceTimeBy(50)
            runCurrent()
        }
    }

    /** Selects the 60 % rung and presses [starter]: the count-in begins now. Returns the press time. */
    private fun TestScope.startRun(
        vm: PracticeViewModel,
        starter: MidiNote = c4,
    ): Long {
        vm.selectRung(TempoRung.P60)
        runCurrent()
        val pressedAt = clock.nowNanos()
        piano.press(starter)
        piano.release(starter)
        runCurrent()
        return pressedAt
    }

    /** Presses [note], holds it [heldMs] (a quarter note's worth) and releases it. */
    private fun TestScope.hold(
        note: MidiNote,
        heldMs: Long = 900,
    ) {
        piano.press(note)
        runCurrent()
        advance(heldMs)
        piano.release(note)
        runCurrent()
    }

    private fun TestScope.press(note: MidiNote) {
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    @Test
    fun timedRungWaitsForAKeyThenCountsInOneBar() =
        runPractice {
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            runCurrent()
            advance(2_000)

            assertEquals(RunPhase.READY, vm.state.value.phase)
            assertEquals(TempoRung.P60, vm.state.value.rung)
            assertNull(metronome.running.value)

            val pressedAt = startRun(vm)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
            assertEquals(0, vm.state.value.beat)
            assertEquals(
                FakeMetronome.Pattern(pressedAt + leadMs * ms, beatMs * ms, beatsPerBar = 4),
                metronome.running.value,
            )

            advance(leadMs)
            assertEquals(1, vm.state.value.beat)
            advance(beatMs)
            assertEquals(2, vm.state.value.beat)
            advance(2 * beatMs)
            assertEquals(4, vm.state.value.beat)
            assertEquals(RunPhase.COUNT_IN, vm.state.value.phase)
            advance(beatMs)
            assertEquals(RunPhase.PLAYING, vm.state.value.phase)
            assertEquals(1, vm.state.value.beat)
            assertEquals(0, vm.state.value.currentStep)
        }

    @Test
    fun theCountInKeyIsNotGraded() =
        runPractice {
            val vm = practice()
            startRun(vm, starter = c4)

            assertEquals(emptyMap(), vm.state.value.liveMarks)
            advance(countInMs)
            press(c4) // on the first beat: Correct for step 0
            assertEquals(mapOf(ScoreNoteRef(0, c4) to LiveMark.Correct), vm.state.value.liveMarks)

            advance(runMs)
            val attempt = assertNotNull(attempts.get("attempt-1"))
            assertEquals(listOf(c4), attempt.playedNotes.map { it.pitch })
        }

    @Test
    fun cursorAdvancesWithTheClockWithoutInput() =
        runPractice {
            val vm = practice()
            startRun(vm)

            advance(countInMs + beatMs - 50)
            assertEquals(0, vm.state.value.currentStep)
            advance(50)
            assertEquals(1, vm.state.value.currentStep)
            assertEquals(listOf(StepStatus.UPCOMING, StepStatus.WAITING, StepStatus.UPCOMING), vm.statuses())
            advance(beatMs)
            assertEquals(2, vm.state.value.currentStep)
        }

    @Test
    fun liveVerdictsMarkNotes() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs)

            press(c4)
            assertEquals(KeyFlash(c4, correct = true), vm.state.value.flash)
            advance(beatMs + 300)
            press(d4)
            assertEquals(KeyFlash(d4, correct = true, deviationMs = 300), vm.state.value.flash)
            piano.press(f4)
            runCurrent()

            assertEquals(
                mapOf(ScoreNoteRef(0, c4) to LiveMark.Correct, ScoreNoteRef(1, d4) to LiveMark.TimingOff(300)),
                vm.state.value.liveMarks,
            )
            assertEquals(KeyFlash(f4, correct = false), vm.state.value.lastKey)
            assertEquals(setOf(f4), vm.state.value.wrongKeys)
        }

    @Test
    fun runEndsAtEndNanosAndSavesATimedAttempt() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs)
            hold(c4)
            advance(beatMs - 900)
            hold(d4)
            advance(beatMs - 900)
            hold(e4, heldMs = 2 * beatMs) // a half note

            advance(runMs - 4 * beatMs - 50)
            assertNull(vm.state.value.finishedAttemptId)
            advance(50)

            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
            assertEquals(RunPhase.FINISHED, vm.state.value.phase)
            val attempt = assertNotNull(attempts.get("attempt-1"))
            assertEquals(48, attempt.tempoBpm)
            assertEquals(Band.CLEAN, attempt.band)
            assertEquals(emptyList(), attempt.events)
            assertEquals(listOf(c4, d4, e4), attempt.playedNotes.map { it.pitch })
            assertEquals(listOf(0L, beatMs * ms, 2 * beatMs * ms), attempt.playedNotes.map { it.downNanos })
            assertEquals(setOf(TempoRung.P60), vm.state.value.doneRungs)
        }

    @Test
    fun aKeyStillDownWhenTheRunEndsEndsWithTheRun() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs + 2 * beatMs)
            piano.press(e4) // held past the end: its NoteOff never reaches the take
            runCurrent()
            advance(runMs - 2 * beatMs)

            val e = assertNotNull(attempts.get("attempt-1")).playedNotes.single()
            assertEquals(2 * beatMs * ms, e.downNanos)
            assertEquals(runMs * ms, e.upNanos)
            vm.close()
            piano.release(e4)
        }

    @Test
    fun aPedalDownBeforeTheStartingKeyHoldsTheTakesNotes() =
        runPractice {
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            runCurrent()
            piano.play(byteArrayOf(0xB0.toByte(), 64, 127)) // sustain pedal down before the count-in
            runCurrent()
            press(c4) // starts the count-in
            advance(countInMs)
            press(c4)
            advance(runMs)

            val c = assertNotNull(attempts.get("attempt-1")).playedNotes.single()
            assertEquals(runMs * ms, c.soundingEndNanos)
        }

    @Test
    fun aClickTurnedOnAsTheRunEndsDoesNotKeepClicking() =
        runPractice {
            val vm = practice()
            vm.toggleClick() // off
            startRun(vm)
            advance(countInMs)
            val gate = CompletableDeferred<Unit>()
            metronome.startGate = gate
            vm.toggleClick() // on: the start waits on the gate
            runCurrent()
            vm.selectRung(TempoRung.WAIT) // the run ends while that start is pending
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            assertNull(metronome.running.value)
        }

    @Test
    fun metronomeStartsWithTheCountInAndStopsAtTheEnd() =
        runPractice {
            val vm = practice()
            val pressedAt = startRun(vm)

            assertEquals(Pattern(pressedAt + leadMs * ms), metronome.running.value?.let { Pattern(it.firstBeatNanos) })
            val clicks = metronome.clicksUntil(pressedAt + (countInMs + 2 * beatMs) * ms)
            assertEquals(listOf(true, false, false, false, true, false, false), clicks.map { it.accent })

            advance(countInMs + runMs)
            assertNull(metronome.running.value)
        }

    @Test
    fun toggleClickStopsAndRestartsTheClickWithoutMovingTheTiming() =
        runPractice {
            val vm = practice()
            startRun(vm)
            val pattern = metronome.running.value
            advance(countInMs + 100)

            vm.toggleClick()
            runCurrent()
            assertFalse(vm.state.value.clickOn)
            assertNull(metronome.running.value)
            advance(beatMs)
            assertEquals(
                2,
                vm.state.value.currentStep
                    .coerceAtLeast(2),
            ) // the clock keeps running

            vm.toggleClick()
            runCurrent()
            assertTrue(vm.state.value.clickOn)
            assertEquals(pattern, metronome.running.value)
            assertTrue(clickSetting.on.value)
        }

    @Test
    fun aMutedRunStartsSilentAndTheClickCanComeBack() =
        runPractice {
            val vm = practice()
            vm.toggleClick() // no run yet: only the setting changes
            runCurrent()
            assertFalse(vm.state.value.clickOn)

            startRun(vm)
            assertNull(metronome.running.value)
            vm.toggleClick()
            runCurrent()

            assertNotNull(metronome.running.value)
            assertTrue(clickSetting.on.value)
        }

    @Test
    fun theMuteSettingIsKeptForTheNextScreen() =
        runPractice {
            practice().toggleClick()
            runCurrent()

            assertFalse(practice().state.value.clickOn)
        }

    @Test
    fun selectingARungMidRunDiscardsIt() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs)
            press(c4)

            vm.selectRung(TempoRung.P70)
            runCurrent()

            assertEquals(RunPhase.READY, vm.state.value.phase)
            assertEquals(TempoRung.P70, vm.state.value.rung)
            assertEquals(emptyMap(), vm.state.value.liveMarks)
            assertNull(metronome.running.value)
            advance(runMs)
            assertNull(attempts.get("attempt-1"))
            assertNull(vm.state.value.finishedAttemptId)
        }

    @Test
    fun selectingWaitGoesBackToWaitMode() =
        runPractice {
            val vm = practice()
            startRun(vm)

            vm.selectRung(TempoRung.WAIT)
            runCurrent()
            playCde()

            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
            assertNull(assertNotNull(attempts.get("attempt-1")).tempoBpm)
        }

    @Test
    fun disconnectDuringATimedRunDiscardsIt() =
        runPractice {
            val vm = practice()
            startRun(vm)
            advance(countInMs)
            press(c4)

            piano.unplug()
            runCurrent()

            assertEquals(PracticeConnection.DISCONNECTED, vm.state.value.connection)
            assertEquals(RunPhase.READY, vm.state.value.phase)
            assertEquals(emptyMap(), vm.state.value.liveMarks)
            assertNull(metronome.running.value)
            advance(runMs)
            assertNull(attempts.get("attempt-1"))
        }

    @Test
    fun disconnectBeforeTheCountInStartsIsADisconnect() =
        runPractice {
            val vm = practice()
            vm.selectRung(TempoRung.P85)
            runCurrent()

            piano.unplug()
            runCurrent()

            assertEquals(PracticeConnection.DISCONNECTED, vm.state.value.connection)
        }

    @Test
    fun keysAndPedalBeforeAndDuringARunAreHandled() =
        runPractice {
            val vm = practice()
            vm.selectRung(TempoRung.P60)
            runCurrent()
            piano.press(f4) // starts the count-in, still held
            runCurrent()
            assertEquals(setOf(f4), vm.state.value.keysDown)
            piano.release(f4)
            runCurrent()
            assertEquals(emptySet(), vm.state.value.keysDown)

            advance(countInMs)
            piano.play(byteArrayOf(0xB0.toByte(), 64, 127)) // sustain pedal down
            runCurrent()
            press(c4)
            advance(runMs)

            assertNotNull(attempts.get("attempt-1"))
        }

    @Test
    fun doneRungsComeFromCleanTempos() =
        runPractice {
            attempts.save(timedAttempt("old-1", tempoBpm = 48, band = Band.CLEAN))
            attempts.save(timedAttempt("old-2", tempoBpm = 56, band = Band.GOOD))
            attempts.save(timedAttempt("old-3", tempoBpm = 68, band = Band.CLEAN))

            val vm = practice()
            assertEquals(setOf(TempoRung.P60, TempoRung.P85), vm.state.value.doneRungs)

            vm.selectRung(TempoRung.P70)
            runCurrent()
            assertEquals(setOf(TempoRung.P60, TempoRung.P85), vm.state.value.doneRungs)
        }

    @Test
    fun anUnreadableStoreLeavesNoRungDone() =
        runPractice {
            attempts.failing = true

            assertEquals(emptySet(), practice().state.value.doneRungs)
        }

    private fun timedAttempt(
        id: String,
        tempoBpm: Int,
        band: Band,
    ) = Attempt(
        id = id,
        passageId = Passages.CDE.id,
        deviceId = piano.device.id,
        startedAt = openedAt,
        durationNanos = 0,
        stepCount = 3,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = band,
        events = emptyList(),
        tempoBpm = tempoBpm,
        playedNotes = emptyList(),
    )

    private data class Pattern(
        val firstBeatNanos: Long,
    )
}
