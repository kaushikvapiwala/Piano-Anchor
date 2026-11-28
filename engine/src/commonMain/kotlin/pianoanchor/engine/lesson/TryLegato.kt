package pianoanchor.engine.lesson

import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables

// Legato (`docs/algorithms/try-checks.md`): `done` notes are in a clean chain; `holding` says the last of them is
// still down, `prevDown` that the one before it is (a join by overlap), `startMs` is when the last went down and
// `upMs` when it came up.

/** The legato run after [note] went down at [atMs]. */
internal fun TryState.pressLegato(
    legato: TryCheck.Legato,
    note: Int,
    atMs: Long,
): TryState =
    when {
        note != legato.notes[done] -> {
            wrong(note)
        }

        done == 0 -> {
            copy(done = 1, holding = true, startMs = atMs, lastWrong = null, timing = null)
        }

        prevDown -> {
            brokenLegato(legato, note, atMs, TryTiming.OVERLAP)
        }

        !holding && atMs - upMs > Tunables.TRY_LEGATO_MAX_GAP_MS -> {
            brokenLegato(legato, note, atMs, TryTiming.GAP)
        }

        else -> {
            val next = done + 1
            copy(
                done = next,
                prevDown = holding,
                holding = true,
                startMs = atMs,
                lastWrong = null,
                timing = null,
                finished = next == legato.notes.size,
            )
        }
    }

/** The legato run after [note] came up at [atMs]: it ends the last note's hold or an overlap. */
internal fun TryState.releaseLegato(
    legato: TryCheck.Legato,
    note: Int,
    atMs: Long,
): TryState =
    when {
        holding && note == legato.notes[done - 1] -> {
            copy(holding = false, upMs = atMs)
        }

        prevDown && note == legato.notes[done - 2] -> {
            if (atMs - startMs > Tunables.TRY_LEGATO_MAX_OVERLAP_MS) {
                brokenLegato(legato, null, atMs, TryTiming.OVERLAP)
            } else {
                copy(prevDown = false)
            }
        }

        else -> {
            this
        }
    }

/** A failed legato attempt: back to the start, and a press of the first note ([note]) is already note 1. */
private fun TryState.brokenLegato(
    legato: TryCheck.Legato,
    note: Int?,
    atMs: Long,
    why: TryTiming,
): TryState {
    val restart = note == legato.notes.first()
    return copy(
        done = if (restart) 1 else 0,
        holding = restart,
        prevDown = false,
        startMs = atMs,
        lastWrong = null,
        wrongCount = wrongCount + 1,
        timing = why,
    )
}
