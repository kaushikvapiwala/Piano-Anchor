package pianoanchor.engine.content

import pianoanchor.core.model.CARD_SCENE_LOOP_MS
import pianoanchor.core.model.CardScene
import pianoanchor.core.model.SceneAction
import pianoanchor.core.model.SceneAction.Caption
import pianoanchor.core.model.SceneAction.Highlight
import pianoanchor.core.model.SceneAction.LightKey
import pianoanchor.core.model.SceneAction.Pulse
import pianoanchor.core.model.SceneCue
import pianoanchor.core.model.SceneHand
import pianoanchor.core.model.SceneStaff
import pianoanchor.core.model.SceneStage

private const val MIDDLE_C = 60
private const val BEAT_MS = 800
private const val FIRST_BEAT_MS = 300
private const val BEATS = 4
private const val BEAT_CUE_MS = 600
private const val FINGER_MS = 500
private const val FIRST_FINGER_MS = 850
private const val POSE_OVERLAP_MS = 150
private const val POSE_FLAT_MS = 750
private const val POSE_CURVED_AT_MS = POSE_FLAT_MS - POSE_OVERLAP_MS
private const val CAPTION_END_MS = 3800
private const val GROUP_MS = 800
private const val C_MS = 400
private const val C_END_MS = CARD_SCENE_LOOP_MS - 100
private const val NAME_MS = 500
private const val FIRST_NAME_MS = 250
private const val LONG_BEATS = 2
private const val RUN_MS = 400
private const val RUN_START_MS = 300
private const val STEP_MS = 800
private const val SKIP_AT_MS = 1900
private const val COUNT_START_MS = 250
private const val COUNT_BEAT_MS = 450
private const val COUNT_DOT_MS = 440
private const val COUNT_BEATS = 8
private const val SOFT_END_MS = 2100
private const val LOUD_AT_MS = 2200
private const val STAFF_AT_MS = 300
private const val STAFF_GAP_MS = 1000
private const val STAFF_END_MS = 3700
private const val G_ABOVE_MIDDLE_C = 67
private const val LOW_C = 48
private const val E_ABOVE_LOW_C = 52
private const val F_ABOVE_LOW_C = 53
private const val G_ABOVE_LOW_C = 55
private const val E_ABOVE_MIDDLE_C = 64
private const val THREE_BEATS = 3
private const val WALTZ_FIRST_MS = 250
private const val WALTZ_BEAT_MS = 600
private const val WALTZ_DOT_MS = 560
private const val WALTZ_BAR_MS = THREE_BEATS * WALTZ_BEAT_MS
private const val WALTZ_HOLD_MS = WALTZ_BAR_MS - 40
private const val TURN_MS = 800
private const val TURN_GAP_MS = 1000
private const val HELD_MS = 1000
private const val WHOLE_NOTE_AT_MS = 2100
private const val WHOLE_NOTE_PULSE_MS = 450
private const val WHOLE_NOTE_DOT_MS = 400
private const val FIRST_FINGER_MS_LEFT = 300
private const val CHORD_FINGER_AT_MS = 300
private const val CHORD_FINGER_GAP_MS = 500
private const val CHORD_AT_MS = 1800
private const val SYMBOL_AT_MS = 250
private const val SYMBOL_MS = 1200
private const val TUNE_AT_MS = 50
private const val TUNE_NOTE_MS = 900
private const val ACCIDENTAL_AT_MS = 900
private const val ACCIDENTAL_MS = 1200
private const val FLAT_AT_MS = 2800
private const val D_ABOVE_MIDDLE_C = 62
private const val F_ABOVE_MIDDLE_C = 65
private const val A_ABOVE_MIDDLE_C = 69
private const val B_BELOW_MIDDLE_C = 59
private const val C_SHARP = 61
private val ODE_NOTES = listOf(E_ABOVE_MIDDLE_C, E_ABOVE_MIDDLE_C, F_ABOVE_MIDDLE_C, G_ABOVE_MIDDLE_C)
private val C_CHORD_NOTES = listOf(MIDDLE_C, E_ABOVE_MIDDLE_C, G_ABOVE_MIDDLE_C)
private val LOW_C_CHORD_NOTES = listOf(LOW_C, E_ABOVE_LOW_C, G_ABOVE_LOW_C)
private val FIVE_FINGER_NOTES = listOf(60, 62, 64, 65, 67)
private val RUN_NOTES = FIVE_FINGER_NOTES + FIVE_FINGER_NOTES.dropLast(1).reversed()

