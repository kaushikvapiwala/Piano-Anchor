package pianoanchor.feature.songs

import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Pieces
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.PassageHistory
import pianoanchor.ui.shell.PianoAnchorTheme
import kotlin.time.Instant

/**
 * Golden screenshots of Songs in light and dark. `./gradlew :feature:songs:recordRoborazziAndroidHostTest` rewrites
 * them under `feature/songs/screenshots/`; fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w915dp-h412dp-land-xxhdpi")
class SongsScreenScreenshotTest {
    private fun capture(
        name: String,
        state: SongsState,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/SongsScreen_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) { SongsScreen(state, SongsActions()) }
            }
        }
    }

    private fun played(
        id: String,
        vararg rungs: TempoRung,
    ): Pair<String, PassageHistory> {
        val passage = checkNotNull(Curriculum.passage("builtin:$id"))
        val tempos = rungs.mapNotNull { it.bpm(passage) }.toSet()
        return passage.id to PassageHistory(Instant.fromEpochSeconds(1), tempos, cleanWait = true)
    }

    private val history =
        mapOf(
            played("lightly-row", TempoRung.P60, TempoRung.P70),
            played("ode-to-joy", TempoRung.P60, TempoRung.P70, TempoRung.P85, TempoRung.P100),
        )

    @Test
    fun newInProgressAndMastered() =
        capture(
            "mixed",
            SongsState(pieces = Pieces.list(Curriculum, history)),
        )

    @Test
    fun storageError() = capture("storage_error", SongsState(storageFailed = true))
}

private const val SDK = 36
