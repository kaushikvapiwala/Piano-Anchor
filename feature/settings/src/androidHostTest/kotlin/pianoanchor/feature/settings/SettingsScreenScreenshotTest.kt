package pianoanchor.feature.settings

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Settings
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the settings in light and dark. `./gradlew :feature:settings:recordRoborazziAndroidHostTest`
 * rewrites them under `feature/settings/screenshots/`; fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenScreenshotTest {
    private fun capture(
        name: String,
        state: SettingsState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/SettingsScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { SettingsScreen(state, SettingsActions()) }
            }
        }
    }

    @Test
    fun defaults() = capture("defaults", SettingsState())

    @Test
    fun changed() =
        capture(
            "changed",
            SettingsState(
                Settings(DailyBudget.MIN_30, weeklyGoalDays = 7, clickOn = false, updateCheckOn = false),
            ),
        )

    @Test
    fun storageError() = capture("storage_error", SettingsState(storageFailed = true))
}

/** Android API level the screenshots render with (the newest Robolectric supports). */
private const val SDK = 36