private val CS = intArrayOf(36, 48, 60, 72, 84)
private val WHITE_NAMES = listOf("C" to 48, "D" to 50, "E" to 52, "F" to 53, "G" to 55, "A" to 57, "B" to 59)

/** A chord symbol and its keys lit together for one [SYMBOL_MS] slot. */
private fun chord(
    at: Int,
    symbol: String,
    notes: List<Int>,
): List<SceneCue> = notes.map { cue(at, SYMBOL_MS, LightKey(it)) } + cue(at, SYMBOL_MS, Caption(symbol))

private fun cue(
    at: Int,
    length: Int,
    action: SceneAction,
) = SceneCue(at, length, action)

/** The beat dots at 4 even beats, each with [key] lit for its [lengths] (in beats, one per lit beat) from the first. */
private fun beats(vararg keyLengths: Pair<Int, Int>): List<SceneCue> =
    List(BEATS) { cue(FIRST_BEAT_MS + it * BEAT_MS, BEAT_CUE_MS, Pulse(accent = it == 0)) } +
        keyLengths.map { (beat, lengthInBeats) ->
            cue(FIRST_BEAT_MS + beat * BEAT_MS, lengthInBeats * BEAT_MS - (BEAT_MS - BEAT_CUE_MS), LightKey(MIDDLE_C))
        }

/**
 * The looping animations of the concept cards, one per skill, written in the shared scene vocabulary so every card
 * animates in the same style (`docs/ui/living-studio.md`, "Card animations"). Units 0 to 3 now;
 * later units add theirs.
 */
internal object CardScenes {
    private val handShape =
        CardScene(
            SceneStage(60..67, hand = SceneHand.RIGHT),
            listOf(
                cue(0, POSE_FLAT_MS, SceneAction.HandPose(curved = false, overNote = MIDDLE_C)),
                cue(
                    POSE_CURVED_AT_MS,
                    CARD_SCENE_LOOP_MS - POSE_CURVED_AT_MS,
                    SceneAction.HandPose(curved = true, overNote = MIDDLE_C),
                ),
                cue(800, CAPTION_END_MS - 800, Caption("Curved, like a ball")),
            ) + (1..5).map { cue(FIRST_FINGER_MS + (it - 1) * FINGER_MS, FINGER_MS, SceneAction.ShowFinger(it)) },
            "A hand curves over the keys and fingers 1 to 5 light up in turn.",
        )

    private val middleC =
        CardScene(
            SceneStage(48..72),
            listOf(
                cue(200, 3600, Highlight(setOf(61, 63))),
                cue(800, 700, LightKey(MIDDLE_C, strong = true)),
                cue(1800, 800, LightKey(MIDDLE_C, strong = true)),
                cue(400, 3400, Caption("Middle C")),
            ),
            "Middle C lights up twice, just left of the group of two black keys.",
        )

    private val blackKeyGroups =
        CardScene(
            SceneStage(48..72),
            listOf(
                listOf(49, 51),
                listOf(54, 56, 58),
                listOf(61, 63),
                listOf(66, 68, 70),
            ).mapIndexed { i, keys -> cue(100 + i * GROUP_MS, GROUP_MS, Highlight(keys.toSet())) } +
                cue(300, 3600, Caption("2s and 3s")),
            "The black keys come in groups of two and groups of three, repeating up the keyboard.",
        )

    private val highAndLow =
        CardScene(
            SceneStage(36..84),
            CS.mapIndexed { i, c -> cue(300 + i * C_MS, C_END_MS - (300 + i * C_MS), LightKey(c)) } +
                cue(300, 3600, Caption("Every C")),
            "Every C lights up from the lowest to the highest and stays lit.",
        )

    private val steadyBeat =
        CardScene(
            SceneStage(55..67),
            beats(0 to 1, 1 to 1, 2 to 1, 3 to 1),
            "A dot pulses four times, evenly, and middle C lights with each beat.",
        )

