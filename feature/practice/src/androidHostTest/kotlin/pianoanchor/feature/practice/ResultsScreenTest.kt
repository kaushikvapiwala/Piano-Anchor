package pianoanchor.feature.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.RunMode
import pianoanchor.engine.grading.NoteValue
import pianoanchor.engine.grading.Observation
import pianoanchor.engine.grading.ResultsWords
import pianoanchor.engine.grading.Tip
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.test.assertEquals

/** The results screen's actions, on Robolectric. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land")
class ResultsScreenTest {
    private val ready =
        ResultsState.Ready(
            passageTitle = "C-D-E",
            runNumber = 1,
            band = Band.CLEAN,
            correctSteps = 3,
            stepCount = 3,
            wrongNotes = 0,
            incompleteChords = 0,
        )

    @Test
    fun tryAgainAndBackCallTheirActions() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                PianoAnchorTheme {
                    ResultsScreen(
                        ready,
                        onBack = { calls += "back" },
                        onTryAgain = { calls += "try again" },
                        onRetry = { calls += "retry" },
                    )
                }
            }

            onNodeWithText("Try again").performClick()
            onNodeWithContentDescription("Back").performClick()

            assertEquals(listOf("try again", "back"), calls)
        }

    @Test
    fun aSightReadingResultNamesTheLevelAndHowItMoved() =
        runComposeUiTest {
            var sight by mutableStateOf(SightOutcome(2, SightChange.DOWN, 1))
            setContent {
                PianoAnchorTheme {
                    ResultsScreen(
                        ready.copy(runNumber = null, sight = sight),
                        onBack = {},
                        onTryAgain = {},
                        onRetry = {},
                    )
                }
            }

            onNodeWithText("Sight-reading · level 2").assertIsDisplayed()
            onNodeWithText("Level down: 1").assertIsDisplayed()
            sight = SightOutcome(2, SightChange.UP, 3)
            onNodeWithText("Level up: 3").assertIsDisplayed()
            sight = SightOutcome(2, SightChange.STAYS, 2)
            onNodeWithText("Level stays 2").assertIsDisplayed()
            sight = SightOutcome(2, SightChange.REPLAY, 2)
            onNodeWithText("Only the first play moves your level").assertIsDisplayed()
        }

    @Test
    fun finishLessonShowsOnlyWhenStartedFromALesson() =
        runComposeUiTest {
            var finished = 0
            var fromLesson by mutableStateOf(false)
            setContent {
                PianoAnchorTheme {
                    ResultsScreen(
                        ready,
                        onBack = {},
                        onTryAgain = {},
                        onRetry = {},
                        onFinishLesson = if (fromLesson) ({ finished++ }) else null,
                    )
                }
            }

            onNodeWithText("Finish lesson").assertDoesNotExist()
            fromLesson = true
            onNodeWithText("Finish lesson").performClick()
            assertEquals(1, finished)
            onNodeWithText("Try again").assertIsDisplayed()
        }

    @Test
    fun theSpotLoopActionNamesItsBarsAndStartsTheLoop() =
        runComposeUiTest {
            val started = mutableListOf<BarRange>()
            setContent {
                PianoAnchorTheme {
                    ResultsScreen(
                        ready.copy(spot = BarRange(5, 6)),
                        onBack = {},
                        onTryAgain = {},
                        onRetry = {},
                        onLoopBars = { started += it },
                    )
                }
            }

            onNodeWithText("Loop bars 5–6").performClick()

            assertEquals(listOf(BarRange(5, 6)), started)
        }

    @Test
    fun theLessonLineSaysPassedOrAsksForARunWithoutHints() =
        runComposeUiTest {
            var state by mutableStateOf(ready.copy(lesson = LessonResult.PASSED))
            setContent { PianoAnchorTheme { ResultsScreen(state, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText("Lesson passed. It comes back tomorrow as a quick quiz.").assertIsDisplayed()
            state = ready.copy(lesson = LessonResult.CLEAN_WITH_HINTS)
            waitForIdle()
            onNodeWithText("Clean! Play it once without hints to pass the lesson.").assertIsDisplayed()
        }

    @Test
    fun theWordsAndTheTipShowAndTheTilesWaitBehindDetails() =
        runComposeUiTest {
            val words = ResultsWords(listOf(Observation.STARTED_LATE, Observation.NOTES_SHORT), Tip.WAIT_FOR_COUNT_IN)
            val state = ready.copy(band = Band.GOOD, tempoBpm = 48, words = words, shiftBeats = 1.5)
            setContent { PianoAnchorTheme { ResultsScreen(state, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText("You started about 1.5 beats late.").assertExists()
            onNodeWithText("Notes were short.").assertExists()
            onNodeWithText("Wait for 3, 2, 1, then play on the next click.").assertExists()
            onNodeWithContentDescription("Pitch: 3 of 3 correct, every note right").assertDoesNotExist()
            onNodeWithText("Details").assertIsDisplayed()
            onNodeWithText("Details").performClick()
            onNodeWithContentDescription("Pitch: 3 of 3 correct, every note right").assertExists()
        }

    @Test
    fun detailsToggleReportsItsStateToTalkBack() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { ResultsScreen(ready, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText(
                "Details",
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
            onNodeWithText("Details").performClick()
            onNodeWithText(
                "Details",
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
            onNodeWithText("Details").performClick()
            onNodeWithText(
                "Details",
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        }

    @Test
    fun everyObservationAndTipReadsAsASentence() =
        runComposeUiTest {
            val sentences =
                mapOf(
                    Observation.STARTED_EARLY to "You started before the count-in ended.",
                    Observation.WRONG_NOTES to "2 notes weren't right.",
                    Observation.NOTES_LONG to "Notes were held too long.",
                    Observation.OFF_BEAT to "Some notes came off the beat.",
                    Observation.PAUSED to "You paused a few times.",
                    Observation.CLEAN to "Clean run!",
                )
            var state by mutableStateOf<ResultsState>(ready.copy(wrongNotes = 2))
            setContent { PianoAnchorTheme { ResultsScreen(state, onBack = {}, onTryAgain = {}, onRetry = {}) } }
            for ((observation, text) in sentences) {
                state = ready.copy(wrongNotes = 2, words = ResultsWords(listOf(observation), Tip.NEXT_TEMPO))
                onNodeWithText(text).assertExists()
            }
            val tips =
                mapOf(
                    Tip.SLOW_DOWN to "Slow down: wait mode lets you take your time.",
                    Tip.HOLD_FULL_CLICKS to "Hold each note for its full clicks.",
                    Tip.LET_GO_SOONER to "Let go when the next click comes.",
                    Tip.LISTEN_TO_CLICK to "Listen for the click and land with it.",
                    Tip.PRACTISE_THE_PAUSE to "Play that spot slowly a few times.",
                    Tip.NEXT_TEMPO to "Try the next tempo.",
                )
            for ((tip, text) in tips) {
                state = ready.copy(words = ResultsWords(listOf(Observation.CLEAN), tip))
                onNodeWithText(text).assertExists()
            }
        }

    @Test
    fun aPauseNamesTheNoteItCameBefore() =
        runComposeUiTest {
            val words = ResultsWords(listOf(Observation.PAUSED), Tip.PRACTISE_THE_PAUSE)
            val state = ready.copy(words = words, hesitation = HesitationTile(1, Pause(14, "D4")))
            setContent { PianoAnchorTheme { ResultsScreen(state, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText("You paused before D4.").assertExists()
        }

    @Test
    fun aOneBarSpotReadsLoopBar() =
        runComposeUiTest {
            setContent {
                PianoAnchorTheme {
                    ResultsScreen(ready.copy(spot = BarRange(3, 3)), onBack = {}, onTryAgain = {}, onRetry = {})
                }
            }

            onNodeWithText("Loop bar 3").assertExists()
        }

    @Test
    fun aRunWithNoSpotOffersNoLoop() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { ResultsScreen(ready, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText("Loop bar", substring = true).assertDoesNotExist()
        }

    @Test
    fun aPerformRunIsLabelledPerformed() =
        runComposeUiTest {
            val performed = ready.copy(tempoBpm = 72, mode = RunMode.PERFORM)
            setContent { PianoAnchorTheme { ResultsScreen(performed, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText("C-D-E · run 1 · Performed · ♩ = 72").assertExists()
        }

    @Test
    fun thePitchTileReadsAsOneSentence() =
        runComposeUiTest {
            setContent { PianoAnchorTheme { ResultsScreen(ready, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText("Details").performClick()
            onNodeWithContentDescription("Pitch: 3 of 3 correct, every note right").assertExists()
        }

    @Test
    fun aTimedRunShowsFourTilesAndTheTempo() =
        runComposeUiTest {
            val timed =
                ready.copy(
                    tempoBpm = 72,
                    timing = TimingTile(31, TimingWorst(late = true, noteNumber = 6)),
                    length = LengthTile(86, LengthWorst(tooLong = false, noteNumber = 3, value = NoteValue.HALF)),
                    hesitation = HesitationTile(1, Pause(14, "D4")),
                )
            setContent { PianoAnchorTheme { ResultsScreen(timed, onBack = {}, onTryAgain = {}, onRetry = {}) } }

            onNodeWithText("C-D-E · run 1 · ♩ = 72").assertExists()
            onNodeWithText("Details").performClick()
            onNodeWithContentDescription("Timing: within 31 milliseconds on average, late at note 6").assertExists()
            onNodeWithContentDescription("Note length: 86 percent of notes held right, half note held short")
                .assertExists()
            onNodeWithContentDescription("Hesitation: 1 pause, 1.4 s before D4").assertExists()
        }

    @Test
    fun tileDetailsNameTheWorstNote() =
        runComposeUiTest {
            var state by mutableStateOf<ResultsState>(ready)
            setContent { PianoAnchorTheme { ResultsScreen(state, onBack = {}, onTryAgain = {}, onRetry = {}) } }
            onNodeWithText("Details").performClick()

            fun check(
                tile: ResultsState.Ready,
                description: String,
            ) {
                state = tile
                waitForIdle()
                onNodeWithContentDescription(description).assertExists()
            }
            val base = ready.copy(tempoBpm = 80)
            check(
                base.copy(timing = TimingTile(1, TimingWorst(late = false, noteNumber = 3))),
                "Timing: within 1 millisecond on average, early at note 3",
            )
            check(base.copy(timing = TimingTile(0, null)), "Timing: within 0 milliseconds on average, in time")
            check(
                base.copy(timing = TimingTile(0, null, calibrated = false)),
                "Timing: within 0 milliseconds on average, in time · not calibrated",
            )
            check(
                base.copy(length = LengthTile(100, null)),
                "Note length: 100 percent of notes held right, every note held right",
            )
            check(
                base.copy(length = LengthTile(50, LengthWorst(true, 4, null))),
                "Note length: 50 percent of notes held right, note 4 held long",
            )
            check(
                base.copy(length = LengthTile(50, LengthWorst(true, 1, NoteValue.WHOLE))),
                "Note length: 50 percent of notes held right, whole note held long",
            )
            check(
                base.copy(length = LengthTile(50, LengthWorst(false, 1, NoteValue.QUARTER))),
                "Note length: 50 percent of notes held right, quarter note held short",
            )
            check(
                base.copy(wrongNotes = 1, missedNotes = 2, incompleteChords = 1, correctSteps = 0),
                "Pitch: 0 of 3 correct, 1 wrong note, 2 notes missed, 1 chord incomplete",
            )
            check(base.copy(hesitation = HesitationTile(0, null)), "Hesitation: no pauses")
            check(base.copy(hesitation = HesitationTile(2, null)), "Hesitation: 2 pauses")
        }

    @Test
    fun unavailableOffersRetryAndBackToPianos() =
        runComposeUiTest {
            val calls = mutableListOf<String>()
            setContent {
                PianoAnchorTheme {
                    ResultsScreen(
                        ResultsState.Unavailable,
                        onBack = { calls += "back" },
                        onTryAgain = { calls += "try again" },
                        onRetry = { calls += "retry" },
                    )
                }
            }

            onNodeWithText("Couldn't load this run.").assertExists()
            onNodeWithText("Retry").performClick()
            onNodeWithText("Back to pianos").performClick()

            assertEquals(listOf("retry", "back"), calls)
        }
}
