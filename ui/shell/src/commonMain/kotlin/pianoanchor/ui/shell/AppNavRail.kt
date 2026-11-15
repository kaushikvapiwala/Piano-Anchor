package pianoanchor.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.ui.shell.resources.Res
import pianoanchor.ui.shell.resources.nav_pianos
import pianoanchor.ui.shell.resources.nav_pianos_glyph
import pianoanchor.ui.shell.resources.nav_settings
import pianoanchor.ui.shell.resources.nav_settings_glyph
import pianoanchor.ui.shell.resources.nav_songs
import pianoanchor.ui.shell.resources.nav_songs_glyph
import pianoanchor.ui.shell.resources.nav_today
import pianoanchor.ui.shell.resources.nav_today_glyph

/**
 * The navigation rail of the top-level screens: Today, Songs, Pianos and the settings gear, each with a text label.
 * [selected] is the screen showing; tapping it does nothing, the others call their callback.
 */
@Composable
public fun AppNavRail(
    selected: RailDestination,
    onToday: () -> Unit,
    onSongs: () -> Unit,
    onPianos: () -> Unit,
    onSettings: () -> Unit,
) {
    NavigationRail(containerColor = Color.Transparent) {
        RailItem(selected == RailDestination.TODAY, Res.string.nav_today_glyph, Res.string.nav_today, onToday)
        RailItem(selected == RailDestination.SONGS, Res.string.nav_songs_glyph, Res.string.nav_songs, onSongs)
        RailItem(selected == RailDestination.PIANOS, Res.string.nav_pianos_glyph, Res.string.nav_pianos, onPianos)
        RailItem(
            selected == RailDestination.SETTINGS,
            Res.string.nav_settings_glyph,
            Res.string.nav_settings,
            onSettings,
        )
    }
}

@Composable
private fun RailItem(
    selected: Boolean,
    glyph: StringResource,
    label: StringResource,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = { if (!selected) onClick() },
        icon = { Text(stringResource(glyph), style = MaterialTheme.typography.titleLarge) },
        label = { Text(stringResource(label)) },
    )
}