    private val quarterAndHalf =
        CardScene(
            SceneStage(55..67),
            beats(0 to 1, 1 to 1, 2 to LONG_BEATS) + cue(300, 3500, Caption("1 click, 2 clicks")),
            "Middle C lights for one beat, one beat again, then glows for two beats.",
        )

    private val whiteKeyNames =
        CardScene(
            SceneStage(48..72),
            listOf(
                cue(FIRST_NAME_MS, 1500, Highlight(setOf(49, 51))),
                cue(FIRST_NAME_MS + 1500, 2000, Highlight(setOf(54, 56, 58))),
            ) +
                WHITE_NAMES
                    .mapIndexed { i, pair ->
                        val at = FIRST_NAME_MS + i * NAME_MS
                        listOf(cue(at, NAME_MS, LightKey(pair.second)), cue(at, NAME_MS, Caption(pair.first)))
                    }.flatten(),
            "White keys light up in turn: C, D, E by the group of two, then F, G, A, B by the group of three.",
        )

    private val rightHandPosition =
        CardScene(
            SceneStage(60..67, hand = SceneHand.RIGHT),
            listOf(cue(0, CARD_SCENE_LOOP_MS, SceneAction.HandPose(curved = true, overNote = MIDDLE_C))) +
                RUN_NOTES.mapIndexed { i, note ->
                    val finger = FIVE_FINGER_NOTES.indexOf(note) + 1
                    cue(RUN_START_MS + i * RUN_MS, RUN_MS, LightKey(note, finger))
                },
            "The right hand sits curved on C to G and its fingers 1 to 5 light up the keys, up and then back down.",
        )

    private val stepsAndSkips =
        CardScene(
            SceneStage(60..72),
            listOf(
                cue(RUN_START_MS, STEP_MS, LightKey(60)),
                cue(RUN_START_MS + STEP_MS, STEP_MS, LightKey(D_ABOVE_MIDDLE_C)),
                cue(RUN_START_MS, 2 * STEP_MS, Caption("Step")),
                cue(SKIP_AT_MS, STEP_MS, LightKey(64)),
                cue(SKIP_AT_MS, STEP_MS, Highlight(setOf(65))),
                cue(SKIP_AT_MS + STEP_MS, STEP_MS, LightKey(67)),
                cue(SKIP_AT_MS, CAPTION_END_MS - SKIP_AT_MS, Caption("Skip")),
            ),
            "Two neighbouring keys light up for a step, then a key and the one two white keys up for a skip.",
        )

    private val fingerNumberTunes =
        CardScene(
            SceneStage(60..67, hand = SceneHand.RIGHT),
            listOf(
                cue(0, CARD_SCENE_LOOP_MS, SceneAction.HandPose(curved = true, overNote = MIDDLE_C)),
                cue(RUN_START_MS, STEP_MS, LightKey(64, finger = 3)),
                cue(RUN_START_MS + STEP_MS, STEP_MS, LightKey(62, finger = 2)),
                cue(RUN_START_MS + 2 * STEP_MS, LONG_BEATS * STEP_MS, LightKey(MIDDLE_C, finger = 1)),
                cue(RUN_START_MS, CAPTION_END_MS - RUN_START_MS, Caption("Play by finger")),
            ),
            "The first bar of Hot Cross Buns lights up under fingers 3, 2 and 1.",
        )

    private val barsAndCounting =
        CardScene(
            SceneStage(55..67),
            List(COUNT_BEATS) {
                cue(COUNT_START_MS + it * COUNT_BEAT_MS, COUNT_DOT_MS, Pulse(accent = it % BEATS == 0))
            } +
                List(COUNT_BEATS) {
                    cue(COUNT_START_MS + it * COUNT_BEAT_MS, COUNT_BEAT_MS, Caption("${it % BEATS + 1}"))
                },
            "A dot pulses eight times and counts 1 2 3 4 twice, with a bigger pulse on each 1.",
        )

    private val staffSteps =
        CardScene(
            SceneStage(60..67, staff = SceneStaff.TREBLE),
            listOf(60, 62, 64)
                .mapIndexed { i, note ->
                    val at = STAFF_AT_MS + i * STAFF_GAP_MS
                    listOf(
                        cue(at, STAFF_END_MS - at, SceneAction.StaffNote(note)),
                        cue(at, STAFF_GAP_MS - POSE_OVERLAP_MS, LightKey(note)),
                    )
                }.flatten(),
            "Notes climb the staff from middle C one step at a time, each lighting its key.",
        )

