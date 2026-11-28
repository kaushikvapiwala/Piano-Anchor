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
import pianoanchor.core.model.PedalSpan
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.NoteText
import pianoanchor.testing.TEST_RIGHTS
import pianoanchor.ui.shell.PianoAnchorTheme

private const val FONT_SCALE_LARGE = 2f
private const val BAR_TICKS = 4 * TICKS_PER_QUARTER

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
    private val auntRhody = Curriculum.passage("builtin:go-tell-aunt-rhody")!!
    private val amazingGrace = Curriculum.passage("builtin:amazing-grace")!!
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

    /** F major with a B-flat (no sign), a B natural and an E-flat, over a left-hand line with the same signs. */
    private val fMajor =
        Passage(
            id = "test:f-major",
            title = "F major",
            steps =
                (
                    NoteText.parse("F4q A4q Bb4q B4q | Bb4q A4q Eb4q F4q", TimeSignature(4, 4)) +
                        NoteText.parse("F3h Bb2h | Bb2q B2q Eb3h", TimeSignature(4, 4))
                ).groupBy { it.onsetTicks }
                    .toSortedMap()
                    .map { (onset, same) -> Step(onset, same.flatMapTo(LinkedHashSet()) { it.notes }) },
            timeSignature = TimeSignature(4, 4),
            tempoBpm = 80,
            rights = TEST_RIGHTS,
            keySignature = KeySignature.F,
        )

    /** Two bars of 6/8: two beamed threes, then a dotted-quarter rest and a beamed three. */
    private val sixEight =
        Passage(
            id = "test:six-eight",
            title = "Six eight",
            steps = NoteText.parse("C4e D4e E4e G4e F4e E4e | rq. G4e F4e E4e", TimeSignature(6, 8)),
            timeSignature = TimeSignature(6, 8),
            tempoBpm = 80,
            rights = TEST_RIGHTS,
        )

    /** Four bars over left-hand triads with the pedal changed in every bar. */
    private val pedalChanges =
        Passage(
            id = "test:pedal-changes",
            title = "Pedal changes",
            steps =
                (
                    NoteText.parse("C4q E4q G4q E4q | F4q A4q C5q A4q | G4q B4q D5q B4q | C5w", TimeSignature(4, 4)) +
                        NoteText.parse(
                            "[C3 E3 G3]w | [F2 A2 C3]w | [G2 B2 D3]w | [C3 E3 G3]w",
                            TimeSignature(4, 4),
                        )
                ).groupBy { it.onsetTicks }
                    .toSortedMap()
                    .map { (onset, same) -> Step(onset, same.flatMapTo(LinkedHashSet()) { it.notes }) },
            timeSignature = TimeSignature(4, 4),
            tempoBpm = 80,
            rights = TEST_RIGHTS,
            pedal = (0 until 4).map { PedalSpan(it * BAR_TICKS, (it + 1) * BAR_TICKS) },
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
    fun aFlatKeySignatureAndFlatNotesOnBothStaves() = capture("f_major", fMajor)

    @Test
    fun sixEightBeamsInThreesAndRestsADottedQuarter() = capture("six_eight", sixEight)

    @Test
    fun goTellAuntRhodyShowsTheKeySignatureOnBothStaves() = capture("aunt_rhody", auntRhody)

    @Test
    fun amazingGraceShowsThreeFourWithAPedalLineAndNotches() = capture("amazing_grace", amazingGrace)

    @Test
    fun odeWithChordsAtFontScale2() = capture("ode_with_chords_font2", odeWithChords, FONT_SCALE_LARGE)

    @Test
    fun turnTakingAtFontScale2() = capture("turn_taking_font2", turnTaking, FONT_SCALE_LARGE)

    @Test
    fun gMajorAtFontScale2() = capture("g_major_font2", gMajor, FONT_SCALE_LARGE)

    @Test
    fun aPedalChangeInEveryBarDrawsOneLineWithNotches() = capture("pedal_changes", pedalChanges)

    @Test
    fun pedalChangesAtFontScale2() = capture("pedal_changes_font2", pedalChanges, FONT_SCALE_LARGE)
}
