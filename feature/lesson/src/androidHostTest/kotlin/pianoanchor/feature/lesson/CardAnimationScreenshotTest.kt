package pianoanchor.feature.lesson

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pianoanchor.core.model.CardScene
import pianoanchor.core.model.SceneAction
import pianoanchor.core.model.SceneCue
import pianoanchor.core.model.SceneHand
import pianoanchor.core.model.SceneStaff
import pianoanchor.core.model.SceneStage
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.ui.shell.PianoAnchorTheme

/**
 * Golden screenshots of the Unit 0 card scenes at their midpoint frame in light and dark, plus a left-hand and a staff
 * test scene. `./gradlew :feature:lesson:recordRoborazziAndroidHostTest` rewrites them under
 * `feature/lesson/screenshots/`; fastCheck verifies them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SDK], qualifiers = "w360dp-h640dp-xxhdpi")
class CardAnimationScreenshotTest {
    private fun capture(
        name: String,
        scene: CardScene,
    ) {
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            captureRoboImage("screenshots/CardScene_${name}_$theme.png") {
                PianoAnchorTheme(darkTheme = dark) {
                    CardFrame(scene, { CARD_SCENE_MIDPOINT_MS }, Modifier.padding(8.dp))
                }
            }
        }
    }

    private fun skill(id: String) = capture(id, checkNotNull(Curriculum.scene(id)))

    @Test
    fun handShape() = skill(Skills.HAND_SHAPE.id)

    @Test
    fun middleC() = skill(Skills.MIDDLE_C.id)

    @Test
    fun blackKeyGroups() = skill(Skills.BLACK_KEY_GROUPS.id)

    @Test
    fun highAndLow() = skill(Skills.HIGH_AND_LOW.id)

    @Test
    fun steadyBeat() = skill(Skills.STEADY_BEAT.id)

    @Test
    fun quarterAndHalf() = skill(Skills.QUARTER_AND_HALF.id)

    @Test
    fun whiteKeyNames() = skill(Skills.WHITE_KEY_NAMES.id)

    @Test
    fun leftHandMirrorsTheRight() {
        val right = checkNotNull(Curriculum.scene(Skills.HAND_SHAPE.id))
        val cues =
            right.cues.map {
                val pose = it.action as? SceneAction.HandPose
                if (pose == null) it else it.copy(action = pose.copy(overNote = 67))
            }
        capture("test_left_hand", right.copy(stage = right.stage.copy(hand = SceneHand.LEFT), cues = cues))
    }

    @Test
    fun aGrandStaffNote() =
        capture(
            "test_grand_staff",
            CardScene(
                SceneStage(48..72, staff = SceneStaff.GRAND),
                listOf(
                    SceneCue(200, 3600, SceneAction.StaffNote(60)),
                    SceneCue(200, 3600, SceneAction.LightKey(60)),
                    SceneCue(200, 3600, SceneAction.StaffNote(67, beats = 2)),
                    SceneCue(200, 3600, SceneAction.StaffNote(50)),
                    SceneCue(200, 3600, SceneAction.Caption("Middle C")),
                ),
                "A test scene.",
            ),
        )
}