    private val trebleClef =
        CardScene(
            SceneStage(60..72, staff = SceneStaff.TREBLE),
            listOf(
                cue(STAFF_AT_MS, STAFF_END_MS - STAFF_AT_MS, SceneAction.StaffNote(G_ABOVE_MIDDLE_C)),
                cue(STAFF_AT_MS, STAFF_END_MS - STAFF_AT_MS, LightKey(G_ABOVE_MIDDLE_C, strong = true)),
                cue(STAFF_AT_MS, STAFF_END_MS - STAFF_AT_MS, Caption("G")),
            ),
            "The note G appears on the second line of the treble staff, and that G lights up on the keys.",
        )

    private val leftHandPosition =
        CardScene(
            SceneStage(LOW_C..G_ABOVE_LOW_C, hand = SceneHand.LEFT),
            listOf(cue(0, CARD_SCENE_LOOP_MS, SceneAction.HandPose(curved = true, overNote = G_ABOVE_LOW_C))) +
                (LOW_C..52).mapIndexed { i, note ->
                    cue(FIRST_FINGER_MS_LEFT + i * RUN_MS, RUN_MS, LightKey(note, finger = 5 - i))
                },
            "The left hand sits curved on the low C and its fingers 5 down to 1 light up the keys in turn.",
        )

    private val bassClefWholeNote =
        CardScene(
            SceneStage(43..55, staff = SceneStaff.BASS),
            listOf(
                cue(
                    STAFF_AT_MS,
                    WHOLE_NOTE_AT_MS - STAFF_AT_MS,
                    SceneAction.StaffNote(F_ABOVE_LOW_C),
                ),
                cue(
                    STAFF_AT_MS,
                    WHOLE_NOTE_AT_MS - STAFF_AT_MS,
                    LightKey(F_ABOVE_LOW_C, strong = true),
                ),
                cue(STAFF_AT_MS, WHOLE_NOTE_AT_MS - STAFF_AT_MS, Caption("F line")),
                cue(WHOLE_NOTE_AT_MS, CAPTION_END_MS - WHOLE_NOTE_AT_MS, SceneAction.StaffNote(LOW_C, beats = BEATS)),
                cue(WHOLE_NOTE_AT_MS, CAPTION_END_MS - WHOLE_NOTE_AT_MS, LightKey(LOW_C)),
                cue(WHOLE_NOTE_AT_MS, CAPTION_END_MS - WHOLE_NOTE_AT_MS, Caption("Whole note: 4 beats")),
            ) +
                List(BEATS) {
                    cue(WHOLE_NOTE_AT_MS + it * WHOLE_NOTE_PULSE_MS, WHOLE_NOTE_DOT_MS, Pulse(accent = it == 0))
                },
            "A note sits on the F line of the bass staff and lights its key, then a whole note glows for four beats.",
        )

    private val grandStaffTurnsRests =
        CardScene(
            SceneStage(LOW_C..72, staff = SceneStaff.GRAND),
            listOf(
                cue(STAFF_AT_MS, TURN_MS, SceneAction.StaffNote(E_ABOVE_MIDDLE_C)),
                cue(STAFF_AT_MS, TURN_MS, LightKey(E_ABOVE_MIDDLE_C)),
                cue(STAFF_AT_MS, TURN_MS, Caption("Right hand")),
                cue(STAFF_AT_MS + TURN_GAP_MS, TURN_MS, Pulse(accent = true)),
                cue(STAFF_AT_MS + TURN_GAP_MS, TURN_MS, Caption("Rest: stay quiet")),
                cue(STAFF_AT_MS + 2 * TURN_GAP_MS, TURN_MS, SceneAction.StaffNote(F_ABOVE_LOW_C)),
                cue(STAFF_AT_MS + 2 * TURN_GAP_MS, TURN_MS, LightKey(F_ABOVE_LOW_C)),
                cue(STAFF_AT_MS + 2 * TURN_GAP_MS, TURN_MS, Caption("Left hand")),
            ),
            "A treble note lights its key, a rest gives a quiet beat, then a bass note lights its key.",
        )

