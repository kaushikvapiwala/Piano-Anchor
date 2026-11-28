package pianoanchor.engine.lesson

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.Tone
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum

private const val NOTES_PER_OCTAVE = 12
private const val BEATS_PER_BAR = 4
private const val MS_PER_MINUTE = 60_000L
private const val PEDAL_DEMO_BPM = 60L
private const val PEDAL_BEAT_MS = MS_PER_MINUTE / PEDAL_DEMO_BPM
private const val PEDAL_CHANGE_BEATS = 2

/** The sustain pedal going [down] (else up) [atMs] after the demo begins. */
public data class PedalCue(
    val atMs: Long,
    val down: Boolean,
)

/**
 * What the lesson's See step plays: [tones] (empty when the check has nothing to hear, so the screen only shows the
 * card), and, for a check with a click, its [clickBpm] and [leadMs]: the click starts at [leadMs] after the demo
 * begins, the first tone sounding on it. [pedal] are the pedal moves of a pedal demo, in time order.
 */
public data class Demo(
    val tones: List<Tone>,
    val clickBpm: Int? = null,
    val leadMs: Long = 0,
    val pedal: List<PedalCue> = emptyList(),
)

/** The demos of the Try checks (`docs/algorithms/try-checks.md`, "See"). Pure. */
public object LessonDemo {
    /** The demo that shows what [check] asks for. */
    public fun of(check: TryCheck): Demo =
        when (check) {
            TryCheck.AnyKey -> {
                Demo(emptyList())
            }

            is TryCheck.PlayKey -> {
                spaced(listOf(check.note))
            }

            is TryCheck.FindAll -> {
                spaced(check.range.filter { it % NOTES_PER_OCTAVE == check.pitchClass })
            }

            is TryCheck.Sequence -> {
                spaced(check.notes)
            }

            is TryCheck.Repeat -> {
                clicked(check.bpm) { beat ->
                    List(check.times) {
                        Tone(
                            note(check.note),
                            it * beat,
                            beat / 2,
                        )
                    }
                }
            }

            is TryCheck.Rhythm -> {
                clicked(check.bpm) { beat -> rhythm(check, beat) }
            }

            is TryCheck.EvenSequence -> {
                clicked(check.bpm) { beat -> check.notes.mapIndexed { i, n -> Tone(note(n), i * beat, beat) } }
            }

            is TryCheck.Hold -> {
                clicked(check.bpm) { beat -> listOf(Tone(note(check.note), 0, check.clicks * beat)) }
            }

            is TryCheck.StressBeatOne -> {
                clicked(check.bpm) { beat -> stress(check, beat) }
            }

            is TryCheck.SoftThenLoud -> {
                val soft =
                    Tone(note(check.note), 0, Tunables.PLAYBACK_NOTE_LENGTH_MS, Tunables.LESSON_DEMO_SOFT_VELOCITY)
                val loud =
                    soft.copy(
                        startMs = Tunables.PLAYBACK_NOTE_GAP_MS,
                        velocity = Tunables.LESSON_DEMO_LOUD_VELOCITY,
                    )
                Demo(listOf(soft, loud))
            }

            is TryCheck.Chord -> {
                Demo(check.notes.sorted().map { Tone(note(it), 0, Tunables.EAR_CHORD_LENGTH_MS) })
            }

            is TryCheck.Passage -> {
                Demo(passageTones(check.passageId))
            }

            is TryCheck.Legato, is TryCheck.PedalHold, is TryCheck.PedalChange -> {
                pedalDemo(check)
            }
        }

    /** The demos of the legato and pedal checks, all at 60 BPM and without a click. */
    private fun pedalDemo(check: TryCheck): Demo =
        when (check) {
            is TryCheck.Legato -> {
                Demo(check.notes.mapIndexed { i, n -> Tone(note(n), i * PEDAL_BEAT_MS, PEDAL_BEAT_MS) })
            }

            is TryCheck.PedalChange -> {
                pedalChange(check)
            }

            else -> {
                val hold = check as TryCheck.PedalHold
                Demo(
                    hold.chord.sorted().map { Tone(note(it), 0, PEDAL_BEAT_MS) },
                    pedal = listOf(PedalCue(0, true), PedalCue(PEDAL_BEAT_MS + Tunables.TRY_PEDAL_RING_MS, false)),
                )
            }
        }

    /** Each chord for two beats; after every chord but the first the pedal comes up, then down again. */
    private fun pedalChange(check: TryCheck.PedalChange): Demo {
        val length = PEDAL_CHANGE_BEATS * PEDAL_BEAT_MS
        val cluster = Tunables.CHORD_CLUSTER_MS.toLong()
        val tones =
            check.chords.withIndex().flatMap { (i, chord) ->
                chord.sorted().map { Tone(note(it), i * length, length) }
            }
        val changes =
            (1 until check.chords.size).flatMap {
                listOf(PedalCue(it * length + cluster, false), PedalCue(it * length + 2 * cluster, true))
            }
        return Demo(tones, pedal = listOf(PedalCue(0, true)) + changes)
    }

    private fun note(number: Int) = MidiNote(number)

    /** [notes] one after another, a playback gap apart. */
    private fun spaced(notes: List<Int>): Demo =
        Demo(
            notes.mapIndexed {
                i,
                n,
                ->
                Tone(note(n), i * Tunables.PLAYBACK_NOTE_GAP_MS, Tunables.PLAYBACK_NOTE_LENGTH_MS)
            },
        )

    /** Tones on the clicks of [bpm]: [build] gets the click interval and times tones from the first click. */
    private fun clicked(
        bpm: Int,
        build: (beatMs: Long) -> List<Tone>,
    ): Demo {
        val beat = MS_PER_MINUTE / bpm
        return Demo(build(beat).map { it.copy(startMs = it.startMs + beat) }, clickBpm = bpm, leadMs = beat)
    }

    /** The pattern's notes, each sounding for half its written length, from the first click. */
    private fun rhythm(
        check: TryCheck.Rhythm,
        beat: Long,
    ): List<Tone> {
        var onset = 0
        return check.lengths.map { length ->
            val start = onset * beat / TICKS_PER_QUARTER
            onset += length
            Tone(note(check.note), start, length * beat / TICKS_PER_QUARTER / 2)
        }
    }

    private fun stress(
        check: TryCheck.StressBeatOne,
        beat: Long,
    ): List<Tone> =
        List(check.bars * BEATS_PER_BAR) {
            val accent = it % BEATS_PER_BAR == 0
            val velocity = if (accent) Tunables.LESSON_DEMO_LOUD_VELOCITY else Tunables.LESSON_DEMO_SOFT_VELOCITY
            Tone(note(check.note), it * beat, beat / 2, velocity)
        }

    private fun passageTones(id: String): List<Tone> {
        val passage = Curriculum.passage(id) ?: return emptyList()
        val tickMs = MS_PER_MINUTE.toDouble() / (TICKS_PER_QUARTER * passage.tempoBpm)
        return passage.steps.flatMap { step ->
            step.notes.map {
                Tone(it.pitch, (step.onsetTicks * tickMs).toLong(), maxOf(1L, (it.lengthTicks * tickMs).toLong()))
            }
        }
    }
}
