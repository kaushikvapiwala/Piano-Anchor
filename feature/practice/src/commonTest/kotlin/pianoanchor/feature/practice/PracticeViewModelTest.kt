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
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.WrongNote
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Passages
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeClock
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
class PracticeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)
    private val piano = FakePiano(clock)
    private val attempts = FakeAttemptRepository()
    private val openedAt = Instant.parse("2026-11-13T09:00:00Z")
    private val wallClock = FakeWallClock(openedAt)
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val f4 = MidiNote(65)
    private val ms = 1_000_000L
    private var ids = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.practice(
        device: MidiDevice = piano.device,
        repository: AttemptRepositoryPort = attempts,
    ): PracticeViewModel =
        PracticeViewModel(device, Passages.CDE, piano, repository, wallClock) { "attempt-${++ids}" }
            .also { runCurrent() }

    private fun TestScope.playCde() {
        tap(c4)
        tap(d4)
        tap(e4)
    }

    /** Presses and releases [note] [gapMs] after the previous note, then lets the ViewModel react. */
    private fun TestScope.tap(
        note: MidiNote,
        gapMs: Long = 300,
    ) {
        clock.advanceNanos(gapMs * ms)
        piano.press(note)
        piano.release(note)
        runCurrent()
    }

    private fun PracticeViewModel.statuses() = state.value.steps.map { it.status }

    @Test
    fun opensWaitingForTheFirstStep() =
        runTest(dispatcher) {
            val vm = practice()

            assertEquals(PracticeState.start(Passages.CDE), vm.state.value)
            assertEquals(listOf(StepStatus.WAITING, StepStatus.UPCOMING, StepStatus.UPCOMING), vm.statuses())
            assertEquals(Passages.CDE, vm.state.value.passage)
        }

    @Test
    fun correctNoteMarksTheStepDoneAndWaitsForTheNext() =
        runTest(dispatcher) {
            val vm = practice()

            tap(c4)

            assertEquals(listOf(StepStatus.DONE, StepStatus.WAITING, StepStatus.UPCOMING), vm.statuses())
            assertEquals(1, vm.state.value.currentStep)
            assertEquals(KeyFlash(c4, correct = true), vm.state.value.flash)
            assertEquals(KeyFlash(c4, correct = true), vm.state.value.lastKey)
        }

    @Test
    fun cleanRunIsGradedSavedAndFinishes() =
        runTest(dispatcher) {
            val vm = practice()
            wallClock.advance(10.seconds) // the learner waits before the first note

            tap(c4)
            val firstNoteNanos = clock.nowNanos()
            tap(d4)
            tap(e4)

            assertEquals(List(3) { StepStatus.DONE }, vm.statuses())
            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
            val attempt = assertNotNull(attempts.get("attempt-1"))
            assertEquals(Band.CLEAN, attempt.band)
            assertEquals(Passages.CDE.id, attempt.passageId)
            assertEquals(piano.device.id, attempt.deviceId)
            assertEquals(openedAt + 10.seconds, attempt.startedAt)
            assertEquals(clock.nowNanos() - firstNoteNanos, attempt.durationNanos)
            assertEquals(
                listOf(
                    StepCorrect(0, 0),
                    StepCorrect(300 * ms, 1),
                    StepCorrect(600 * ms, 2),
                    PassageComplete(600 * ms, 2),
                ),
                attempt.events,
            )
        }

    @Test
    fun wrongNoteFlashesWrongAndKeepsTheStep() =
        runTest(dispatcher) {
            val vm = practice()
            tap(c4)

            tap(f4)

            assertEquals(KeyFlash(f4, correct = false), vm.state.value.flash)
            assertEquals(KeyFlash(f4, correct = false), vm.state.value.lastKey)
            assertEquals(1, vm.state.value.currentStep)
            assertEquals(listOf(StepStatus.DONE, StepStatus.WAITING, StepStatus.UPCOMING), vm.statuses())
        }

    @Test
    fun wrongNotesLowerTheSavedBand() =
        runTest(dispatcher) {
            practice()

            tap(c4)
            tap(f4)
            tap(d4)
            tap(e4)

            val attempt = assertNotNull(attempts.get("attempt-1"))
            assertEquals(1, attempt.wrongNotes)
            assertEquals(Band.KEEP_PRACTISING, attempt.band) // 3 / 4 = 0.75
            assertEquals(WrongNote(300 * ms, 1, f4), attempt.events[1])
        }

    @Test
    fun keysDownFollowNoteOnAndNoteOff() =
        runTest(dispatcher) {
            val vm = practice()

            piano.press(c4)
            piano.press(f4)
            runCurrent()
            assertEquals(setOf(c4, f4), vm.state.value.keysDown)

            piano.release(c4)
            runCurrent()
            assertEquals(setOf(f4), vm.state.value.keysDown)
        }

    @Test
    fun aHeldWrongKeyStaysWrongAfterACorrectKey() =
        runTest(dispatcher) {
            val vm = practice()

            piano.press(f4)
            piano.press(c4)
            runCurrent()
            assertEquals(setOf(f4), vm.state.value.wrongKeys)

            piano.release(f4)
            runCurrent()
            assertEquals(emptySet(), vm.state.value.wrongKeys)
        }

    @Test
    fun flashClearsAfterKeyFlashMsButTheLastKeyStays() =
        runTest(dispatcher) {
            val vm = practice()
            tap(c4)

            advanceTimeBy(Tunables.KEY_FLASH_MS - 1)
            runCurrent()
            assertEquals(KeyFlash(c4, correct = true), vm.state.value.flash)

            advanceTimeBy(1)
            runCurrent()
            assertNull(vm.state.value.flash)
            assertEquals(KeyFlash(c4, correct = true), vm.state.value.lastKey)
        }

    @Test
    fun aNewKeyRestartsTheFlash() =
        runTest(dispatcher) {
            val vm = practice()
            tap(c4)
            advanceTimeBy(Tunables.KEY_FLASH_MS - 10)

            tap(f4)
            advanceTimeBy(Tunables.KEY_FLASH_MS - 10)
            runCurrent()

            assertEquals(KeyFlash(f4, correct = false), vm.state.value.flash)
        }

    @Test
    fun disconnectEndsTheRunWithoutSaving() =
        runTest(dispatcher) {
            val vm = practice()
            tap(c4)
            tap(d4)

            piano.unplug()
            runCurrent()

            assertEquals(PracticeConnection.DISCONNECTED, vm.state.value.connection)
            assertNull(vm.state.value.finishedAttemptId)
            assertNull(attempts.get("attempt-1"))
        }

    @Test
    fun disconnectReleasesHeldKeys() =
        runTest(dispatcher) {
            val vm = practice()
            piano.press(c4)
            piano.press(f4)
            runCurrent()

            piano.unplug()
            runCurrent()

            assertEquals(emptySet(), vm.state.value.keysDown)
            assertEquals(emptySet(), vm.state.value.wrongKeys)
        }

    @Test
    fun closeEndsTheRunAndFreesThePianoForTheNextReader() =
        runTest(dispatcher) {
            val vm = practice()
            tap(c4)

            vm.close()
            val next = PracticeViewModel(piano.device, Passages.CDE, piano, attempts, wallClock) { "next" }
            runCurrent()
            tap(c4)

            assertEquals(1, vm.state.value.currentStep) // the closed run saw nothing more
            assertEquals(1, next.state.value.currentStep)
        }

    @Test
    fun unavailablePianoShowsDisconnected() =
        runTest(dispatcher) {
            val vm = practice(MidiDevice(id = "gone", name = "Gone Piano"))

            assertEquals(PracticeConnection.DISCONNECTED, vm.state.value.connection)
        }

    @Test
    fun restartDiscardsTheRunAndStartsOver() =
        runTest(dispatcher) {
            val vm = practice()
            tap(c4)
            tap(f4)

            vm.restart()
            runCurrent()
            assertEquals(PracticeState.start(Passages.CDE), vm.state.value)

            wallClock.advance(5.seconds)
            tap(c4)
            tap(d4)
            tap(e4)
            val attempt = assertNotNull(attempts.get("attempt-1"))
            assertEquals(0, attempt.wrongNotes)
            assertEquals(openedAt + 5.seconds, attempt.startedAt)
        }

    @Test
    fun restartAfterADisconnectReopensThePiano() =
        runTest(dispatcher) {
            val vm = practice()
            piano.unplug()
            runCurrent()
            piano.plugIn()

            vm.restart()
            runCurrent()
            tap(c4)

            assertEquals(PracticeConnection.CONNECTED, vm.state.value.connection)
            assertEquals(1, vm.state.value.currentStep)
        }

    // Storage failure: the graded attempt is kept for Retry or Discard

    @Test
    fun failedSaveShowsSaveFailedAndNoFinishedAttemptId() =
        runTest(dispatcher) {
            attempts.failing = true
            val vm = practice()

            playCde()

            assertTrue(vm.state.value.saveFailed)
            assertNull(vm.state.value.finishedAttemptId)
        }

    @Test
    fun retrySaveAfterRecoverySavesTheSameAttemptIdAndSetsFinishedAttemptId() =
        runTest(dispatcher) {
            attempts.failing = true
            val vm = practice()
            playCde()

            attempts.failing = false
            vm.retrySave()
            runCurrent()

            assertFalse(vm.state.value.saveFailed)
            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
            assertEquals(Band.CLEAN, assertNotNull(attempts.get("attempt-1")).band)
        }

    @Test
    fun retrySaveThatFindsTheIdStoredCountsAsSaved() =
        runTest(dispatcher) {
            // The first save commits but reports a failure (e.g. the disk filled while confirming it).
            val commitsThenFails =
                object : AttemptRepositoryPort by attempts {
                    var calls = 0

                    override suspend fun save(attempt: Attempt) {
                        attempts.save(attempt)
                        if (++calls == 1) throw StorageUnavailableException("save failed")
                    }
                }
            val vm = practice(repository = commitsThenFails)
            playCde()
            assertTrue(vm.state.value.saveFailed)

            vm.retrySave()
            runCurrent()

            assertFalse(vm.state.value.saveFailed)
            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
        }

    @Test
    fun retrySaveThatFailsAgainStaysFailed() =
        runTest(dispatcher) {
            attempts.failing = true
            val vm = practice()
            playCde()

            vm.retrySave()
            runCurrent()

            assertTrue(vm.state.value.saveFailed)
            assertNull(vm.state.value.finishedAttemptId)

            attempts.failing = false // the attempt is still kept for a later retry
            vm.retrySave()
            runCurrent()
            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
        }

    @Test
    fun aTapWhileARetryRunsIsIgnored() =
        runTest(dispatcher) {
            val answer = CompletableDeferred<Unit>()
            var saves = 0
            val slowRetry =
                object : AttemptRepositoryPort by attempts {
                    override suspend fun save(attempt: Attempt) {
                        if (++saves == 1) throw StorageUnavailableException("save failed")
                        answer.await()
                        attempts.save(attempt)
                    }
                }
            val vm = practice(repository = slowRetry)
            playCde()

            vm.retrySave()
            runCurrent()
            vm.retrySave()
            runCurrent()
            answer.complete(Unit)
            runCurrent()

            assertEquals(2, saves)
            assertEquals("attempt-1", vm.state.value.finishedAttemptId)
        }

    @Test
    fun retrySaveWithNothingUnsavedDoesNothing() =
        runTest(dispatcher) {
            val vm = practice()

            vm.retrySave()
            runCurrent()

            assertEquals(PracticeState.start(Passages.CDE), vm.state.value)
        }

    @Test
    fun discardRunStartsAFreshRun() =
        runTest(dispatcher) {
            attempts.failing = true
            val vm = practice()
            playCde()

            vm.discardRun()
            runCurrent()
            assertEquals(PracticeState.start(Passages.CDE), vm.state.value)
            vm.retrySave() // the discarded attempt is gone
            runCurrent()
            assertFalse(vm.state.value.saveFailed)

            attempts.failing = false
            playCde()
            assertEquals("attempt-2", vm.state.value.finishedAttemptId)
            assertNull(attempts.get("attempt-1"))
        }
}
