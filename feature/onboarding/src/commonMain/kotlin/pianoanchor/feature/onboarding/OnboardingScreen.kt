package pianoanchor.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import pianoanchor.feature.onboarding.resources.onboarding_connect_not_found
import pianoanchor.feature.onboarding.resources.onboarding_connect_searching
import pianoanchor.feature.onboarding.resources.onboarding_connect_title
import pianoanchor.feature.onboarding.resources.onboarding_continue
import pianoanchor.feature.onboarding.resources.onboarding_echo_any_octave
import pianoanchor.feature.onboarding.resources.onboarding_echo_listen
import pianoanchor.feature.onboarding.resources.onboarding_echo_miss
import pianoanchor.feature.onboarding.resources.onboarding_echo_play
import pianoanchor.feature.onboarding.resources.onboarding_echo_replay
import pianoanchor.feature.onboarding.resources.onboarding_echo_skip
import pianoanchor.feature.onboarding.resources.onboarding_echo_success
import pianoanchor.feature.onboarding.resources.onboarding_echo_title
import pianoanchor.feature.onboarding.resources.onboarding_key_c
import pianoanchor.feature.onboarding.resources.onboarding_key_d
import pianoanchor.feature.onboarding.resources.onboarding_key_e
import pianoanchor.feature.onboarding.resources.onboarding_key_f
import pianoanchor.feature.onboarding.resources.onboarding_key_g
import pianoanchor.feature.onboarding.resources.onboarding_key_highlighted
import pianoanchor.feature.onboarding.resources.onboarding_start_beginning
import pianoanchor.feature.onboarding.resources.onboarding_start_beginning_summary
import pianoanchor.feature.onboarding.resources.onboarding_start_can_play
import pianoanchor.feature.onboarding.resources.onboarding_start_error
import pianoanchor.feature.onboarding.resources.onboarding_start_title
import pianoanchor.feature.onboarding.resources.onboarding_start_unit
import pianoanchor.feature.onboarding.resources.onboarding_start_unit_1_summary
import pianoanchor.feature.onboarding.resources.onboarding_start_unit_2_summary
import pianoanchor.feature.onboarding.resources.onboarding_step
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

private val TouchTarget = 44.dp
private val PillShape = RoundedCornerShape(22.dp)
private const val STEP_COUNT = 4

/** The keys the echo strip draws: C to G, with the chord's three (C, E, G) highlighted. */
private val KEYS =
    listOf(
        Res.string.onboarding_key_c to true,
        Res.string.onboarding_key_d to false,
        Res.string.onboarding_key_e to true,
        Res.string.onboarding_key_f to false,
        Res.string.onboarding_key_g to true,
    )

/**
 * First-launch onboarding (Living Studio, `docs/ui/living-studio.md`): one step at a time, as [state] says: press a
 * key, echo C-E-G, calibrate (the [calibration] slot hosts the calibration screen for the connected piano) and choose
 * the start point. Stateless: see [OnboardingViewModel].
 */
@Composable
public fun OnboardingScreen(
    state: OnboardingState,
    actions: OnboardingActions,
    calibration: @Composable (MidiDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = state.device
    if (state.step == OnboardingStep.CALIBRATE && device != null) {
        calibration(device)
        return
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(Res.string.onboarding_step, (state.step.ordinal + 1).toString(), STEP_COUNT.toString()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state.step) {
                OnboardingStep.CONNECT -> Connect(state, actions)
                OnboardingStep.ECHO -> Echo(state, actions)
                OnboardingStep.START_POINT -> StartPoint(state, actions)
                OnboardingStep.CALIBRATE -> Unit // Calibrating without a piano can't happen: the slot shows it.
            }
        }
    }
}

@Composable
private fun Title(text: StringResource) {
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
private fun ConnectionLine(device: MidiDevice?) {
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
private fun Connect(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    Title(Res.string.onboarding_connect_title)
    ConnectionLine(state.device ?: state.listed)
    if (state.pianoListHint) {
        TextButton(onClick = actions.onOpenPianoList, modifier = Modifier.heightIn(min = TouchTarget)) {
            Text(stringResource(Res.string.onboarding_connect_not_found))
        }
    }
}

@Composable
private fun Echo(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    Title(Res.string.onboarding_echo_title)
    ConnectionLine(state.device)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            KeyStrip(state.keysHighlighted)
            Text(
                stringResource(echoMessage(state)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (state.echo == EchoPhase.LISTENING) {
                Text(
                    stringResource(Res.string.onboarding_echo_any_octave),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EchoButtons(state, actions)
        }
    }
}

private fun echoMessage(state: OnboardingState): StringResource =
    when (state.echo) {
        EchoPhase.PLAYING -> Res.string.onboarding_echo_listen
        EchoPhase.LISTENING -> Res.string.onboarding_echo_play
        EchoPhase.MISSED -> Res.string.onboarding_echo_miss
        EchoPhase.PASSED -> Res.string.onboarding_echo_success
    }

@Composable
private fun EchoButtons(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.echo == EchoPhase.PASSED) {
            PillButton(stringResource(Res.string.onboarding_continue), actions.onContinue)
        }
        if (state.echo == EchoPhase.MISSED) {
            PillButton(stringResource(Res.string.onboarding_echo_replay), actions.onReplay)
        }
        if (state.canSkipEcho && state.echo != EchoPhase.PASSED) {
            OutlinedButton(
                onClick = actions.onContinue,
                modifier = Modifier.height(TouchTarget),
                shape = PillShape,
            ) {
                Text(
                    stringResource(Res.string.onboarding_echo_skip),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Five keys, C to G, with C, E and G in the primary colour; read as prompts when the piano can't play them. */
@Composable
private fun KeyStrip(promptKeys: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, inChord) in KEYS) {
            val name = stringResource(label)
            val colors = MaterialTheme.colorScheme
            val described =
                if (inChord && promptKeys) stringResource(Res.string.onboarding_key_highlighted, name) else name
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(88.dp)
                        .background(
                            if (inChord) colors.primaryContainer else colors.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        ).padding(bottom = 8.dp)
                        .semantics { contentDescription = described },
            ) {
                Text(
                    name,
                    fontWeight = FontWeight.Bold,
                    color = if (inChord) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun StartPoint(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    Title(Res.string.onboarding_start_title)
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
    val beginning = Curriculum.units.first().title
    ChoiceCard(
        stringResource(Res.string.onboarding_start_beginning),
        stringResource(Res.string.onboarding_start_beginning_summary, beginning),
        primary = true,
    ) { actions.onStart(StartPoint.BEGINNING) }
    Text(
        stringResource(Res.string.onboarding_start_can_play),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    for (
    (point, summary) in
    listOf(
        StartPoint.UNIT_1 to Res.string.onboarding_start_unit_1_summary,
        StartPoint.UNIT_2 to Res.string.onboarding_start_unit_2_summary,
    )
    ) {
        val unit = point.unitsPlaced
        ChoiceCard(
            stringResource(Res.string.onboarding_start_unit, unit.toString(), Curriculum.units[unit].title),
            stringResource(summary),
            primary = false,
        ) { actions.onStart(point) }
    }
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
private fun PillButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.height(TouchTarget), shape = PillShape) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