    private val threeFourTime =
        CardScene(
            SceneStage(55..67),
            List(2 * THREE_BEATS) {
                cue(WALTZ_FIRST_MS + it * WALTZ_BEAT_MS, WALTZ_DOT_MS, Pulse(accent = it % THREE_BEATS == 0))
            } +
                (0 until 2).flatMap {
                    listOf(
                        cue(WALTZ_FIRST_MS + it * WALTZ_BAR_MS, WALTZ_HOLD_MS, LightKey(MIDDLE_C)),
                        cue(WALTZ_FIRST_MS + it * WALTZ_BAR_MS, WALTZ_HOLD_MS, Caption("1 2 3")),
                    )
                },
            "A dot pulses six times in two groups of three, and middle C glows for the three beats of each group.",
        )

    private val handsTogetherHeld =
        CardScene(
            SceneStage(LOW_C..67, staff = SceneStaff.GRAND),
            listOf(
                cue(0, CARD_SCENE_LOOP_MS, LightKey(LOW_C)),
                cue(0, CARD_SCENE_LOOP_MS, SceneAction.StaffNote(LOW_C)),
                cue(STAFF_AT_MS, CAPTION_END_MS - STAFF_AT_MS, Caption("Left holds, right moves")),
            ) +
                listOf(MIDDLE_C, 62, E_ABOVE_MIDDLE_C).withIndex().flatMap { (i, note) ->
                    val at = HELD_MS / 2 + i * HELD_MS
                    listOf(cue(at, HELD_MS, LightKey(note)), cue(at, HELD_MS, SceneAction.StaffNote(note)))
                },
            "The left hand holds the low C all the way through while the right hand lights middle C, D and E in turn.",
        )

    private val softAndLoud =
        CardScene(
            SceneStage(55..67),
            listOf(
                cue(FIRST_NAME_MS, SOFT_END_MS - FIRST_NAME_MS, LightKey(MIDDLE_C)),
                cue(FIRST_NAME_MS, SOFT_END_MS - FIRST_NAME_MS, Caption("p soft")),
                cue(LOUD_AT_MS, CAPTION_END_MS - LOUD_AT_MS, LightKey(MIDDLE_C, strong = true)),
                cue(LOUD_AT_MS, CAPTION_END_MS - LOUD_AT_MS, Caption("f loud")),
            ),
            "Middle C glows faintly for soft, then bright and ringed for loud.",
        )

    private val cChord =
        CardScene(
            SceneStage(60..67, hand = SceneHand.RIGHT),
            listOf(cue(0, CARD_SCENE_LOOP_MS, SceneAction.HandPose(curved = true, overNote = MIDDLE_C))) +
                listOf(1, 3, 5).mapIndexed { i, finger ->
                    cue(
                        CHORD_FINGER_AT_MS + i * CHORD_FINGER_GAP_MS,
                        CHORD_FINGER_GAP_MS,
                        SceneAction.ShowFinger(finger),
                    )
                } +
                C_CHORD_NOTES.zip(listOf(1, 3, 5)).map { (note, finger) ->
                    cue(CHORD_AT_MS, CAPTION_END_MS - CHORD_AT_MS, LightKey(note, finger, strong = true))
                } +
                cue(CHORD_AT_MS, CAPTION_END_MS - CHORD_AT_MS, Caption("C chord")),
            "Fingers 1, 3 and 5 show in turn, then middle C, E and G light up together as the C chord.",
        )

    private val gAndFChords =
        CardScene(
            SceneStage(55..72),
            listOf(
                chord(SYMBOL_AT_MS, "C", C_CHORD_NOTES - G_ABOVE_MIDDLE_C),
                chord(SYMBOL_AT_MS + SYMBOL_MS, "G", listOf(B_BELOW_MIDDLE_C, D_ABOVE_MIDDLE_C)),
                chord(SYMBOL_AT_MS + 2 * SYMBOL_MS, "F", listOf(MIDDLE_C, F_ABOVE_MIDDLE_C, A_ABOVE_MIDDLE_C)),
                // The G both the C and the G chord share stays lit across the change.
                listOf(cue(SYMBOL_AT_MS, 2 * SYMBOL_MS, LightKey(G_ABOVE_MIDDLE_C))),
            ).flatten(),
            "The C chord lights, then the G chord with its top G still lit, then the F chord.",
        )

