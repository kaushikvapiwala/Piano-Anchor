package pianoanchor.feature.practice

import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.MidiNote
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.NoteLabelsUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelClickTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(5_000_000_000)

    // No MIDI output: a timed run takes the transport default at once instead of measuring the link.
    private val piano = FakePiano(clock).apply { inputOnly = true }
    private val attempts = FakeAttemptRepository()
    private val calibration = FakeCalibrationRepository()
    private val learnerRepository = FakeLearnerRepository()
    private val learner = LearnerModel(Curriculum.graph, learnerRepository)
    private val metronome = FakeMetronome(clock)
    private val settings = FakeSettingsRepository()
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
        PracticeViewModel(
            piano.device,
            Passages.CDE,
            piano,
            piano,
            repository,
            calibration,
            metronome,
            clock,
            wallClock,
            learner,
            TimeZone.UTC,
            settings,
            NoteLabelsUseCase(settings, attempts, learner, TimeZone.UTC),
        ) {
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
            clock.advanceNanos(50 * this@PracticeViewModelClickTest.ms)
            advanceTimeBy(50)
            runCurrent()
        }
    }

    private val autoStartMs = Tunables.TEMPO_AUTO_START_DELAY_MS

    /** Selects the 60 % rung and lets it arm: the count-in begins now. Returns the time the count-in began. */
    private fun TestScope.startRun(vm: PracticeViewModel): Long {
        vm.selectRung(TempoRung.P60)
        runCurrent()
        advance(autoStartMs)
        return clock.nowNanos()
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

    /** Plays C, D and E on time, from the first beat on: the take is clean once the clock runs out. */
    private fun TestScope.playCdeOnTime() {
        press(c4)
        advance(beatMs)
        press(d4)
        advance(beatMs)
        press(e4)
    }

    private fun TestScope.press(note: MidiNote) {
        piano.press(note)
        piano.release(note)
        runCurrent()
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

            advance(countInMs)
            playCdeOnTime()
            advance(runMs - 2 * beatMs)
            assertNull(metronome.running.value)
            assertNotNull(attempts.get("attempt-1"))
        }

    @Test
    fun toggleClickStopsAndRestartsTheClickWithoutMovingTheTiming() =
        runPractice {
            val vm = practice()
            startRun(vm)
            val pattern = metronome.running.value
            advance(countInMs)
            press(c4)
            advance(100)

            vm.toggleClick()
            runCurrent()
            assertFalse(vm.state.value.clickOn)
            assertNull(metronome.running.value)
            advance(beatMs - 100)
            press(d4)
            assertEquals(1, vm.state.value.currentStep) // the clock keeps running

            vm.toggleClick()
            runCurrent()
            assertTrue(vm.state.value.clickOn)
            assertEquals(pattern, metronome.running.value)
            assertTrue(settings.settings.first().clickOn)
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
            assertTrue(settings.settings.first().clickOn)
        }

    @Test
    fun `click toggle persists`() =
        runPractice {
            practice().toggleClick()
            runCurrent()
            assertFalse(settings.settings.first().clickOn)

            practice().toggleClick()
            runCurrent()
            assertTrue(settings.settings.first().clickOn)
        }

    @Test
    fun theStoredClickChoiceSeedsTheScreen() =
        runPractice {
            settings.update { it.copy(clickOn = false) }

            val vm = practice()

            assertFalse(vm.state.value.clickOn)
            startRun(vm)
            assertNull(metronome.running.value)
        }

    @Test
    fun aToggleBeforeTheStoredChoiceIsReadWinsAndIsStored() =
        runPractice {
            settings.update { it.copy(clickOn = false) }
            settings.readDelay = 1.seconds
            val vm = practice()

            vm.toggleClick() // the screen still shows the default (on), so this turns the click off
            advanceTimeBy(2.seconds)
            runCurrent()

            assertFalse(vm.state.value.clickOn)
            settings.readDelay = Duration.ZERO
            assertFalse(settings.settings.first().clickOn)
        }

    @Test
    fun aStoredOffChoiceReadAfterTheRunStartedSilencesTheClick() =
        runPractice {
            settings.update { it.copy(clickOn = false) }
            settings.readDelay = 2.seconds
            val vm = practice()
            startRun(vm)
            assertNotNull(metronome.running.value) // the default click started before the read returned

            advanceTimeBy(3.seconds)
            runCurrent()

            assertFalse(vm.state.value.clickOn)
            assertNull(metronome.running.value)
        }

    @Test
    fun anUnreadableSettingsStoreLeavesTheClickOn() =
        runPractice {
            settings.failing = true

            assertTrue(practice().state.value.clickOn)
        }

    @Test
    fun aToggleThatCannotBeStoredStillChangesTheClickForThisScreen() =
        runPractice {
            val vm = practice()
            settings.failing = true

            vm.toggleClick()
            runCurrent()

            assertFalse(vm.state.value.clickOn)
            settings.failing = false
            assertTrue(settings.settings.first().clickOn)
        }

    @Test
    fun theMuteSettingIsKeptForTheNextScreen() =
        runPractice {
            practice().toggleClick()
            runCurrent()

            assertFalse(practice().state.value.clickOn)
        }

    private data class Pattern(
        val firstBeatNanos: Long,
    )
}
