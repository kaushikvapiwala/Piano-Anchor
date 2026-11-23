package pianoanchor.feature.lesson

import androidx.compose.animation.core.FastOutSlowInEasing
import pianoanchor.core.model.CARD_SCENE_LOOP_MS
import pianoanchor.core.model.CardScene
import pianoanchor.core.model.SceneAction
import pianoanchor.core.model.SceneCue
import pianoanchor.engine.Tunables

private const val SLOT_CENTRE = 0.5f

/** The frame shown when the system asks for less motion: the middle of the loop. */
internal const val CARD_SCENE_MIDPOINT_MS: Int = CARD_SCENE_LOOP_MS / 2

/** The loop plus its rest: the time one cycle of a card animation takes. */
internal const val CARD_SCENE_CYCLE_MS: Int = CARD_SCENE_LOOP_MS + Tunables.CARD_SCENE_REST_MS

/** A cue's action and how visible it is now, from 0 (faded out) to 1. */
internal data class ActiveAction(
    val cue: SceneCue,
    val alpha: Float,
) {
    val action: SceneAction get() = cue.action
}

/**
 * How visible [cue] is at [timeMs]: it fades in and out over `CARD_SCENE_FADE_MS` each with the one motion curve
 * (`FastOutSlowInEasing`), and is 0 outside its time.
 */
internal fun cueAlpha(
    cue: SceneCue,
    timeMs: Int,
): Float {
    val into = timeMs - cue.atMs
    val left = cue.atMs + cue.lengthMs - timeMs
    if (into < 0 || left <= 0) return 0f
    val fade = Tunables.CARD_SCENE_FADE_MS.toFloat()
    return FastOutSlowInEasing.transform(minOf(1f, into / fade, left / fade))
}

/** The actions of [scene] visible at [timeMs] of its cycle, in cue order; none during the rest. */
internal fun activeActions(
    scene: CardScene,
    timeMs: Int,
): List<ActiveAction> =
    scene.cues.mapNotNull { cue ->
        cueAlpha(cue, timeMs).takeIf { it > 0f }?.let { ActiveAction(cue, it) }
    }

/** The keys lit at [timeMs] of [scene]'s cycle. */
internal fun litKeysAt(
    scene: CardScene,
    timeMs: Int,
): Set<Int> = activeActions(scene, timeMs).mapNotNull { (it.action as? SceneAction.LightKey)?.note }.toSet()

/**
 * Where along the note area (0 to 1) the staff note of [cue] sits: the staff notes of [scene] share the area in the
 * order they start, so repeated notes get their own slots.
 */
internal fun staffSlot(
    scene: CardScene,
    cue: SceneCue,
): Float {
    val notes = scene.cues.filter { it.action is SceneAction.StaffNote }.sortedBy { it.atMs }
    return (notes.indexOfFirst { it === cue } + SLOT_CENTRE) / notes.size
}
