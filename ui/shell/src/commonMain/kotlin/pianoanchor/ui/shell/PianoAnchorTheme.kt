package pianoanchor.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import pianoanchor.ui.shell.resources.Res
import pianoanchor.ui.shell.resources.inter
import pianoanchor.ui.shell.resources.space_grotesk

// Living Studio palette (docs/ui/living-studio.md): plum primary, warm paper background, feedback tokens.
private val Plum = Color(0xFF7A3E6B)
private val PlumDark = Color(0xFFE3A6D0)
private val OnPlumDark = Color(0xFF3B1834)
private val Paper = Color(0xFFFBFAF8)
private val Ink = Color(0xFF1B1B1F)
private val InkSecondary = Color(0xFF5E5A66)
private val PaperDark = Color(0xFFF7F5F2)
private val Mist = Color(0xFFCFC8D3)
private val Divider = Color(0xFFEEE9EF)
private val ChipBorder = Color(0xFFD9CFDC)
private val DividerDark = Color(0xFF3F3B45)
private val Graphite = Color(0xFF2A2730)

private val LightColors =
    lightColorScheme(
        primary = Plum,
        onPrimary = Color.White,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        onSurfaceVariant = InkSecondary,
        outline = ChipBorder,
        outlineVariant = Divider,
    )
private val DarkColors =
    darkColorScheme(
        primary = PlumDark,
        onPrimary = OnPlumDark,
        background = Ink,
        onBackground = PaperDark,
        surface = Ink,
        onSurface = PaperDark,
        onSurfaceVariant = Mist,
        outline = InkSecondary,
        outlineVariant = DividerDark,
    )

/**
 * Feedback colours on top of the Material scheme: [correct] (played right), [waiting] (the note the app waits for),
 * [timing] (timing off, unused until timing exists), [wrongKey] (a wrong key), plus the staff's [staffLine] and
 * [upcomingNote], the key strip's [targetBand], [whiteKey], [blackKey] and [keyBorder], and the results screen's
 * [page] and its [tile]s.
 */
@Immutable
data class PianoAnchorColors(
    val correct: Color,
    val waiting: Color,
    val timing: Color,
    val wrongKey: Color,
    val staffLine: Color,
    val upcomingNote: Color,
    val targetBand: Color,
    val whiteKey: Color,
    val blackKey: Color,
    val keyBorder: Color,
    val page: Color,
    val tile: Color,
)

private val LightFeedback =
    PianoAnchorColors(
        correct = Color(0xFF1F5F8B),
        waiting = Color(0xFFE69F00),
        timing = Color(0xFFD55E00),
        wrongKey = Color(0xFFB86FA3),
        staffLine = DividerDark,
        upcomingNote = Ink,
        targetBand = Color(0xFFFBE3B0),
        whiteKey = Color.White,
        blackKey = Graphite,
        keyBorder = Mist,
        page = PaperDark,
        tile = Color.White,
    )
private val DarkFeedback =
    PianoAnchorColors(
        correct = Color(0xFF8CC4E8),
        waiting = Color(0xFFF2B84B),
        timing = Color(0xFFF08A4B),
        wrongKey = PlumDark,
        staffLine = Mist,
        upcomingNote = PaperDark,
        targetBand = Color(0xFFFBE3B0),
        whiteKey = PaperDark,
        blackKey = Graphite,
        keyBorder = InkSecondary,
        page = Ink,
        tile = Graphite,
    )

private val LocalPianoAnchorColors = staticCompositionLocalOf { LightFeedback }

/** The Living Studio feedback colours of the current [PianoAnchorTheme]. */
val MaterialTheme.pianoAnchorColors: PianoAnchorColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPianoAnchorColors.current

private val INTER_WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
private val SPACE_GROTESK_WEIGHTS = listOf(FontWeight.Medium, FontWeight.Bold)

/** Space Grotesk (500, 700): titles and headlines. */
val displayFontFamily: FontFamily
    @Composable
    get() = variableFamily(Res.font.space_grotesk, SPACE_GROTESK_WEIGHTS)

@Composable
private fun variableFamily(
    font: FontResource,
    weights: List<FontWeight>,
): FontFamily =
    FontFamily(
        weights.map { weight ->
            Font(font, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
        },
    )

@Composable
private fun livingStudioTypography(): Typography {
    val body = variableFamily(Res.font.inter, INTER_WEIGHTS)
    val display = displayFontFamily
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = display),
        displayMedium = base.displayMedium.copy(fontFamily = display),
        displaySmall = base.displaySmall.copy(fontFamily = display),
        headlineLarge = base.headlineLarge.copy(fontFamily = display),
        headlineMedium = base.headlineMedium.copy(fontFamily = display),
        headlineSmall = base.headlineSmall.copy(fontFamily = display),
        titleLarge = base.titleLarge.copy(fontFamily = display),
        titleMedium = base.titleMedium.copy(fontFamily = body),
        titleSmall = base.titleSmall.copy(fontFamily = body),
        bodyLarge = base.bodyLarge.copy(fontFamily = body),
        bodyMedium = base.bodyMedium.copy(fontFamily = body),
        bodySmall = base.bodySmall.copy(fontFamily = body),
        labelLarge = base.labelLarge.copy(fontFamily = body),
        labelMedium = base.labelMedium.copy(fontFamily = body),
        labelSmall = base.labelSmall.copy(fontFamily = body),
    )
}

/**
 * The app's Material 3 theme in light or [darkTheme] (follows the system by default): the Living Studio palette,
 * Inter and Space Grotesk, and [pianoAnchorColors].
 */
@Composable
fun PianoAnchorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPianoAnchorColors provides if (darkTheme) DarkFeedback else LightFeedback) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = livingStudioTypography(),
            content = content,
        )
    }
}
