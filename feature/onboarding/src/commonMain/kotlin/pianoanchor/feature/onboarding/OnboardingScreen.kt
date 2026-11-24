package pianoanchor.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.MidiDevice
import pianoanchor.engine.content.Curriculum
import pianoanchor.feature.onboarding.resources.Res
import pianoanchor.feature.onboarding.resources.onboarding_connect_connected
import pianoanchor.feature.onboarding.resources.onboarding_connect_continue
import pianoanchor.feature.onboarding.resources.onboarding_connect_not_found
import pianoanchor.feature.onboarding.resources.onboarding_connect_searching
import pianoanchor.feature.onboarding.resources.onboarding_connect_sleep
import pianoanchor.feature.onboarding.resources.onboarding_connect_title
import pianoanchor.feature.onboarding.resources.onboarding_continue
import pianoanchor.feature.onboarding.resources.onboarding_placement_next
import pianoanchor.feature.onboarding.resources.onboarding_start_beginning
import pianoanchor.feature.onboarding.resources.onboarding_start_beginning_summary
import pianoanchor.feature.onboarding.resources.onboarding_start_error
import pianoanchor.feature.onboarding.resources.onboarding_start_test
import pianoanchor.feature.onboarding.resources.onboarding_start_test_summary
import pianoanchor.feature.onboarding.resources.onboarding_step
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

internal val TouchTarget = 44.dp
internal val PillShape = RoundedCornerShape(22.dp)
private const val STEP_COUNT = 3

/** The panes split the width about 45/55, left to right. */
private const val LEFT_PANE_WEIGHT = 0.45f
private const val RIGHT_PANE_WEIGHT = 0.55f
private val PaneGap = 24.dp

/**
 * First-launch onboarding (Living Studio, `docs/ui/living-studio.md`): one step at a time in landscape
 * (explanation left, controls right), as [state] says: press a key, echo C-E-G and choose the start point ("Test me"
 * or the beginning). While a piece of the placement test is played, [runSlot] hosts its run under a header with
 * "Stop here" (the app puts a Practice screen there). Stateless: see [OnboardingViewModel].
 */
@Composable
public fun OnboardingScreen(
    state: OnboardingState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
    runSlot: @Composable (PlacementPiece) -> Unit = {},
) {
    val piece = state.piece
    if (piece != null && state.verdict == null) {
        PlacementRun(piece, actions.onStopHere, modifier, runSlot)
        return
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        // Landscape: the left pane (title, explanation, picture, step dots) reads first, then the controls.
        Row(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(PaneGap),
        ) {
            Column(modifier = Modifier.weight(LEFT_PANE_WEIGHT).fillMaxHeight()) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (state.step) {
                        OnboardingStep.CONNECT -> ConnectLeft(state)
                        OnboardingStep.ECHO -> EchoLeft(state)
                        OnboardingStep.START_POINT -> StartPointLeft(state)
                    }
                }
                StepDots(state.step.ordinal)
            }
            Column(
                modifier = Modifier.weight(RIGHT_PANE_WEIGHT).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state.step) {
                    OnboardingStep.CONNECT -> ConnectRight(state, actions)
                    OnboardingStep.ECHO -> EchoRight(state, actions)
                    OnboardingStep.START_POINT -> StartPointRight(state, actions)
                }
            }
        }
    }
}

/** Three dots, the current one filled, with the "Step n of 3" text that screen readers get. */
@Composable
private fun StepDots(current: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        val colors = MaterialTheme.colorScheme
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clearAndSetSemantics {}) {
            repeat(STEP_COUNT) { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (index == current) colors.primary else colors.outlineVariant, CircleShape),
                )
            }
        }
        Text(
            stringResource(Res.string.onboarding_step, (current + 1).toString(), STEP_COUNT.toString()),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Title(text: StringResource) {
    Text(
        stringResource(text),
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        modifier = Modifier.semantics { heading() },
    )
}

/** "Searching for your piano…" or "Connected · name", read out when it changes. */
@Composable
internal fun ConnectionLine(device: MidiDevice?) {
    val connected = device != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        val dot = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        Box(Modifier.size(12.dp).background(dot, CircleShape))
        Text(
            if (device == null) {
                stringResource(Res.string.onboarding_connect_searching)
            } else {
                stringResource(Res.string.onboarding_connect_connected, device.name)
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ConnectLeft(state: OnboardingState) {
    Title(Res.string.onboarding_connect_title)
    val piano = state.device ?: state.listed
    ConnectionLine(piano)
    if (piano != null) Text(stringResource(Res.string.onboarding_connect_continue))
}

@Composable
private fun ConnectRight(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    if (state.pianoListHint) {
        TextButton(onClick = actions.onOpenPianoList, modifier = Modifier.heightIn(min = TouchTarget)) {
            Text(stringResource(Res.string.onboarding_connect_not_found))
        }
        Text(stringResource(Res.string.onboarding_connect_sleep))
    }
}

@Composable
private fun StartPointRight(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    if (state.storageFailed) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(Res.string.onboarding_start_error),
                modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (state.piece != null) {
        val passed = state.verdict == PlacementVerdict.PASSED
        val next = if (passed) Res.string.onboarding_placement_next else Res.string.onboarding_continue
        PillButton(stringResource(next), actions.onNextPiece)
        return
    }
    ChoiceCard(
        stringResource(Res.string.onboarding_start_test),
        stringResource(Res.string.onboarding_start_test_summary),
        primary = true,
        onClick = actions.onTestMe,
    )
    ChoiceCard(
        stringResource(Res.string.onboarding_start_beginning),
        stringResource(Res.string.onboarding_start_beginning_summary, Curriculum.units.first().title),
        primary = false,
        onClick = actions.onStartAtBeginning,
    )
}

/** A whole-card button: a [title] and a one-line [summary]. */
@Composable
private fun ChoiceCard(
    title: String,
    summary: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor = if (primary) colors.primaryContainer else colors.surface,
                contentColor = if (primary) colors.onPrimaryContainer else colors.onSurface,
            ),
        border = if (primary) null else BorderStroke(1.dp, colors.outlineVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun PillButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.height(TouchTarget), shape = PillShape) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
