package pianoanchor.engine.lesson

import pianoanchor.engine.Tunables

// The pedal kinds keep `pedalDown` (with hysteresis), the `notes` of the chord's cluster and `startMs`, its first note.

/**
 * A failed pedal attempt: back to the start with the pedal as it is, one more wrong ([note] is the stray key). [atStart]
 * says the first chord or pedal came too late.
 */
internal fun TryState.failed(
    why: TryTiming?,
    note: Int? = null,
    atStart: Boolean = false,
): TryState =
    copy(
        done = 0,
        notes = emptySet(),
        held = emptySet(),
        lastWrong = note,
        wrongCount = wrongCount + 1,
        timing = why,
        lateStart = atStart,
    )

/** The cluster with [note] added at [atMs]; the first note starts the window. A reset just before it stays shown. */
internal fun TryState.withClusterNote(
    note: Int,
    atMs: Long,
): TryState =
    copy(
        notes = notes + note,
        held = held + note,
        startMs = if (notes.isEmpty()) atMs else startMs,
    )

/** The run with the last reset forgotten: a press that is not a reset shows no hint. */
internal fun TryState.cleared(): TryState = copy(lastWrong = null, timing = null, lateStart = false)

/** True once the cluster of the first chord has run past `CHORD_CLUSTER_MS` without the chord and pedal meeting. */
internal fun TryState.clusterLapsed(atMs: Long): Boolean =
    done == 0 && notes.isNotEmpty() && atMs - startMs > Tunables.CHORD_CLUSTER_MS

// Pedal change: `done` chords are done, `notes` is the cluster of chord `done`, `startMs` its first note.

/** The pedal change run after [note] went down at [atMs]. */
internal fun TryState.pressChange(
    chords: List<Set<Int>>,
    note: Int,
    atMs: Long,
): TryState {
    val chord = chords[done]
    if (note !in chord) return if (changePending(chord)) failed(TryTiming.LATE) else failed(null, note)
    val live = afterWindow(chord, atMs)
    val first = chords[live.done]
    return when {
        note !in first -> {
            live
        }

        else -> {
            val cluster = live.withClusterNote(note, atMs)
            // The first chord is done at once when the pedal is down; later ones wait for their pedal change.
            if (cluster.done == 0 && cluster.pedalDown && cluster.notes.size == first.size) {
                cluster.copy(done = 1, notes = emptySet())
            } else {
                cluster
            }
        }
    }
}

/** The pedal change run after the pedal went [down] (else up) at [atMs]. */
internal fun TryState.pedalChange(
    chords: List<Set<Int>>,
    down: Boolean,
    atMs: Long,
): TryState {
    val live = afterWindow(chords[done], atMs)
    return when {
        down -> live.copy(pedalDown = true).pedalPressed(chords)
        live.done >= 1 && live.notes.isEmpty() -> live.failed(TryTiming.EARLY).copy(pedalDown = false)
        else -> live.copy(pedalDown = false)
    }
}

/** The pedal came down: it meets the first chord's cluster, or it is the change that finishes a later chord. */
private fun TryState.pedalPressed(chords: List<Set<Int>>): TryState =
    when {
        notes.size != chords[done].size -> this

        // Only a pedal that came up since the chord's first note can come down again here.
        done == 0 -> copy(done = 1, notes = emptySet())

        else -> copy(done = done + 1, notes = emptySet(), finished = done + 1 == chords.size)
    }

/** The cluster of a later chord is complete, so the pedal change is what is still to come. */
private fun TryState.changePending(chord: Set<Int>): Boolean = done >= 1 && notes.size == chord.size

/** The run with the cluster of [chord] failed if its window has closed at [atMs] (a missed change is LATE). */
private fun TryState.afterWindow(
    chord: Set<Int>,
    atMs: Long,
): TryState {
    val pending = changePending(chord)
    val window = if (pending) Tunables.TRY_PEDAL_CHANGE_MAX_MS else Tunables.CHORD_CLUSTER_MS.toLong()
    return when {
        notes.isEmpty() || atMs - startMs <= window -> cleared()
        pending -> failed(TryTiming.LATE)
        else -> failed(TryTiming.LATE, atStart = done == 0)
    }
}
