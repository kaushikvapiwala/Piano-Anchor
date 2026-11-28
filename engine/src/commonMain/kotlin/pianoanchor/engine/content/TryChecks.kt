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
private const val PITCH_CLASS_F_SHARP = 6
private val G_POSITION_RUN = listOf(67, 69, 71, 72, 74, 72, 71, 69, 67)
private val LEFT_HAND_G_RUN = listOf(50, 48, 47, 45, 43)
private val D7_SHORTCUT = setOf(42, 48, 50)
private val C_POSITION_RUN = listOf(60, 62, 64, 65, 67, 65, 64, 62, 60)
private val THUMB_UNDER_RUN = listOf(60, 62, 64, 65, 67, 65, 64, 62, 60)
private val C_SCALE = listOf(60, 62, 64, 65, 67, 69, 71, 72, 71, 69, 67, 65, 64, 62, 60)
private val G_SCALE = listOf(67, 69, 71, 72, 74, 76, 78, 79, 78, 76, 74, 72, 71, 69, 67)
private val LEFT_C_SCALE = listOf(48, 50, 52, 53, 55, 57, 59, 60, 59, 57, 55, 53, 52, 50, 48)
private val C_CHORD_KEYS = setOf(48, 52, 55)
private val F_CHORD_KEYS = setOf(45, 48, 53)
private val G_CHORD_KEYS = setOf(47, 50, 55)
private const val QUARTER = TICKS_PER_QUARTER
private const val EIGHTH = QUARTER / 2
private const val DOTTED_QUARTER = QUARTER + EIGHTH
private const val HALF = 2 * QUARTER

/**
 * The Try check of each of the 41 course skills (`docs/algorithms/try-checks.md`), keyed by skill id. Keyboard-wide
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
            Skills.RH_G_POSITION.id to TryCheck.Sequence(G_POSITION_RUN),
            Skills.LH_G_POSITION.id to TryCheck.Sequence(LEFT_HAND_G_RUN),
            Skills.KEY_OF_G.id to TryCheck.FindAll(PITCH_CLASS_F_SHARP, LOWEST_KEY..HIGHEST_KEY),
            Skills.G_AND_D7_CHORDS.id to TryCheck.Chord(D7_SHORTCUT),
            Skills.MELODY_CHORDS_IN_G.id to passage(Skills.MELODY_CHORDS_IN_G),
            Skills.LEGATO_TOUCH.id to TryCheck.Legato(C_POSITION_RUN),
            Skills.SUSTAIN_PEDAL.id to TryCheck.PedalHold(C_CHORD_KEYS),
            Skills.PEDAL_CHANGES.id to
                TryCheck.PedalChange(listOf(C_CHORD_KEYS, F_CHORD_KEYS, G_CHORD_KEYS, C_CHORD_KEYS)),
            Skills.PEDAL_SONG.id to passage(Skills.PEDAL_SONG),
            Skills.THUMB_UNDER.id to TryCheck.EvenSequence(THUMB_UNDER_RUN, BEATS_PER_MINUTE),
            Skills.C_MAJOR_SCALE.id to TryCheck.EvenSequence(C_SCALE, BEATS_PER_MINUTE),
            Skills.G_MAJOR_SCALE.id to TryCheck.EvenSequence(G_SCALE, BEATS_PER_MINUTE),
            Skills.LH_C_SCALE.id to TryCheck.EvenSequence(LEFT_C_SCALE, BEATS_PER_MINUTE),
            Skills.SCALE_SONG.id to passage(Skills.SCALE_SONG),
        )

    private fun passage(skill: Skill): TryCheck =
        TryCheck.Passage(Curriculum.passages.first { skill.id in it.skills }.id)
}
