package pianoanchor.engine.content

import pianoanchor.core.model.Skill
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TryCheck

private const val MIDDLE_C = 60
private const val C_BELOW_MIDDLE_C = 48
private const val G_ABOVE_MIDDLE_C = 67
private const val C_SHARP_ABOVE_MIDDLE_C = 61
private const val LOWEST_KEY = 21
private const val HIGHEST_KEY = 108
private const val PITCH_CLASS_C = 0
private const val PITCH_CLASS_F = 5
private const val BEATS_PER_MINUTE = 60
private const val EIGHTH_NOTES_TAPS = 8
private const val QUARTER = TICKS_PER_QUARTER
private const val EIGHTH = QUARTER / 2
private const val DOTTED_QUARTER = QUARTER + EIGHTH
private const val HALF = 2 * QUARTER

/**
 * The Try check of each of the 27 course skills (`docs/algorithms/try-checks.md`), keyed by skill id. Keyboard-wide
 * checks assume 88 keys (A0 to C8). Piece concepts check their first passage, the same one the Use step plays.
 */
internal object TryChecks {
    val all: Map<String, TryCheck> =
        mapOf(
            Skills.HAND_SHAPE.id to TryCheck.AnyKey,
            Skills.MIDDLE_C.id to TryCheck.PlayKey(MIDDLE_C, lit = true),
            Skills.BLACK_KEY_GROUPS.id to TryCheck.PlayKey(MIDDLE_C, lit = false),
            Skills.HIGH_AND_LOW.id to TryCheck.FindAll(PITCH_CLASS_C, LOWEST_KEY..HIGHEST_KEY),
            Skills.STEADY_BEAT.id to TryCheck.Repeat(MIDDLE_C, times = 8, bpm = BEATS_PER_MINUTE),
            Skills.QUARTER_AND_HALF.id to TryCheck.Hold(MIDDLE_C, clicks = 2, bpm = BEATS_PER_MINUTE),
            Skills.WHITE_KEY_NAMES.id to TryCheck.FindAll(PITCH_CLASS_F, LOWEST_KEY..HIGHEST_KEY),
            Skills.RH_C_POSITION.id to TryCheck.Sequence(listOf(60, 62, 64, 65, 67, 65, 64, 62, 60)),
            Skills.STEPS_AND_SKIPS.id to TryCheck.Sequence(listOf(64, 67)),
            Skills.FINGER_NUMBER_TUNES.id to passage(Skills.FINGER_NUMBER_TUNES),
            Skills.BARS_AND_COUNTING.id to TryCheck.StressBeatOne(MIDDLE_C, bars = 2, bpm = BEATS_PER_MINUTE),
            Skills.STAFF_STEPS.id to TryCheck.Sequence(listOf(60, 62, 64)),
            Skills.TREBLE_CLEF.id to TryCheck.PlayKey(G_ABOVE_MIDDLE_C, lit = false),
            Skills.SOFT_AND_LOUD.id to TryCheck.SoftThenLoud(MIDDLE_C),
            Skills.LH_C_POSITION.id to TryCheck.PlayKey(C_BELOW_MIDDLE_C, lit = true),
            Skills.BASS_CLEF_WHOLE_NOTE.id to TryCheck.Hold(C_BELOW_MIDDLE_C, clicks = 4, bpm = BEATS_PER_MINUTE),
            Skills.GRAND_STAFF_TURNS_RESTS.id to passage(Skills.GRAND_STAFF_TURNS_RESTS),
            Skills.THREE_FOUR_TIME.id to passage(Skills.THREE_FOUR_TIME),
            Skills.HANDS_TOGETHER_HELD.id to passage(Skills.HANDS_TOGETHER_HELD),
            Skills.C_CHORD.id to TryCheck.Chord(setOf(60, 64, 67)),
            Skills.G_AND_F_CHORDS.id to passage(Skills.G_AND_F_CHORDS),
            Skills.MELODY_AND_CHORDS.id to passage(Skills.MELODY_AND_CHORDS),
            Skills.SHARPS_AND_FLATS.id to TryCheck.PlayKey(C_SHARP_ABOVE_MIDDLE_C, lit = false),
            Skills.EIGHTH_NOTES.id to TryCheck.Rhythm(MIDDLE_C, List(EIGHTH_NOTES_TAPS) { EIGHTH }, BEATS_PER_MINUTE),
            Skills.EIGHTH_PAIRS.id to
                TryCheck.Rhythm(
                    MIDDLE_C,
                    listOf(QUARTER, QUARTER, EIGHTH, EIGHTH, QUARTER),
                    BEATS_PER_MINUTE,
                ),
            Skills.DOTTED_QUARTER.id to
                TryCheck.Rhythm(MIDDLE_C, listOf(DOTTED_QUARTER, EIGHTH, HALF), BEATS_PER_MINUTE),
            Skills.RHYTHM_SONG.id to passage(Skills.RHYTHM_SONG),
        )

    private fun passage(skill: Skill): TryCheck =
        TryCheck.Passage(Curriculum.passages.first { skill.id in it.skills }.id)
}
