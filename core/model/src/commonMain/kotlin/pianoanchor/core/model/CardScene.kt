package pianoanchor.core.model

/** Every card animation loops for exactly this long, in milliseconds, before its rest (see the engine's `Tunables`). */
public const val CARD_SCENE_LOOP_MS: Int = 4000

/** The longest caption of a card scene, in characters (the engine's `Tunables` explain why). */
public const val CARD_SCENE_CAPTION_MAX_CHARS: Int = 24

/** The longest description of a card scene, in characters: the same limit as a concept card field. */
public const val CARD_SCENE_DESCRIPTION_MAX_CHARS: Int = 120

private val FINGERS = 1..5

/** What the stage shows: a keyboard of [keys] (MIDI notes, white-key aligned), optionally a staff and a hand. */
public data class SceneStage(
    val keys: IntRange,
    val staff: SceneStaff? = null,
    val hand: SceneHand? = null,
) {
    init {
        require(!keys.isEmpty()) { "a scene stage needs at least one key" }
    }
}

/** Which staff a scene draws above its keyboard. */
public enum class SceneStaff { TREBLE, BASS, GRAND }

/** Which hand a scene draws over its keys; the left hand is the right one mirrored. */
public enum class SceneHand { RIGHT, LEFT }

/** One thing that happens at [atMs] for [lengthMs] within the loop. */
public data class SceneCue(
    val atMs: Int,
    val lengthMs: Int,
    val action: SceneAction,
)

/** What a [SceneCue] does; the whole vocabulary of card animations. */
public sealed interface SceneAction {
    /** Lights the key [note], with the [finger] number of the hand over it, brighter and ringed when [strong]. */
    public data class LightKey(
        val note: Int,
        val finger: Int? = null,
        val strong: Boolean = false,
    ) : SceneAction

    /** Shows the number of [finger] over the hand's finger. */
    public data class ShowFinger(
        val finger: Int,
    ) : SceneAction

    /** Draws the hand, [curved] or flat, over the key [overNote]. */
    public data class HandPose(
        val curved: Boolean,
        val overNote: Int,
    ) : SceneAction

    /** Draws [note] on the staff, [beats] long, linked to its key. */
    public data class StaffNote(
        val note: Int,
        val beats: Int = 1,
    ) : SceneAction

    /** A soft outline around the keys [notes], e.g. a black-key group. */
    public data class Highlight(
        val notes: Set<Int>,
    ) : SceneAction

    /** The beat dot, bigger when it is the [accent] (first) beat. */
    public data class Pulse(
        val accent: Boolean = false,
    ) : SceneAction

    /** A short caption; one shows at a time. */
    public data class Caption(
        val text: String,
    ) : SceneAction
}

/**
 * A concept card's looping animation: the [stage], the [cues] in a loop of [CARD_SCENE_LOOP_MS] and the [description]
 * TalkBack reads. Throws [IllegalArgumentException] for a cue outside the loop, a note off the keyboard, a finger
 * outside 1-5, a long or overlapping caption, a blank or long description, or a staff or hand action on a stage
 * without that staff or hand.
 */
public data class CardScene(
    val stage: SceneStage,
    val cues: List<SceneCue>,
    val description: String,
) {
    init {
        require(description.isNotBlank()) { "a card scene needs a description" }
        require(description.length <= CARD_SCENE_DESCRIPTION_MAX_CHARS) {
            "the description is longer than $CARD_SCENE_DESCRIPTION_MAX_CHARS characters"
        }
        cues.forEach(::validate)
        val captions = cues.filter { it.action is SceneAction.Caption }.sortedBy { it.atMs }
        captions.zipWithNext().forEach { (a, b) ->
            require(a.atMs + a.lengthMs <= b.atMs) { "captions at ${a.atMs} ms and ${b.atMs} ms overlap" }
        }
    }

    private fun validate(cue: SceneCue) {
        require(cue.atMs in 0 until CARD_SCENE_LOOP_MS) { "cue at ${cue.atMs} ms starts outside the loop" }
        require(cue.lengthMs > 0 && cue.atMs + cue.lengthMs <= CARD_SCENE_LOOP_MS) {
            "cue at ${cue.atMs} ms of ${cue.lengthMs} ms does not fit the loop"
        }
        when (val action = cue.action) {
            is SceneAction.LightKey -> {
                requireKeys(action.note)
                action.finger?.let {
                    requireFinger(it)
                    requireHand()
                }
            }

            is SceneAction.ShowFinger -> {
                requireHand()
                requireFinger(action.finger)
            }

            is SceneAction.HandPose -> {
                requireHand()
                requireKeys(action.overNote)
            }

            is SceneAction.StaffNote -> {
                require(stage.staff != null) { "a staff note needs a stage with a staff" }
                require(action.beats > 0) { "a staff note needs a positive length" }
                requireKeys(action.note)
            }

            is SceneAction.Highlight -> {
                requireKeys(*action.notes.toIntArray())
            }

            is SceneAction.Pulse -> {
                // Nothing to check: a beat dot names no note, finger or text.
            }

            is SceneAction.Caption -> {
                require(action.text.length <= CARD_SCENE_CAPTION_MAX_CHARS) {
                    "caption \"${action.text}\" is longer than $CARD_SCENE_CAPTION_MAX_CHARS characters"
                }
            }
        }
    }

    private fun requireKeys(vararg notes: Int) =
        notes.forEach { require(it in stage.keys) { "note $it is not on the stage's keys ${stage.keys}" } }

    private fun requireFinger(finger: Int) = require(finger in FINGERS) { "finger $finger is not 1 to 5" }

    private fun requireHand() = require(stage.hand != null) { "a finger or hand action needs a stage with a hand" }
}
