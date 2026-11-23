package pianoanchor.feature.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.PieceView
import pianoanchor.engine.content.Pieces
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.passageHistories

/**
 * Songs: lists every piece of the curriculum with its status and tempo-ladder progress, from each piece's history in
 * [attempts], and follows the connected piano (the first of [input]'s devices). Every piece is open. A store that
 * can't be read leaves [SongsState.storageFailed] until [refresh].
 */
public class SongsViewModel(
    private val attempts: AttemptRepositoryPort,
    input: MidiInputPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SongsState())
    public val state: StateFlow<SongsState> = mutableState.asStateFlow()

    private var load: Job? = null

    init {
        viewModelScope.launch {
            input.inputDevices.collect { devices ->
                mutableState.update { it.copy(piano = devices.firstOrNull()) }
            }
        }
        refresh()
    }

    /** Loads the pieces again, e.g. after coming back from a run or after a failed read. */
    public fun refresh() {
        load?.cancel()
        load = viewModelScope.launch { loadPieces() }
    }

    /**
     * Where tapping [piece] leads: Practice at its next rung (the top rung once mastered), or Pianos when no piano is
     * connected.
     */
    public fun target(piece: PieceView): SongsTarget {
        val device = mutableState.value.piano ?: return SongsTarget.Pianos
        return SongsTarget.Practice(device, piece.passage.id, piece.nextRung ?: TempoRung.P100)
    }

    private suspend fun loadPieces() {
        try {
            val history =
                attempts.passageHistories(Curriculum.passages.filter { it.piece })
            val pieces = Pieces.list(Curriculum, history)
            mutableState.update { it.copy(pieces = pieces, storageFailed = false) }
        } catch (_: StorageUnavailableException) {
            mutableState.update { it.copy(pieces = null, storageFailed = true) }
        }
    }
}
