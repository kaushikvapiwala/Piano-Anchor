package pianoanchor.engine.lesson

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.SceneHand
import pianoanchor.engine.Tunables
import kotlin.math.abs

private const val NOTES_PER_OCTAVE = 12
private const val WHITE_KEYS_PER_OCTAVE = 7
private val WHITE_INDEX_IN_OCTAVE = listOf(0, -1, 1, -1, 2, 3, -1, 4, -1, 5, -1, 6)

/** One tone of a See demo, pressed by [finger] (1 thumb to 5 little finger) on [note] from [startMs] for [lengthMs]. */
public data class FingerPress(
    val finger: Int,
    val note: MidiNote,
    val startMs: Long,
    val lengthMs: Long,
)

/** The hand of a See demo: its [hand], the white key under the thumb ([thumbNote]) and every press in [presses]. */
public data class HandTimeline(
    val hand: SceneHand,
    val thumbNote: MidiNote,
    val presses: List<FingerPress>,
)

/** Which fingers play a See demo (`docs/algorithms/try-checks.md`, "See"). Pure. */
public object HandDemo {
    /**
     * The hand for [demo]: each tone pressed by the finger over its key. Null when the demo has no tones, plays a
     * black key, or spans more than five white keys (no fake fingering).
     *
     * The thumb sits at the lowest white key of [position] (right hand; the highest for the left hand) when
     * [position] (MIDI notes of the concept's hand position) holds at most `HAND_MAX_WHITE_SPAN` white keys and
     * every demo note, so a demo that stops short of the position's edge still gets the position's fingering (left
     * C position: C3 is finger 5). Otherwise the thumb is over the lowest white key the demo plays (right hand; the
     * highest for the left hand). Finger k is over the key k - 1 white keys further up (left hand: down).
     */
    public fun of(
        demo: Demo,
        hand: SceneHand = SceneHand.RIGHT,
        position: IntRange? = null,
    ): HandTimeline? {
        val indexes = demo.tones.map { whiteIndex(it.note) }
        val whites = indexes.filterNotNull()
        val fits =
            whites.isNotEmpty() && whites.size == indexes.size &&
                whites.max() - whites.min() + 1 <= Tunables.HAND_MAX_WHITE_SPAN
        if (!fits) return null
        val anchor = positionAnchor(position, hand, whites)
        val thumb = anchor ?: if (hand == SceneHand.RIGHT) whites.min() else whites.max()
        val presses =
            demo.tones.mapIndexed { i, tone ->
                FingerPress(abs(whites[i] - thumb) + 1, tone.note, tone.startMs, tone.lengthMs)
            }
        val thumbNote = if (anchor != null) noteOfWhite(anchor) else demo.tones[whites.indexOf(thumb)].note
        return HandTimeline(hand, thumbNote, presses)
    }

    /** The white-key place of the thumb in [position], or null when it is unusable for the demo's [whites]. */
    private fun positionAnchor(
        position: IntRange?,
        hand: SceneHand,
        whites: List<Int>,
    ): Int? {
        val inPosition = position?.mapNotNull { whiteIndex(MidiNote(it)) }.orEmpty()
        val usable =
            inPosition.isNotEmpty() && inPosition.size <= Tunables.HAND_MAX_WHITE_SPAN &&
                whites.all { it in inPosition }
        return if (usable) (if (hand == SceneHand.RIGHT) inPosition.min() else inPosition.max()) else null
    }

    private fun noteOfWhite(index: Int): MidiNote =
        MidiNote(
            index / WHITE_KEYS_PER_OCTAVE * NOTES_PER_OCTAVE +
                WHITE_INDEX_IN_OCTAVE.indexOf(index % WHITE_KEYS_PER_OCTAVE),
        )

    /** The place of [note] among the white keys, or null for a black key. */
    private fun whiteIndex(note: MidiNote): Int? {
        val inOctave = WHITE_INDEX_IN_OCTAVE[note.number % NOTES_PER_OCTAVE]
        return if (inOctave < 0) null else note.number / NOTES_PER_OCTAVE * WHITE_KEYS_PER_OCTAVE + inOctave
    }
}
