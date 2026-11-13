package pianoanchor.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Living Studio essentials (docs/ui/living-studio.md): one brand colour, light and dark Material 3 schemes.
private val BrandTeal = Color(0xFF1F6F6B)
private val BrandTealLight = Color(0xFF8AD3CC)
private val OnBrandTealLight = Color(0xFF00201E)

private val LightColors = lightColorScheme(primary = BrandTeal, onPrimary = Color.White)
private val DarkColors = darkColorScheme(primary = BrandTealLight, onPrimary = OnBrandTealLight)

/** The app's Material 3 theme in light or [darkTheme] (follows the system by default). */
@Composable
fun PianoAnchorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
