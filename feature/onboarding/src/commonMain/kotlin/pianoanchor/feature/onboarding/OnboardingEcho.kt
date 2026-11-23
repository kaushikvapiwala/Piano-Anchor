package pianoanchor.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.onboarding.resources.Res
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

/** The keys the echo strip draws: C to G, with the chord's three (C, E, G) highlighted. */
private val KEYS =
    listOf(
        Res.string.onboarding_key_c to true,
        Res.string.onboarding_key_d to false,
        Res.string.onboarding_key_e to true,
        Res.string.onboarding_key_f to false,
        Res.string.onboarding_key_g to true,
    )

@Composable
internal fun EchoLeft(state: OnboardingState) {
    Title(Res.string.onboarding_echo_title)
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
        }
    }
}

@Composable
internal fun EchoRight(
    state: OnboardingState,
    actions: OnboardingActions,
) {
    ConnectionLine(state.device)
    EchoButtons(state, actions)
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
