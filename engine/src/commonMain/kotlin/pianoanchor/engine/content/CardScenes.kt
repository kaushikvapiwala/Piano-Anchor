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
private val FIVE_FINGER_NOTES = listOf(60, 62, 64, 65, 67)
private val RUN_NOTES = FIVE_FINGER_NOTES + FIVE_FINGER_NOTES.dropLast(1).reversed()

private val CS = intArrayOf(36, 48, 60, 72, 84)
private val WHITE_NAMES = listOf("C" to 48, "D" to 50, "E" to 52, "F" to 53, "G" to 55, "A" to 57, "B" to 59)

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
 * animates in the same style (`docs/ui/living-studio.md`, "Card animations"). Units 0 and 1 now;
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
                cue(RUN_START_MS + STEP_MS, STEP_MS, LightKey(62)),
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
                cue(STAFF_AT_MS, STAFF_END_MS - STAFF_AT_MS, Highlight(setOf(G_ABOVE_MIDDLE_C))),
                cue(STAFF_AT_MS, STAFF_END_MS - STAFF_AT_MS, LightKey(G_ABOVE_MIDDLE_C, strong = true)),
                cue(STAFF_AT_MS, STAFF_END_MS - STAFF_AT_MS, Caption("G")),
            ),
            "The treble clef circles the line of the note G, and that G lights up on the keys.",
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
        )
}
