package pianoanchor.engine.lesson

import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables
import kotlin.math.abs
import kotlin.math.roundToLong

private const val NOTES_PER_OCTAVE = 12
private const val MS_PER_MINUTE = 60_000.0
private const val BEATS_PER_BAR = 4

/**
 * Progress of one Try: [done] of [total] parts, the [lastWrong] key pressed against [expected] (for the gentle hint),
 * [finished]. [expected] is the next key to play, or null where no single key is next. [wrongCount] counts wrong
 * keys and failed attempts.
 */
public data class TryProgress(
    val done: Int,
    val total: Int,
    val expected: Int?,
    val lastWrong: Int?,
    val finished: Boolean,
    val wrongCount: Int,
)

private data class TryState(
    val done: Int = 0,
    val notes: Set<Int> = emptySet(),
    val velocities: List<Int> = emptyList(),
    val startMs: Long = 0,
    val holding: Boolean = false,
    val wrongCount: Int = 0,
    val lastWrong: Int? = null,
    val finished: Boolean = false,
) {
    /** This state after the wrong key [note]. */
    fun wrong(note: Int): TryState = copy(wrongCount = wrongCount + 1, lastWrong = note)
}

/**
 * Evaluates [check] from note events (`docs/algorithms/try-checks.md`); pure and immutable: each call returns the
 * next run. Not for [TryCheck.Passage], which the practice screen checks.
 */
public class TryRun private constructor(
    public val check: TryCheck,
    private val state: TryState,
) {
    public constructor(check: TryCheck) : this(check, TryState())

    /** Where the run stands now. */
    public val progress: TryProgress =
        TryProgress(
            done = if (check is TryCheck.FindAll || check is TryCheck.Chord) state.notes.size else state.done,
            total = total(),
            expected = if (state.finished) null else expected(),
            lastWrong = state.lastWrong,
            finished = state.finished,
            wrongCount = state.wrongCount,
        )

    /** The run after [note] went down at [atMs] with [velocity] (1-127). */
    public fun onNoteOn(
        note: Int,
        velocity: Int,
        atMs: Long,
    ): TryRun = if (state.finished) this else TryRun(check, press(note, velocity, atMs))

    /** The run after [note] came up at [atMs]; only a hold cares. */
    public fun onNoteOff(
        note: Int,
        atMs: Long,
    ): TryRun {
        // Only a press of the held note sets `holding`, and finishing clears it.
        if (check !is TryCheck.Hold || !state.holding || note != check.note) return this
        val needed = (check.clicks * MS_PER_MINUTE / check.bpm * (1 - Tunables.TRY_HOLD_TOLERANCE)).roundToLong()
        val next =
            if (atMs - state.startMs >= needed) {
                state.copy(done = 1, holding = false, lastWrong = null, finished = true)
            } else {
                state.copy(holding = false, lastWrong = null, wrongCount = state.wrongCount + 1)
            }
        return TryRun(check, next)
    }

    private fun total(): Int =
        when (check) {
            is TryCheck.AnyKey, is TryCheck.PlayKey, is TryCheck.Hold -> 1

            is TryCheck.FindAll -> targets(check).size

            is TryCheck.Sequence -> check.notes.size

            is TryCheck.Repeat -> check.times

            is TryCheck.SoftThenLoud -> 2

            is TryCheck.StressBeatOne -> check.bars * BEATS_PER_BAR

            is TryCheck.Chord -> check.notes.size

            is TryCheck.Passage -> throw IllegalArgumentException(
                "a passage check is run by the practice screen, not TryRun",
            )
        }

    private fun expected(): Int? =
        when (check) {
            is TryCheck.PlayKey -> check.note

            is TryCheck.FindAll -> state.nearestUnfound(check)

            is TryCheck.Sequence -> check.notes[state.done]

            is TryCheck.Repeat -> check.note

            is TryCheck.Hold -> check.note

            is TryCheck.SoftThenLoud -> check.note

            is TryCheck.StressBeatOne -> check.note

            // No single next key: any key, or a chord (a passage is rejected by total() first).
            else -> null
        }

    /** A press of the one right [expected] key, or the wrong-key state otherwise. */
    private fun single(
        note: Int,
        expected: Int,
        right: () -> TryState,
    ): TryState = if (note == expected) right() else state.wrong(note)

    private fun press(
        note: Int,
        velocity: Int,
        atMs: Long,
    ): TryState =
        when (check) {
            is TryCheck.PlayKey -> {
                single(note, check.note) { state.copy(done = 1, lastWrong = null, finished = true) }
            }

            is TryCheck.FindAll -> {
                state.pressFindAll(check, note)
            }

            is TryCheck.Sequence -> {
                single(note, check.notes[state.done]) {
                    val done = state.done + 1
                    state.copy(done = done, lastWrong = null, finished = done == check.notes.size)
                }
            }

            is TryCheck.Repeat -> {
                single(note, check.note) {
                    val done = state.done + 1
                    state.copy(done = done, lastWrong = null, finished = done == check.times)
                }
            }

            is TryCheck.Hold -> {
                single(note, check.note) { state.copy(holding = true, startMs = atMs, lastWrong = null) }
            }

            is TryCheck.SoftThenLoud -> {
                single(note, check.note) { state.pressSoftLoud(velocity) }
            }

            is TryCheck.StressBeatOne -> {
                single(note, check.note) { state.pressStress(total(), velocity) }
            }

            is TryCheck.Chord -> {
                state.pressChord(check, note, atMs)
            }

            // Any key (a passage is rejected by total() first).
            else -> {
                state.copy(done = 1, finished = true)
            }
        }
}

