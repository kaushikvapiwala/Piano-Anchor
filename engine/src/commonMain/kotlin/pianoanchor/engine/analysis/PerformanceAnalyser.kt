package pianoanchor.engine.analysis

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.MAX_TEMPO_BPM
import pianoanchor.core.model.MIN_TEMPO_BPM
import pianoanchor.core.model.PLAYED_NOTE_ORDER
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.engine.Tunables
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

private const val NANOS_PER_MILLI = 1_000_000L
private const val NANOS_PER_MINUTE = 60_000_000_000.0
private val MISTOUCH_SEMITONES = 1..2

/** Nanoseconds per tick at [bpm] quarter notes per minute. */
private fun nanosPerTick(bpm: Number): Double = NANOS_PER_MINUTE / bpm.toDouble() / TICKS_PER_QUARTER

/**
 * The early/late band at a beat (quarter note) of [beatNanos], in nanoseconds: max([Tunables.TIMING_TOLERANCE_MS],
 * [Tunables.TIMING_TOLERANCE_BEAT_FRACTION] × beat). An onset further than this from where the tempo puts it is
 * early or late.
 */
public fun timingToleranceNanos(beatNanos: Double): Double =
    max(
        (Tunables.TIMING_TOLERANCE_MS * NANOS_PER_MILLI).toDouble(),
        Tunables.TIMING_TOLERANCE_BEAT_FRACTION * beatNanos,
    )

/** What early and late are measured against (`docs/algorithms/performance-analysis.md`). */
public sealed interface TempoReference {
    /**
     * Early/late against this tempo (a tempo-ladder rung), [bpm] in [MIN_TEMPO_BPM]..[MAX_TEMPO_BPM]. [startNanos] is
     * when tick 0 should sound (a tempo run's downbeat after the count-in); null anchors at the first matched onset.
     */
    public data class Target(
        val bpm: Int,
        val startNanos: Long? = null,
    ) : TempoReference {
        init {
            require(
                bpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM,
            ) { "target tempo must be in $MIN_TEMPO_BPM..$MAX_TEMPO_BPM BPM, was $bpm" }
        }
    }

    /** Early/late against the tempo fitted to the take. */
    public data object Fitted : TempoReference

    /** Wait mode: pitch kinds and hesitation only. */
    public data object Untimed : TempoReference
}

/**
 * One aligned score note. [played] is null when missed. [onsetDeviationNanos] (played minus expected, null when
 * untimed or missed) and [gapBeforeNanos] (from the previous step's matched onset, null for the first step or when
 * either is missed) feed the Results tiles.
 */
public data class NoteMatch(
    val at: ScoreNoteRef,
    val played: PlayedNote?,
    val onsetDeviationNanos: Long?,
    val gapBeforeNanos: Long?,
)

/**
 * A take's analysis: [findings] in score order (step, then pitch), then time; [matches], one per score note in score
 * order; [fittedTempoBpm], the take's least-squares tempo, null with fewer than two exact-pitch matches at distinct
 * onsets.
 */
public data class PerformanceAnalysis(
    val findings: List<Finding>,
    val matches: List<NoteMatch>,
    val fittedTempoBpm: Double?,
)

/** The offline performance analyser (`docs/algorithms/performance-analysis.md`). */
public object PerformanceAnalyser {
    /** Aligns [notes] (any order) to [passage] and classifies every error, timed against [tempo]. */
    public fun analyse(
        passage: Passage,
        notes: List<PlayedNote>,
        tempo: TempoReference,
    ): PerformanceAnalysis {
        val played = notes.sortedWith(PLAYED_NOTE_ORDER)
        val target = tempo as? TempoReference.Target
        val alignmentNanosPerTick = nanosPerTick(target?.bpm ?: passage.tempoBpm)
        val first = align(passage, played, alignmentNanosPerTick, target?.startNanos)
        val mistouches = mistouches(passage, played, first, alignmentNanosPerTick)
        val kept = played.filterNot { note -> mistouches.any { it.second === note } }
        val alignment = align(passage, kept, alignmentNanosPerTick, target?.startNanos)
        return Classification(passage, tempo, alignment, alignmentNanosPerTick).analysis(mistouches)
    }

