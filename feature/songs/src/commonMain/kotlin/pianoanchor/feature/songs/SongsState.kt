package pianoanchor.feature.songs

import pianoanchor.core.model.MidiDevice
import pianoanchor.engine.content.PieceView
import pianoanchor.engine.matcher.TempoRung

/**
 * What the Songs screen shows. [pieces] is null until they have loaded (or when [storageFailed]); [piano] is the
 * connected piano, or null while none is.
 */
public data class SongsState(
    val pieces: List<PieceView>? = null,
    val piano: MidiDevice? = null,
    val storageFailed: Boolean = false,
)

/** Where tapping an unlocked piece leads. */
public sealed interface SongsTarget {
    /** Practice [passageId] on [device] at [rung]. */
    public data class Practice(
        val device: MidiDevice,
        val passageId: String,
        val rung: TempoRung,
    ) : SongsTarget

    /** No piano is connected: the Pianos screen. */
    public data object Pianos : SongsTarget
}