private fun targets(find: TryCheck.FindAll): List<Int> = find.range.filter { it % NOTES_PER_OCTAVE == find.pitchClass }

private fun TryState.nearestUnfound(find: TryCheck.FindAll): Int {
    val unfound = targets(find).filter { it !in notes }
    val from = lastWrong ?: return unfound.first()
    return unfound.minWith(compareBy({ abs(it - from) }, { it }))
}

private fun TryState.pressFindAll(
    find: TryCheck.FindAll,
    note: Int,
): TryState {
    val targets = targets(find)
    return when {
        note !in targets -> {
            wrong(note)
        }

        else -> {
            val found = notes + note
            copy(notes = found, lastWrong = null, finished = found.size == targets.size)
        }
    }
}

private fun TryState.pressSoftLoud(velocity: Int): TryState {
    val soft = done == 0
    val fits = if (soft) velocity <= Tunables.TRY_SOFT_MAX_VELOCITY else velocity >= Tunables.TRY_LOUD_MIN_VELOCITY
    return when {
        !fits -> copy(wrongCount = wrongCount + 1, lastWrong = null)
        else -> copy(done = done + 1, lastWrong = null, finished = !soft)
    }
}

private fun TryState.pressStress(
    total: Int,
    velocity: Int,
): TryState {
    val presses = velocities + velocity
    if (presses.size < total) return copy(velocities = presses, done = presses.size, lastWrong = null)
    val (accented, rest) = presses.withIndex().partition { it.index % BEATS_PER_BAR == 0 }
    val delta = accented.map { it.value }.average() - rest.map { it.value }.average()
    return if (delta >= Tunables.TRY_STRESS_MIN_DELTA) {
        copy(velocities = presses, done = presses.size, lastWrong = null, finished = true)
    } else {
        copy(velocities = emptyList(), done = 0, lastWrong = null, wrongCount = wrongCount + 1)
    }
}

private fun TryState.pressChord(
    chord: TryCheck.Chord,
    note: Int,
    atMs: Long,
): TryState {
    if (note !in chord.notes) return wrong(note).copy(notes = emptySet())
    val expired = notes.isNotEmpty() && atMs - startMs > Tunables.CHORD_CLUSTER_MS
    val base = if (expired) copy(notes = emptySet(), wrongCount = wrongCount + 1) else this
    val start = if (base.notes.isEmpty()) atMs else base.startMs
    val cluster = base.notes + note
    return base.copy(
        notes = cluster,
        startMs = start,
        lastWrong = null,
        finished = cluster.size == chord.notes.size,
    )
}