    /**
     * Mistouches (score note, brushed key) against a first alignment, in [played] order: a played note within
     * [Tunables.MISTOUCH_WINDOW_MS] of a louder exact match 1–2 semitones away points at that match's score note (the
     * nearest in time when several qualify). Not when the note is itself an exact match on time for its own step,
     * measured from the louder note at [nanosPerTick]: a chord with a second in it, or a fast run.
     */
    private fun mistouches(
        passage: Passage,
        played: List<PlayedNote>,
        alignment: Alignment,
        nanosPerTick: Double,
    ): List<Pair<ScoreNoteRef, PlayedNote>> {
        val ticksOf = { ref: ScoreNoteRef -> passage.steps[ref.stepIndex].onsetTicks }
        val exact = alignment.matched.filter { (ref, note) -> note.pitch == ref.pitch }
        val exactTicksOf = exact.entries.associate { (ref, note) -> note to ticksOf(ref) }
        val window = Tunables.MISTOUCH_WINDOW_MS * NANOS_PER_MILLI
        val tolerance = timingToleranceNanos(nanosPerTick * TICKS_PER_QUARTER)
        return played.mapNotNull { touch ->
            val ownTicks = exactTicksOf[touch]
            exact.entries
                .filter { (ref, louder) ->
                    val expected = ownTicks?.let { louder.downNanos + (it - ticksOf(ref)) * nanosPerTick }
                    val onTimeMatch = expected != null && abs(touch.downNanos - expected) <= tolerance
                    !onTimeMatch &&
                        louder.velocity > touch.velocity &&
                        abs(louder.pitch.number - touch.pitch.number) in MISTOUCH_SEMITONES &&
                        abs(louder.downNanos - touch.downNanos) <= window
                }.minByOrNull { abs(it.value.downNanos - touch.downNanos) }
                ?.let { it.key to touch }
        }
    }
}

/** Expected onsets at [nanosPerTick], pinned so that tick [anchorTicks] sounds at [anchorNanos]. */
private data class Clock(
    val anchorTicks: Int,
    val anchorNanos: Double,
    val nanosPerTick: Double,
) {
    /** When a step at [ticks] should sound, rounded to whole nanoseconds. */
    fun expectedNanos(ticks: Int): Long = (anchorNanos + (ticks - anchorTicks) * nanosPerTick).roundToLong()
}

