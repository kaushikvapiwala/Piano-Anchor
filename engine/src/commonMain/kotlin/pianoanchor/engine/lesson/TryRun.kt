package pianoanchor.engine.lesson

import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables
import kotlin.math.abs
import kotlin.math.roundToLong

private const val NOTES_PER_OCTAVE = 12
private const val MS_PER_MINUTE = 60_000.0
private const val BEATS_PER_BAR = 4

/** An eighth note lasts `30000 / bpm` ms. */
private const val MS_PER_EIGHTH_AT_ONE_BPM = 30_000.0

/**
 * Why an attempt was reset: a rhythm tap came too [EARLY] or too [LATE] against the pattern; a legato note came after
 * a [GAP] or was held into the next too long ([OVERLAP]); a pedal was lifted too [EARLY] or changed too [LATE].
 */
public enum class TryTiming { EARLY, LATE, GAP, OVERLAP }

/**
 * Progress of one Try: [done] of [total] parts, the [lastWrong] key pressed against [expected] (for the gentle hint),
 * [finished]. [expected] is the next key to play, or null where no single key is next. [wrongCount] counts wrong
 * keys and failed attempts. [timing] is why the last rhythm reset happened (for the hint), null when the last press
 * was not a reset. [lateStart] is true when the reset was a chord or pedal that came too late at the start of a
 * pedal attempt (before the first chord was met), for its own hint.
 */
public data class TryProgress(
    val done: Int,
    val total: Int,
    val expected: Int?,
    val lastWrong: Int?,
    val finished: Boolean,
    val wrongCount: Int,
    val timing: TryTiming? = null,
    val lateStart: Boolean = false,
)

