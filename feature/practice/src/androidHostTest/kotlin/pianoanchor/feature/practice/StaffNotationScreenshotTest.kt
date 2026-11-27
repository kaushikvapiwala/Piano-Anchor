package pianoanchor.feature.practice

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.Passage
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.NoteText
import pianoanchor.testing.TEST_RIGHTS
import pianoanchor.ui.shell.PianoAnchorTheme

private const val FONT_SCALE_LARGE = 2f

/**
 * Golden screenshots of the staff's notation (stems, flags, beams, dots, rests, key signature, accidentals), in light
 * and dark and at font scale 2.0 (the staff stays the same size). `./gradlew
 * :feature:practice:recordRoborazziAndroidHostTest` rewrites them under `feature/practice/screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class StaffNotationScreenshotTest {
    private val odeWithChords = Curriculum.passage("builtin:ode-to-joy-held-bass")!!
    private val turnTaking = Curriculum.passages.first { it.title == "Turn-taking" }
    private val gMajor =
        Passage(
            id = "test:g-major",
            title = "G major",
            steps =
                NoteText.parse(
                    "G4q F#4q G4q A4q | B4q F4q G4e A4e B4q | C5q B4q A4q G4q | G4w",
                    TimeSignature(4, 4),
                ),
            timeSignature = TimeSignature(4, 4),
            tempoBpm = 80,
            rights = TEST_RIGHTS,
            keySignature = KeySignature.G,
        )

    private fun capture(
        name: String,
        passage: Passage,
        fontScale: Float = 1f,
    ) {
        val state = PracticeState.start(passage)
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/StaffNotation_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                        PracticeScreen(state, {}, {}, {}, {}, {})
                    }
                }
            }
        }
    }

    @Test
    fun dottedQuartersEighthsAndBeamsOverAHeldBass() = capture("ode_with_chords", odeWithChords)

    @Test
    fun theHandsTakeTurnsWithRests() = capture("turn_taking", turnTaking)

    @Test
    fun aKeySignatureAndANaturalSign() = capture("g_major", gMajor)

    @Test
    fun odeWithChordsAtFontScale2() = capture("ode_with_chords_font2", odeWithChords, FONT_SCALE_LARGE)

    @Test
    fun turnTakingAtFontScale2() = capture("turn_taking_font2", turnTaking, FONT_SCALE_LARGE)

    @Test
    fun gMajorAtFontScale2() = capture("g_major_font2", gMajor, FONT_SCALE_LARGE)
}
