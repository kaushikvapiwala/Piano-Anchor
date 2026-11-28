package pianoanchor.engine.lesson

import pianoanchor.engine.Tunables

/** The parts of a pedal hold: chord with the pedal, keys up with the pedal, the sound ringing on. */
internal const val PEDAL_HOLD_PARTS: Int = 3

private const val RINGING = 2

// Pedal hold: `done` 1 = chord with the pedal, 2 = keys up, `held` = the chord keys down, `upMs` = the last came up.

/** The pedal hold run after [note] went down at [atMs]. */
internal fun TryState.pressHeld(
    chord: Set<Int>,
    note: Int,
    atMs: Long,
): TryState {
    if (note !in chord) return failed(null, note)
    val live = if (clusterLapsed(atMs)) failed(null) else this
    return if (live.done >= 1) {
        live.copy(held = live.held + note, done = 1)
    } else {
        live.withClusterNote(note, atMs).chordWithPedal(chord.size, atMs)
    }
}

/** The pedal hold run after [note] came up at [atMs]. */
internal fun TryState.releaseHeld(
    note: Int,
    atMs: Long,
): TryState {
    val live = if (clusterLapsed(atMs)) failed(null) else this
    val rest = live.held - note
    return when {
        note !in live.held -> live
        live.done == 1 && rest.isEmpty() -> live.copy(held = rest, done = RINGING, upMs = atMs)
        else -> live.copy(held = rest)
    }
}

/** The pedal hold run after the pedal went [down] (else up) at [atMs]. */
internal fun TryState.pedalHold(
    chord: Set<Int>,
    down: Boolean,
    atMs: Long,
): TryState {
    val live = if (clusterLapsed(atMs)) failed(null) else this
    return when {
        down -> {
            live.copy(pedalDown = true).chordWithPedal(chord.size, atMs)
        }

        live.notes.isEmpty() -> {
            live.copy(pedalDown = false)
        }

        live.done == RINGING && atMs - live.upMs >= Tunables.TRY_PEDAL_RING_MS -> {
            live.copy(pedalDown = false, done = PEDAL_HOLD_PARTS, finished = true)
        }

        else -> {
            live.failed(TryTiming.EARLY).copy(pedalDown = false)
        }
    }
}

/** Part one met: every chord note is down in its cluster and the pedal is down too. */
private fun TryState.chordWithPedal(
    size: Int,
    atMs: Long,
): TryState =
    if (pedalDown && notes.size == size) {
        copy(done = if (held.isEmpty()) RINGING else 1, upMs = atMs)
    } else {
        this
    }