    private val melodyAndChords =
        CardScene(
            SceneStage(LOW_C..72, staff = SceneStaff.GRAND),
            LOW_C_CHORD_NOTES.flatMap {
                listOf(cue(0, CARD_SCENE_LOOP_MS, LightKey(it)), cue(0, CARD_SCENE_LOOP_MS, SceneAction.StaffNote(it)))
            } +
                ODE_NOTES.withIndex().flatMap { (i, note) ->
                    val at = TUNE_AT_MS + i * TUNE_NOTE_MS
                    listOf(
                        cue(at, TUNE_NOTE_MS - POSE_OVERLAP_MS, LightKey(note, strong = true)),
                        cue(at, TUNE_NOTE_MS - POSE_OVERLAP_MS, SceneAction.StaffNote(note)),
                    )
                } +
                cue(STAFF_AT_MS, CAPTION_END_MS - STAFF_AT_MS, Caption("Left holds, right plays")),
            "The left hand holds a low C chord while the right hand plays E, E, F, G, the opening of Ode to Joy.",
        )

    private val sharpsAndFlats =
        CardScene(
            SceneStage(60..64),
            listOf(
                cue(STAFF_AT_MS, ACCIDENTAL_AT_MS - STAFF_AT_MS, LightKey(MIDDLE_C)),
                cue(ACCIDENTAL_AT_MS, ACCIDENTAL_MS, LightKey(C_SHARP, strong = true)),
                cue(ACCIDENTAL_AT_MS, ACCIDENTAL_MS, Caption("C\u266F")),
                cue(
                    ACCIDENTAL_AT_MS + ACCIDENTAL_MS,
                    FLAT_AT_MS - ACCIDENTAL_AT_MS - ACCIDENTAL_MS,
                    LightKey(D_ABOVE_MIDDLE_C),
                ),
                cue(FLAT_AT_MS, CAPTION_END_MS - FLAT_AT_MS - POSE_OVERLAP_MS, LightKey(C_SHARP, strong = true)),
                cue(FLAT_AT_MS, CAPTION_END_MS - FLAT_AT_MS - POSE_OVERLAP_MS, Caption("D\u266D")),
            ),
            "C lights, then the black key just right of it for C sharp; D lights, then the same black key for D flat.",
        )

    /** Every card scene by the id of its skill. */
    val all: Map<String, CardScene> =
        mapOf(
            Skills.HAND_SHAPE.id to handShape,
            Skills.MIDDLE_C.id to middleC,
            Skills.BLACK_KEY_GROUPS.id to blackKeyGroups,
            Skills.HIGH_AND_LOW.id to highAndLow,
            Skills.STEADY_BEAT.id to steadyBeat,
            Skills.QUARTER_AND_HALF.id to quarterAndHalf,
            Skills.WHITE_KEY_NAMES.id to whiteKeyNames,
            Skills.RH_C_POSITION.id to rightHandPosition,
            Skills.STEPS_AND_SKIPS.id to stepsAndSkips,
            Skills.FINGER_NUMBER_TUNES.id to fingerNumberTunes,
            Skills.BARS_AND_COUNTING.id to barsAndCounting,
            Skills.STAFF_STEPS.id to staffSteps,
            Skills.TREBLE_CLEF.id to trebleClef,
            Skills.SOFT_AND_LOUD.id to softAndLoud,
            Skills.LH_C_POSITION.id to leftHandPosition,
            Skills.BASS_CLEF_WHOLE_NOTE.id to bassClefWholeNote,
            Skills.GRAND_STAFF_TURNS_RESTS.id to grandStaffTurnsRests,
            Skills.THREE_FOUR_TIME.id to threeFourTime,
            Skills.HANDS_TOGETHER_HELD.id to handsTogetherHeld,
            Skills.C_CHORD.id to cChord,
            Skills.G_AND_F_CHORDS.id to gAndFChords,
            Skills.MELODY_AND_CHORDS.id to melodyAndChords,
            Skills.SHARPS_AND_FLATS.id to sharpsAndFlats,
        )
}
