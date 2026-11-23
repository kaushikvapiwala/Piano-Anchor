package pianoanchor.feature.songs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.content.PieceStatus
import pianoanchor.engine.content.PieceView
import pianoanchor.feature.songs.resources.Res
import pianoanchor.feature.songs.resources.songs_action_retry
import pianoanchor.feature.songs.resources.songs_error_storage
import pianoanchor.feature.songs.resources.songs_lock_glyph
import pianoanchor.feature.songs.resources.songs_locked_description
import pianoanchor.feature.songs.resources.songs_mastered
import pianoanchor.feature.songs.resources.songs_needs
import pianoanchor.feature.songs.resources.songs_new
import pianoanchor.feature.songs.resources.songs_progress
import pianoanchor.feature.songs.resources.songs_row_description
import pianoanchor.feature.songs.resources.songs_title
import pianoanchor.ui.shell.AppNavRail
import pianoanchor.ui.shell.RailDestination

private val RowMinHeight = 56.dp

/** The narrowest a piece card gets: the grid fits 2 to 3 of them beside the rail. */
private val MinCardWidth = 260.dp
private val CardShape = RoundedCornerShape(20.dp)

/**
 * Songs, landscape: the rail with Songs selected, then a heading over an adaptive grid with a card per piece under
 * its unit's heading, in curriculum order. A card shows the title and the progress ("rung 3 of 5", "Mastered",
 * "New"); a locked card shows a lock and the skill it needs and cannot be tapped. Stateless; nothing animates, so
 * screenshots stay still.
 */
@Composable
public fun SongsScreen(
    state: SongsState,
    actions: SongsActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.safeDrawingPadding()) {
            AppNavRail(
                RailDestination.SONGS,
                onToday = actions.onToday,
                onSongs = {},
                onPianos = actions.onPianos,
                onSettings = actions.onSettings,
            )
            // Title, error and unit headings span the grid; the pieces flow into 2 to 3 columns in visual order.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(MinCardWidth),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(Res.string.songs_title), style = MaterialTheme.typography.headlineMedium)
                }
                if (state.storageFailed) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ErrorCard(actions.onRetry) }
                }
                state.pieces?.groupBy { it.unitTitle }?.forEach { (unitTitle, pieces) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            unitTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(pieces, key = { it.passage.id }) { PieceCard(it, actions.onOpen) }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(onRetry: () -> Unit) {
    OutlinedCard(
        shape = CardShape,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.songs_error_storage))
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = RowMinHeight)) {
                Text(stringResource(Res.string.songs_action_retry), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun PieceCard(
    piece: PieceView,
    onOpen: (PieceView) -> Unit,
) {
    val locked = piece.status == PieceStatus.LOCKED
    val progress = progressLine(piece)
    val modifier =
        if (locked) {
            val description = stringResource(Res.string.songs_locked_description, piece.waitingFor?.title.orEmpty())
            // Not tappable; TalkBack reads one line saying why.
            Modifier.clearAndSetSemantics { contentDescription = description }
        } else {
            val description = stringResource(Res.string.songs_row_description, piece.passage.title, progress)
            Modifier.clickable(role = Role.Button) { onOpen(piece) }.clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
        }
    OutlinedCard(
        shape = CardShape,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(
                piece.passage.title,
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                progress,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun progressLine(piece: PieceView): String =
    when (piece.status) {
        PieceStatus.LOCKED -> {
            stringResource(Res.string.songs_lock_glyph) + " " +
                stringResource(Res.string.songs_needs, piece.waitingFor?.title.orEmpty())
        }

        PieceStatus.NEW -> {
            stringResource(Res.string.songs_new)
        }

        PieceStatus.MASTERED -> {
            stringResource(Res.string.songs_mastered)
        }

        PieceStatus.IN_PROGRESS -> {
            stringResource(Res.string.songs_progress, (piece.nextRung?.ordinal ?: 0) + 1, piece.totalRungs)
        }
    }
