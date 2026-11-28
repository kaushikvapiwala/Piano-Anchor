package pianoanchor.feature.practice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PedalSpan
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The sustain pedal as "continue" on the results. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultsPedalTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val attempts = FakeAttemptRepository()
    private val settings = FakeSettingsRepository()
    private val learner = LearnerModel(Curriculum.graph, FakeLearnerRepository())

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun attempt(id: String) =
        Attempt(
            id = id,
            passageId = Passages.CDE.id,
            deviceId = "usb-1",
            startedAt = Instant.fromEpochSeconds(100),
            durationNanos = 2_000_000_000,
            stepCount = 3,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = emptyList(),
            tempoBpm = null,
            playedNotes = emptyList(),
        )

    private suspend fun TestScope.results(
        id: String,
        vararg saved: Attempt,
        passageOf: (String) -> Passage? = { Curriculum.passage(it) },
    ): ResultsViewModel {
        saved.forEach { attempts.save(it) }
        return ResultsViewModel(
            id,
            TempoRung.WAIT,
            attempts,
            settings,
            learner,
            piano.device,
            piano,
            dispatcher,
            passageOf,
        ).also { runCurrent() }
    }

    private fun TestScope.tapPedal() {
        piano.play(byteArrayOf(0xB0.toByte(), 64, 127))
        piano.play(byteArrayOf(0xB0.toByte(), 64, 0))
        runCurrent()
    }

    private fun TestScope.collectContinues(vm: ResultsViewModel): MutableList<Unit> {
        val taps = mutableListOf<Unit>()
        backgroundScope.launch(dispatcher) { vm.pedalContinues.collect { taps += it } }
        runCurrent()
        return taps
    }

    @Test
    fun aPedalTapOnTheResultsAsksToContinueOnce() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1"))
            val taps = collectContinues(vm)
            assertTrue(vm.pedalHint.value)

            tapPedal()
            tapPedal()

            assertEquals(1, taps.size)
            vm.releasePiano()
        }

    @Test
    fun theResultsIgnoreThePedalWithTheSettingOff() =
        runTest(dispatcher) {
            settings.update { it.copy(pedalControlOn = false) }
            val vm = results("a-1", attempt("a-1"))
            val taps = collectContinues(vm)

            tapPedal()

            assertTrue(taps.isEmpty())
            assertFalse(vm.pedalHint.value)
            vm.releasePiano()
        }

    @Test
    fun noPedalHintWhileTheResultsAreNotShown() =
        runTest(dispatcher) {
            val vm = results("missing")
            val taps = collectContinues(vm)

            tapPedal()

            assertTrue(taps.isEmpty())
            assertFalse(vm.pedalHint.value)
            vm.releasePiano()
        }

    @Test
    fun anUnreadableSettingLeavesThePedalOff() =
        runTest(dispatcher) {
            settings.failing = true
            val vm = results("a-1", attempt("a-1"))

            tapPedal()

            assertFalse(vm.pedalHint.value)
            vm.releasePiano()
        }

    @Test
    fun aPianoThatGoesAwayTakesThePedalHintWithIt() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1"))
            assertTrue(vm.pedalHint.value)

            piano.unplug()
            runCurrent()

            assertFalse(vm.pedalHint.value)
            vm.releasePiano()
        }

    @Test
    fun aPianoThatIsAlreadyGoneHasNoPedalControl() =
        runTest(dispatcher) {
            piano.unplug()
            val vm = results("a-1", attempt("a-1"))

            assertFalse(vm.pedalHint.value)
            vm.releasePiano()
        }

    @Test
    fun releasingThePianoClosesTheReader() =
        runTest(dispatcher) {
            val vm = results("a-1", attempt("a-1"))
            val taps = collectContinues(vm)
            vm.releasePiano()

            tapPedal()

            assertTrue(taps.isEmpty())
            assertFalse(vm.pedalHint.value)
        }

    @Test
    fun aPassageWithPedalMarksIgnoresThePedalTap() =
        runTest(dispatcher) {
            val marked = Passages.CDE.copy(pedal = listOf(PedalSpan(0, 3840)))
            val vm = results("a-1", attempt("a-1"), passageOf = { marked })
            val taps = collectContinues(vm)

            tapPedal()

            assertTrue(taps.isEmpty())
            assertFalse(vm.pedalHint.value)
            vm.releasePiano()
        }
}