internal data class TryState(
    val done: Int = 0,
    val notes: Set<Int> = emptySet(),
    val held: Set<Int> = emptySet(),
    val prevDown: Boolean = false,
    val upMs: Long = 0,
    val pedalDown: Boolean = false,
    val velocities: List<Int> = emptyList(),
    val startMs: Long = 0,
    val firstMs: Long = 0,
    val holding: Boolean = false,
    val wrongCount: Int = 0,
    val lastWrong: Int? = null,
    val finished: Boolean = false,
    val timing: TryTiming? = null,
    val lateStart: Boolean = false,
) {
    /** This state after the wrong key [note]. */
    fun wrong(note: Int): TryState = copy(wrongCount = wrongCount + 1, lastWrong = note, timing = null)
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

    /** A run that starts with the sustain pedal already [pedalDown] (a rep that begins while the pedal is held). */
    public constructor(check: TryCheck, pedalDown: Boolean) : this(check, TryState(pedalDown = pedalDown))

    /** Where the run stands now. */
    public val progress: TryProgress =
        TryProgress(
            done = if (check is TryCheck.FindAll || check is TryCheck.Chord) state.notes.size else state.done,
            total = total(),
            expected = if (state.finished) null else expected(),
            lastWrong = state.lastWrong,
            finished = state.finished,
            wrongCount = state.wrongCount,
            timing = state.timing,
            lateStart = state.lateStart,
        )

    /** The run after [note] went down at [atMs] with [velocity] (1-127). */
    public fun onNoteOn(
        note: Int,
        velocity: Int,
        atMs: Long,
    ): TryRun = if (state.finished) this else TryRun(check, press(note, velocity, atMs))

    /** The run after [note] came up at [atMs]; only a hold, a legato and a pedal hold care. */
    public fun onNoteOff(
        note: Int,
        atMs: Long,
    ): TryRun =
        when {
            state.finished -> this
            check is TryCheck.Hold -> release(check, note, atMs)
            check is TryCheck.Legato -> TryRun(check, state.releaseLegato(check, note, atMs))
            check is TryCheck.PedalHold -> TryRun(check, state.releaseHeld(note, atMs))
            else -> this
        }

    /** The run after the sustain pedal (CC 64) moved to [value] (0-127) at [atMs]; other checks ignore it. */
    public fun onPedal(
        value: Int,
        atMs: Long,
    ): TryRun {
        val down = !state.pedalDown
        val crossed = if (down) value >= Tunables.PEDAL_DOWN_VALUE else value < Tunables.PEDAL_UP_VALUE
        return when {
            state.finished || !crossed -> this
            check is TryCheck.PedalHold -> TryRun(check, state.pedalHold(check.chord, down, atMs))
            check is TryCheck.PedalChange -> TryRun(check, state.pedalChange(check.chords, down, atMs))
            else -> this
        }
    }

    private fun release(
        check: TryCheck.Hold,
        note: Int,
        atMs: Long,
    ): TryRun {
        // Only a press of the held note sets `holding`, and finishing clears it.
        if (!state.holding || note != check.note) return this
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

            is TryCheck.Legato -> check.notes.size

            is TryCheck.PedalHold -> PEDAL_HOLD_PARTS

            is TryCheck.PedalChange -> check.chords.size

            is TryCheck.Repeat -> check.times

            is TryCheck.SoftThenLoud -> 2

            is TryCheck.StressBeatOne -> check.bars * BEATS_PER_BAR

            is TryCheck.Rhythm -> check.lengths.size

            is TryCheck.EvenSequence -> check.notes.size

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

            is TryCheck.Legato -> check.notes.getOrNull(state.done)

            is TryCheck.Repeat -> check.note

            is TryCheck.Hold -> check.note

            is TryCheck.SoftThenLoud -> check.note

            is TryCheck.StressBeatOne -> check.note

            is TryCheck.Rhythm -> check.note

            is TryCheck.EvenSequence -> check.notes[state.done]

            // No single next key: any key, or a chord (a passage is rejected by total() first).
            else -> null
        }

    /** A press of the one right [expected] key, or the wrong-key state otherwise. */
    private fun single(
        note: Int,
        expected: Int,
        right: () -> TryState,
    ): TryState = if (note == expected) right() else state.wrong(note)

    /** A press against a Rhythm or an even sequence: the right note, judged in time by the shared helper. */
    private fun pressTimedNote(
        check: TryCheck,
        note: Int,
        atMs: Long,
    ): TryState =
        when (check) {
            is TryCheck.EvenSequence -> {
                single(note, check.notes[state.done]) { state.pressEven(check, note, atMs) }
            }

            else -> {
                val rhythm = check as TryCheck.Rhythm
                single(note, rhythm.note) { state.pressRhythm(rhythm.lengths, rhythm.bpm, atMs) }
            }
        }

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

            is TryCheck.Legato -> {
                state.pressLegato(check, note, atMs)
            }

            is TryCheck.PedalHold -> {
                state.pressHeld(check.chord, note, atMs)
            }

            is TryCheck.PedalChange -> {
                state.pressChange(check.chords, note, atMs)
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

            is TryCheck.Rhythm, is TryCheck.EvenSequence -> {
                pressTimedNote(check, note, atMs)
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
        // The right letter outside the range isn't a mistake (the prompt names no range): it counts nothing.
        note !in find.range && note % NOTES_PER_OCTAVE == find.pitchClass -> {
            this
        }

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

/**
 * A press of a rhythm's note at [atMs], the pattern being [lengths] (ticks) at [bpm]: the first starts the pattern; a
 * later one is judged by [pressTimed] against the written onsets. Rhythm and even sequences both use it.
 */
private fun TryState.pressRhythm(
    lengths: List<Int>,
    bpm: Int,
    atMs: Long,
): TryState {
    val msPerTick = MS_PER_MINUTE / (bpm * TICKS_PER_QUARTER)
    val offsets = lengths.dropLast(1).runningFold(0.0) { sum, length -> sum + length * msPerTick }
    val tolerance = Tunables.TRY_RHYTHM_TOLERANCE * MS_PER_EIGHTH_AT_ONE_BPM / bpm
    return pressTimed(offsets, tolerance, atMs)
}

/**
 * The right note [note] of an even sequence at [atMs]: timed like a rhythm of quarters. A timing miss restarts the
 * attempt, and the press counts as note 1 only if it is the sequence's first note.
 */
private fun TryState.pressEven(
    even: TryCheck.EvenSequence,
    note: Int,
    atMs: Long,
): TryState {
    val next = pressRhythm(List(even.notes.size) { TICKS_PER_QUARTER }, even.bpm, atMs)
    return if (next.wrongCount > wrongCount && note != even.notes.first()) next.copy(done = 0) else next
}

/**
 * A timed tap at [atMs], where press k should fall `expectedOffsetsMs[k-1]` ms after press 1 (the list starts at 0 and
 * has one entry per press). Press k >= 2 is judged twice, each within [toleranceMs]: its gap from the press before and
 * its drift from where it should fall counting from the first press, so a steady tempo off the pattern cannot slip
 * through. A miss counts a wrong attempt, sets `timing` (the gap's sign if the gap failed, else the drift's; negative
 * is early) and restarts with this press as press 1. Press 1 starts the attempt; the last press finishes.
 */
internal fun TryState.pressTimed(
    expectedOffsetsMs: List<Double>,
    toleranceMs: Double,
    atMs: Long,
): TryState {
    val index = done
    val gapError = if (index == 0) 0.0 else atMs - startMs - (expectedOffsetsMs[index] - expectedOffsetsMs[index - 1])
    val driftError = if (index == 0) 0.0 else atMs - firstMs - expectedOffsetsMs[index]
    val gapFailed = abs(gapError) > toleranceMs
    return if (gapFailed || abs(driftError) > toleranceMs) {
        val error = if (gapFailed) gapError else driftError
        val timing = if (error < 0) TryTiming.EARLY else TryTiming.LATE
        copy(
            done = 1,
            startMs = atMs,
            firstMs = atMs,
            lastWrong = null,
            wrongCount = wrongCount + 1,
            timing = timing,
        )
    } else {
        val next = index + 1
        val first = if (index == 0) atMs else firstMs
        copy(
            done = next,
            startMs = atMs,
            firstMs = first,
            lastWrong = null,
            timing = null,
            finished = next == expectedOffsetsMs.size,
        )
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
