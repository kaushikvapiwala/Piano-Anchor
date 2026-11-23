package pianoanchor.feature.onboarding

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.MidiDevice
import pianoanchor.engine.Tunables
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the onboarding steps in light and dark. `./gradlew
 * :feature:onboarding:recordRoborazziAndroidHostTest` rewrites them under `feature/onboarding/screenshots/`;
 * fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class OnboardingScreenScreenshotTest {
    private val piano = MidiDevice("usb:Roland/FP-30X", "FP-30X")

    private fun capture(
        name: String,
        state: OnboardingState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/OnboardingScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { OnboardingScreen(state, OnboardingActions(), calibration = {}) }
            }
        }
    }

    @Test
    fun connect() = capture("connect", OnboardingState())

    @Test
    fun connectNotFound() = capture("connect_not_found", OnboardingState(pianoListHint = true))

    @Test
    fun echo() =
        capture(
            "echo",
            OnboardingState(OnboardingStep.ECHO, piano, echo = EchoPhase.LISTENING, keysHighlighted = true),
        )

    @Test
    fun echoMissed() =
        capture(
            "echo_missed",
            OnboardingState(
                OnboardingStep.ECHO,
                piano,
                echo = EchoPhase.MISSED,
                misses = Tunables.ONBOARDING_MISSES_BEFORE_SKIP,
            ),
        )

    @Test
    fun success() = capture("success", OnboardingState(OnboardingStep.ECHO, piano, echo = EchoPhase.PASSED))

    @Test
    fun startPoint() = capture("start_point", OnboardingState(OnboardingStep.START_POINT, piano))
}

/** Android API level the screenshots render with (the newest Robolectric supports). */
private const val SDK = 36
