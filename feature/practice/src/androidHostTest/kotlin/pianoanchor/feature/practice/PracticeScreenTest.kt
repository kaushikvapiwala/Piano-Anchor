package pianoanchor.feature.practice

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.guidance.wrongKeyHint
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.ui.shell.PianoAnchorTheme
import pianoanchor.ui.shell.pianoAnchorColors
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The practice screen's accessibility text, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class PracticeScreenTest {
    @Test
    fun staffDescriptionIncludesTheTimeSignature() =
        runComposeUiTest {
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(PracticeState.start(Passages.CDE), onBack = {
                    }, onSelectRung = {}, onToggleClick = {}, onRetrySave = {}, onDiscard = {})
                }
            }

            onNodeWithContentDescription("Treble staff, 4/4. Notes C4, D4, E4. Waiting for C4.").assertExists()
        }

    @Test
    fun finishedStaffReadsDoneWithItsTimeSignature() =
        runComposeUiTest {
            val start = PracticeState.start(Passages.CDE.copy(timeSignature = TimeSignature(3, 4)))
            val done = start.copy(steps = start.steps.map { it.copy(status = StepStatus.DONE) })
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(
                        done,
                        onBack = {},
                        onSelectRung = {},
                        onToggleClick = {},
                        onRetrySave = {},
                        onDiscard = {},
                    )
                }
            }

            onNodeWithContentDescription("Treble staff, 3/4. Notes C4, D4, E4. Done.").assertExists()
        }

    private fun ComposeUiTest.show(
        state: PracticeState,
        onSelectRung: (TempoRung) -> Unit = {},
        onToggleClick: () -> Unit = {},
    ) = setContent {
        PianoAnchorTheme { PracticeScreen(state, {}, onSelectRung, onToggleClick, {}, {}) }
    }

    @Test
    fun performIsDisabledAtWaitAndSaysWhy() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE))

            onNodeWithContentDescription("Pick a tempo to perform").assertIsNotEnabled()
            onNodeWithContentDescription("Practice").assertIsSelected()
        }

    @Test
    fun tappingModeSegmentsSelectsTheMode() =
        runComposeUiTest {
            val picked = mutableListOf<RunMode>()
            val timed = PracticeState.start(Passages.CDE).copy(rung = TempoRung.P70)
            setContent {
                PianoAnchorTheme { PracticeScreen(timed, {}, {}, {}, {}, {}, onSelectMode = { picked += it }) }
            }

            onNodeWithContentDescription("Perform").performClick()
            onNodeWithContentDescription("Practice").performClick()

            assertEquals(listOf(RunMode.PERFORM, RunMode.PRACTICE), picked)
        }

    @Test
    fun practiceShowsTheHintButton() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P70))

            onNodeWithContentDescription("Show hint").assertExists()
        }

    @Test
    fun performHidesTheHintButton() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P70, mode = RunMode.PERFORM))

            onNodeWithContentDescription("Show hint").assertDoesNotExist()
            onNodeWithContentDescription("Perform").assertIsSelected()
        }

    @Test
    fun tappingARungSelectsIt() =
        runComposeUiTest {
            var picked: TempoRung? = null
            show(PracticeState.start(Passages.CDE), onSelectRung = { picked = it })

            onNodeWithContentDescription("85%").performClick()

            assertEquals(TempoRung.P85, picked)
        }

    @Test
    fun aDoneRungIsReadAsDoneAndTheSelectedOneIsSelected() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P70, doneRungs = setOf(TempoRung.P60)))

            onNodeWithContentDescription("60%, done").assertExists()
            onNodeWithContentDescription("70%").assertIsSelected()
            onNodeWithContentDescription("Wait").assertIsNotSelected()
        }

    @Test
    fun theClickSwitchNamesItsStateAndToggles() =
        runComposeUiTest {
            var toggles = 0
            val start = PracticeState.start(Passages.CDE)
            val state = mutableStateOf(start)
            setContent {
                PianoAnchorTheme { PracticeScreen(state.value, {}, {}, { toggles++ }, {}, {}) }
            }

            onNodeWithContentDescription("Click on").performClick()
            assertEquals(1, toggles)

            state.value = start.copy(clickOn = false)
            onNodeWithContentDescription("Click off").assertExists()
        }

    @Test
    fun theHintButtonIsReadAsShowHintAndTapsThrough() =
        runComposeUiTest {
            var hints = 0
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(PracticeState.start(Passages.CDE), {}, {}, {}, {}, {}, onHint = { hints++ })
                }
            }

            onNodeWithContentDescription("Show hint").performClick()

            assertEquals(1, hints)
        }

    @Test
    fun readyTimedRunSaysToPlayAKey() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P60))

            onNodeWithText("Play any key to count in").assertExists()
            onNodeWithText("Tempo run · 48 BPM").assertExists()
        }

    @Test
    fun aRunRestartedByAResumedLinkSaysWhy() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P60, linkResumed = true))

            onNodeWithText("The piano disconnected for a moment. Starting again.").assertExists()
            onNodeWithText("Play any key to count in").assertExists()
        }

    @Test
    fun countInShowsTheBeatAndTheTimedStaffReadsTheCursor() =
        runComposeUiTest {
            show(PracticeState.start(Passages.CDE).copy(rung = TempoRung.P60, phase = RunPhase.COUNT_IN, beat = 2))

            onNodeWithContentDescription("Beat 2 of 4").assertExists()
            onNodeWithText("Count-in").assertExists()
            onNodeWithContentDescription("Treble staff, 4/4. Notes C4, D4, E4. Cursor on C4.").assertExists()
        }

    @Test
    fun timingOffNotesAreAnnouncedLateOrEarly() =
        runComposeUiTest {
            val start = PracticeState.start(Passages.CDE).copy(rung = TempoRung.P60, phase = RunPhase.PLAYING)
            val c4 = MidiNote(60)
            val late = start.copy(lastKey = KeyFlash(c4, correct = true, deviationMs = 92))
            val state = mutableStateOf(late)
            setContent { PianoAnchorTheme { PracticeScreen(state.value, {}, {}, {}, {}, {}) } }

            onNodeWithContentDescription("Late, 92 ms").assertExists()
            onNodeWithText("C4 · late, 92 ms").assertExists()

            state.value = start.copy(lastKey = KeyFlash(c4, correct = true, deviationMs = -40))
            onNodeWithContentDescription("Early, 40 ms").assertExists()

            state.value = start.copy(lastKey = KeyFlash(c4, correct = true))
            onNodeWithText("C4 · on time").assertExists()

            state.value = start.copy(lastKey = KeyFlash(c4, correct = false))
            onNodeWithText("C4 · wrong note").assertExists()
        }

    @Test
    fun theCalibrateCardOffersCalibrateAndSkip() =
        runComposeUiTest {
            var calibrate = 0
            var skip = 0
            val timed = PracticeState.start(Passages.CDE).copy(rung = TempoRung.P60)
            val state = mutableStateOf(timed)
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(
                        state.value,
                        {},
                        {},
                        {},
                        {},
                        {},
                        onCalibrate = { calibrate++ },
                        onSkipCalibration = { skip++ },
                    )
                }
            }
            onNodeWithText("Calibrate timing · about 16 s").assertDoesNotExist()

            state.value = timed.copy(calibrationOffered = true)
            onNodeWithText("Calibrate timing · about 16 s").assertExists()
            onNodeWithText("Play any key to count in").assertDoesNotExist() // nothing reads the piano meanwhile
            onNodeWithText("Calibrate").performClick()
            onNodeWithText("Skip").performClick()

            assertEquals(1, calibrate)
            assertEquals(1, skip)
        }

    private val spot =
        PracticeState.start(Passages.CDE).copy(spotLoop = SpotLoopState(BarRange(5, 6), fullRung = TempoRung.P85))

    @Test
    fun aSpotLoopShowsItsProgressAndNoModeSwitch() =
        runComposeUiTest {
            show(spot)

            onNodeWithText("Spot loop · bars 5–6 · clean 0 of 2").assertExists()
            onNodeWithContentDescription("Practice").assertDoesNotExist()
        }

    @Test
    fun aOneBarSpotLoopSaysBar() =
        runComposeUiTest {
            show(spot.copy(spotLoop = SpotLoopState(BarRange(3, 3), TempoRung.WAIT, cleanLoops = 1)))

            onNodeWithText("Spot loop · bar 3 · clean 1 of 2").assertExists()
        }

    @Test
    fun theLastLoopsResultShows() =
        runComposeUiTest {
            val clean = LoopResult(clean = true, wrongNotes = 0, incompleteChords = 0)
            val withSlips = LoopResult(clean = false, wrongNotes = 2, incompleteChords = 1)
            val timingOnly = LoopResult(clean = false, wrongNotes = 0, incompleteChords = 0)
            val last = mutableStateOf(clean)
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(
                        spot.copy(spotLoop = spot.spotLoop?.copy(lastLoop = last.value)),
                        {},
                        {},
                        {},
                        {},
                        {},
                    )
                }
            }

            onNodeWithText("Clean").assertExists()
            last.value = withSlips
            onNodeWithText("2 wrong notes, 1 chord incomplete").assertExists()
            last.value = timingOnly
            onNodeWithText("Not clean").assertExists()
        }

    @Test
    fun theCleanCardOffersThePieceOrMoreLoops() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            val done = spot.copy(spotLoop = spot.spotLoop?.copy(cleanLoops = 2, offerFull = true))
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(
                        done,
                        {},
                        {},
                        {},
                        {},
                        {},
                        onPlayWhole = { calls += "play" },
                        onKeepLooping = { calls += "keep" },
                    )
                }
            }

            onNodeWithText("Bars 5–6 are clean. Play the whole piece?").assertExists()
            onNodeWithText("Play it").performClick()
            onNodeWithText("Keep looping").performClick()
            assertEquals(listOf("play", "keep"), calls)
        }

    @Test
    fun theCleanCardForOneBarSaysBar() =
        runComposeUiTest {
            val one = SpotLoopState(BarRange(3, 3), TempoRung.WAIT, offerFull = true)
            show(spot.copy(spotLoop = one))

            onNodeWithText("Bar 3 is clean. Play the whole piece?").assertExists()
        }

    private val start = PracticeState.start(Passages.CDE)

    @Test
    fun theHintLineIsShownAndAnnounced() =
        runComposeUiTest {
            val state = start.copy(hint = wrongKeyHint(played = 65, expected = 60), hintGlow = setOf(MidiNote(60)))
            setContent { PianoAnchorTheme { PracticeScreen(state, {}, {}, {}, {}, {}) } }

            onNodeWithText("That was F4. C is just left of the two black keys.")
                .assertExists()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        }

    @Test
    fun everyLandmarkHasItsLine() =
        runComposeUiTest {
            val state = mutableStateOf(start)
            setContent { PianoAnchorTheme { PracticeScreen(state.value, {}, {}, {}, {}, {}) } }
            val lines =
                listOf(
                    wrongKeyHint(65, 62) to "That was F4. D is between the two black keys.",
                    wrongKeyHint(60, 64) to "That was C4. E is just right of the two black keys.",
                    wrongKeyHint(60, 65) to "That was C4. F is just left of the three black keys.",
                    wrongKeyHint(60, 67) to "That was C4. G is in the three black keys, just left of the middle one.",
                    wrongKeyHint(60, 69) to "That was C4. A is in the three black keys, just right of the middle one.",
                    wrongKeyHint(60, 71) to "That was C4. B is just right of the three black keys.",
                    wrongKeyHint(60, 61) to "That was C4. C♯ is the black key just right of C.",
                    wrongKeyHint(72, 60) to "That was C5. Same letter, other octave: try C4.",
                )
            lines.forEach { (hint, text) ->
                state.value = start.copy(hint = hint)
                onNodeWithText(text).assertExists()
            }
        }

    @Test
    fun noHintLineWithoutAWrongKey() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { PracticeScreen(start, {}, {}, {}, {}, {}) } }

            onNodeWithText("That was", substring = true).assertDoesNotExist()
        }

    @Test
    fun theCountInShowsThreeTwoOneThenPlay() =
        runComposeUiTest {
            val timed = start.copy(rung = TempoRung.P60)
            val state = mutableStateOf(timed.copy(phase = RunPhase.COUNT_IN, beat = 1))
            setContent { PianoAnchorTheme { PracticeScreen(state.value, {}, {}, {}, {}, {}) } }

            onNodeWithText("3").assertDoesNotExist()
            val seen = mutableListOf<String>()
            for (beat in 2..4) {
                state.value = timed.copy(phase = RunPhase.COUNT_IN, beat = beat)
                val numeral = (5 - beat).toString()
                onNodeWithText(numeral)
                    .assertExists()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
                seen += numeral
            }
            state.value = timed.copy(phase = RunPhase.PLAYING, beat = 1, playNow = true)
            onNodeWithText("Play!").assertExists()
            state.value = timed.copy(phase = RunPhase.PLAYING, beat = 2)
            onNodeWithText("Play!").assertDoesNotExist()
            assertEquals(listOf("3", "2", "1"), seen)
        }

    @Test
    fun listenAndSkipCallBack() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            val state = mutableStateOf(start.copy(canListen = true))
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(
                        state.value,
                        {},
                        {},
                        {},
                        {},
                        {},
                        onListen = { calls += "listen" },
                        onSkipListening = { calls += "skip" },
                    )
                }
            }

            onNodeWithText("Listen").performClick()
            state.value = start.copy(canListen = true, listening = true, demoStep = 1)
            onNodeWithText("Skip").performClick()
            assertEquals(listOf("listen", "skip"), calls)
        }

    @Test
    fun aUnitZeroPassageHasNoStaff() =
        runComposeUiTest {
            val unit0 =
                Curriculum.units
                    .first { it.id == "unit-0" }
                    .passages
                    .first()
            setContent {
                PianoAnchorTheme {
                    PracticeScreen(
                        PracticeState.start(unit0).copy(staffHidden = true),
                        {},
                        {},
                        {},
                        {},
                        {},
                    )
                }
            }

            onNodeWithContentDescription("Treble staff", substring = true).assertDoesNotExist()
        }

    @Test
    fun theWrongKeyColourIsGreyNotRedInBothThemes() =
        runComposeUiTest {
            val colors = mutableListOf<Color>()
            setContent {
                for (dark in listOf(false, true)) {
                    PianoAnchorTheme(darkTheme = dark) { colors += MaterialTheme.pianoAnchorColors.wrongKey }
                }
            }

            assertEquals(2, colors.size)
            colors.forEach {
                assertTrue(abs(it.red - it.green) < GREY_TOLERANCE && abs(it.green - it.blue) < GREY_TOLERANCE)
            }
        }
}

private const val GREY_TOLERANCE = 0.02f