/** Classifies an [alignment] of a take of [passage] against [tempo]. */
private class Classification(
    private val passage: Passage,
    private val tempo: TempoReference,
    private val alignment: Alignment,
    private val alignmentNanosPerTick: Double,
) {
    private val steps = passage.steps
    private val refsByStep =
        steps.mapIndexed { s, step -> step.pitches.sortedBy { it.number }.map { ScoreNoteRef(s, it) } }
    private val line =
        TempoFit.fit(
            alignment.matched
                .filter { (ref, note) -> note.pitch == ref.pitch }
                .map { (ref, note) -> steps[ref.stepIndex].onsetTicks to note.downNanos },
        )

    /** The earliest matched onset of each step, null when none of its notes was matched. */
    private val stepOnsets =
        refsByStep.map { refs -> refs.mapNotNull { alignment.matched[it]?.downNanos }.minOrNull() }

    /** Tempo of the expected gap a hesitation is measured against; null when there is none (no fitted tempo). */
    private val hesitationNanosPerTick =
        if (tempo is TempoReference.Target) nanosPerTick(tempo.bpm) else line?.nanosPerTick

    /**
     * Where early/late and note length are measured from; null while they can't be judged (untimed, no fitted tempo,
     * or a target tempo waiting for its first matched onset). Moves to a step after a hesitation.
     */
    private var clock: Clock? =
        when (tempo) {
            is TempoReference.Target -> tempo.startNanos?.let { Clock(0, it.toDouble(), nanosPerTick(tempo.bpm)) }
            TempoReference.Fitted -> line?.let { Clock(0, it.interceptNanos, it.nanosPerTick) }
            TempoReference.Untimed -> null
        }

    /** The last step with a matched note so far, and its earliest matched onset. */
    private var lastMatched: Pair<Int, Long>? = null

    /** Steps with a matched note and their earliest matched onsets, in score order. */
    private val matchedOnsets = stepOnsets.withIndex().mapNotNull { (s, onset) -> onset?.let { s to it } }

    /** Findings with the time they sort by within a score note: the played onset, or first for a missing note. */
    private val findings = ArrayList<Pair<Finding, Long>>()
    private val matches = ArrayList<NoteMatch>()

    fun analysis(mistouches: List<Pair<ScoreNoteRef, PlayedNote>>): PerformanceAnalysis {
        steps.indices.forEach(::classifyStep)
        mistouches.forEach { (ref, touch) -> report(ErrorKind.MISTOUCH, ref, touch) }
        alignment.extras.forEach { report(ErrorKind.EXTRA_NOTE, refsByStep[nearestStep(it)].first(), it) }
        val order =
            compareBy<Pair<Finding, Long>>({ it.first.at.stepIndex }, { it.first.at.pitch.number }, { it.second })
                .thenBy { it.first.kind }
        val bpm = line?.let { NANOS_PER_MINUTE / (it.nanosPerTick * TICKS_PER_QUARTER) }
        return PerformanceAnalysis(findings.sortedWith(order).map { it.first }, matches, bpm)
    }

    private fun classifyStep(s: Int) {
        val onset = stepOnsets[s]
        val previousOnset = stepOnsets.getOrNull(s - 1)
        val previous = lastMatched
        if (onset != null && previous != null) checkHesitation(s, onset, previous)
        if (onset != null && clock == null && tempo is TempoReference.Target) {
            clock = Clock(steps[s].onsetTicks, onset.toDouble(), nanosPerTick(tempo.bpm))
        }
        if (onset != null) lastMatched = s to onset
        val clock = clock
        refsByStep[s].forEach { ref ->
            val note = alignment.matched[ref]
            val deviation = note?.let { n -> clock?.let { n.downNanos - it.expectedNanos(steps[s].onsetTicks) } }
            matches += NoteMatch(ref, note, deviation, note?.let { n -> previousOnset?.let { n.downNanos - it } })
            if (note == null) {
                val kind = if (onset == null) ErrorKind.MISSED_NOTE else ErrorKind.INCOMPLETE_CHORD
                findings += Finding(kind, ref, null) to Long.MIN_VALUE
            } else {
                classifyNote(ref, note, clock)
            }
        }
    }

    /** A matched note: a pitch finding, or timing and length for an exact pitch when there is a [clock]. */
    private fun classifyNote(
        ref: ScoreNoteRef,
        note: PlayedNote,
        clock: Clock?,
    ) {
        when {
            note.pitch == ref.pitch -> if (clock != null) checkTimingAndLength(ref, note, clock)
            isOctave(note.pitch, ref.pitch) -> report(ErrorKind.OCTAVE, ref, note)
            else -> report(ErrorKind.WRONG_NOTE, ref, note)
        }
    }

    private fun report(
        kind: ErrorKind,
        ref: ScoreNoteRef,
        note: PlayedNote,
    ) {
        findings += Finding(kind, ref, note) to note.downNanos
    }

    /** A hesitation at step [s] (matched from [onset]) after the [previous] matched step (index, onset). */
    private fun checkHesitation(
        s: Int,
        onset: Long,
        previous: Pair<Int, Long>,
    ) {
        val nanosPerTick = hesitationNanosPerTick ?: return
        val (previousStep, previousOnset) = previous
        val gap = onset - previousOnset
        val expectedGap = (steps[s].onsetTicks - steps[previousStep].onsetTicks) * nanosPerTick
        val minGap = Tunables.HESITATION_MIN_MS * NANOS_PER_MILLI
        if (gap >= Tunables.HESITATION_GAP_RATIO * expectedGap && gap >= minGap) {
            val ref = refsByStep[s].first { it in alignment.matched }
            report(ErrorKind.HESITATION, ref, alignment.matched.getValue(ref))
            clock = clock?.copy(anchorTicks = steps[s].onsetTicks, anchorNanos = onset.toDouble())
        }
    }

    private fun checkTimingAndLength(
        ref: ScoreNoteRef,
        note: PlayedNote,
        clock: Clock,
    ) {
        val step = steps[ref.stepIndex]
        val deviation = note.downNanos - clock.expectedNanos(step.onsetTicks)
        val tolerance = timingToleranceNanos(clock.nanosPerTick * TICKS_PER_QUARTER)
        if (abs(deviation) > tolerance) {
            report(if (deviation < 0) ErrorKind.EARLY else ErrorKind.LATE, ref, note)
        }
        val writtenTicks = step.notes.first { it.pitch == ref.pitch }.lengthTicks
        val written = writtenTicks * clock.nanosPerTick
        val held = note.upNanos - note.downNanos
        if (held < Tunables.NOTE_SHORT_RATIO * written &&
            !isLegatoLift(ref.stepIndex, step.onsetTicks + writtenTicks, note, tolerance)
        ) {
            report(ErrorKind.TOO_SHORT, ref, note)
        }
        if (held > Tunables.NOTE_LONG_RATIO * written) report(ErrorKind.TOO_LONG, ref, note)
    }

    /**
     * True when [note] of step [s], written to end by [writtenEndTicks], came up within [tolerance] of the
     * next step's onset and isn't written to sound past it: legato players lift just before the next note.
     */
    private fun isLegatoLift(
        s: Int,
        writtenEndTicks: Int,
        note: PlayedNote,
        tolerance: Double,
    ): Boolean {
        val nextOnset = stepOnsets.getOrNull(s + 1) ?: return false
        return writtenEndTicks <= steps[s + 1].onsetTicks && nextOnset - note.upNanos <= tolerance
    }

    /**
     * The step nearest [note] in time. A matched step is at its earliest matched onset; an unmatched one is placed from
     * the nearest matched step before it (else after it) at the alignment tempo. Ties go to the earlier step. Called
     * only for extras, which share a run with a matched step, so some step is matched.
     */
    private fun nearestStep(note: PlayedNote): Int {
        val distances =
            steps.indices.map { s ->
                val (from, onset) = matchedOnsets.lastOrNull { it.first <= s } ?: matchedOnsets[0]
                abs(note.downNanos - (onset + (steps[s].onsetTicks - steps[from].onsetTicks) * alignmentNanosPerTick))
            }
        return distances.indexOf(distances.min())
    }
}
