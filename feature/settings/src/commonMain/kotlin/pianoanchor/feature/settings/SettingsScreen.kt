package pianoanchor.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Strictness
import pianoanchor.core.model.WEEKLY_GOAL_DAYS_RANGE
import pianoanchor.feature.settings.resources.Res
import pianoanchor.feature.settings.resources.settings_action_dismiss
import pianoanchor.feature.settings.resources.settings_back
import pianoanchor.feature.settings.resources.settings_back_glyph
import pianoanchor.feature.settings.resources.settings_budget_label
import pianoanchor.feature.settings.resources.settings_budget_option
import pianoanchor.feature.settings.resources.settings_click
import pianoanchor.feature.settings.resources.settings_daily_budget
import pianoanchor.feature.settings.resources.settings_error_storage
import pianoanchor.feature.settings.resources.settings_group_playing
import pianoanchor.feature.settings.resources.settings_group_practice
import pianoanchor.feature.settings.resources.settings_group_updates
import pianoanchor.feature.settings.resources.settings_strictness
import pianoanchor.feature.settings.resources.settings_strictness_gentle
import pianoanchor.feature.settings.resources.settings_strictness_gentle_note
import pianoanchor.feature.settings.resources.settings_strictness_standard
import pianoanchor.feature.settings.resources.settings_strictness_standard_note
import pianoanchor.feature.settings.resources.settings_strictness_strict
import pianoanchor.feature.settings.resources.settings_strictness_strict_note
import pianoanchor.feature.settings.resources.settings_title
import pianoanchor.feature.settings.resources.settings_update_check
import pianoanchor.feature.settings.resources.settings_update_check_note
import pianoanchor.feature.settings.resources.settings_weekly_goal
import pianoanchor.feature.settings.resources.settings_weekly_goal_days
import pianoanchor.feature.settings.resources.settings_weekly_goal_fewer
import pianoanchor.feature.settings.resources.settings_weekly_goal_fewer_glyph
import pianoanchor.feature.settings.resources.settings_weekly_goal_more
import pianoanchor.feature.settings.resources.settings_weekly_goal_more_glyph

private val TouchTarget = 44.dp

/** The widest the settings column gets, so rows stay readable on a wide landscape screen. */
private val MaxContentWidth = 600.dp

/**
 * The settings, landscape: grouped rows for the daily budget, weekly goal, click, strictness and update check.
 * Stateless.
 */
@Composable
public fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        // Landscape: one scrolling column, no wider than MaxContentWidth, centred.
        Box(modifier = Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                TopBar(actions.onBack)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.storageFailed) ErrorCard(actions.onDismissError)
                    GroupTitle(Res.string.settings_group_practice)
                    BudgetRow(state.settings.dailyBudget, actions.onDailyBudget)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    WeeklyGoalRow(state.settings.weeklyGoalDays, actions.onWeeklyGoalStep)
                    GroupTitle(Res.string.settings_group_playing)
                    SwitchRow(Res.string.settings_click, null, state.settings.clickOn, actions.onClickOn)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StrictnessRows(state.settings.strictness, actions.onStrictness)
                    GroupTitle(Res.string.settings_group_updates)
                    SwitchRow(
                        Res.string.settings_update_check,
                        Res.string.settings_update_check_note,
                        state.settings.updateCheckOn,
                        actions.onUpdateCheckOn,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    val backLabel = stringResource(Res.string.settings_back)
    Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.size(TouchTarget).semantics { contentDescription = backLabel },
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        ) {
            Text(
                stringResource(Res.string.settings_back_glyph),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun GroupTitle(text: StringResource) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun BudgetRow(
    selected: DailyBudget,
    onSelect: (DailyBudget) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(stringResource(Res.string.settings_daily_budget), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DailyBudget.entries.forEachIndexed { index, budget ->
                val description = stringResource(Res.string.settings_budget_option, budget.minutes.toString())
                SegmentedButton(
                    selected = budget == selected,
                    onClick = { onSelect(budget) },
                    shape = SegmentedButtonDefaults.itemShape(index, DailyBudget.entries.size),
                    modifier = Modifier.heightIn(min = TouchTarget).semantics { contentDescription = description },
                ) {
                    Text(stringResource(Res.string.settings_budget_label, budget.minutes.toString()))
                }
            }
        }
    }
}

@Composable
private fun WeeklyGoalRow(
    days: Int,
    onStep: (Int) -> Unit,
) {
    val fewer = stringResource(Res.string.settings_weekly_goal_fewer)
    val more = stringResource(Res.string.settings_weekly_goal_more)
    val value = pluralStringResource(Res.plurals.settings_weekly_goal_days, days, days.toString())
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.settings_weekly_goal), style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepperButton(Res.string.settings_weekly_goal_fewer_glyph, fewer, days > WEEKLY_GOAL_DAYS_RANGE.first) {
            onStep(-1)
        }
        StepperButton(Res.string.settings_weekly_goal_more_glyph, more, days < WEEKLY_GOAL_DAYS_RANGE.last) {
            onStep(1)
        }
    }
}

@Composable
private fun StepperButton(
    glyph: StringResource,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(TouchTarget).semantics { contentDescription = label },
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
    ) {
        Text(stringResource(glyph), fontSize = 20.sp)
    }
}

/** A row with a [Switch]: the whole row toggles, and TalkBack reads its [label] with the state. */
@Composable
private fun SwitchRow(
    label: StringResource,
    note: StringResource?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            note?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The strictness choice: one selectable row per preset with its one-line description. */
@Composable
private fun StrictnessRows(
    selected: Strictness,
    onSelect: (Strictness) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup().padding(vertical = 4.dp)) {
        Text(stringResource(Res.string.settings_strictness), style = MaterialTheme.typography.bodyLarge)
        Strictness.entries.forEach { strictness ->
            val (title, note) =
                when (strictness) {
                    Strictness.GENTLE -> {
                        Res.string.settings_strictness_gentle to Res.string.settings_strictness_gentle_note
                    }

                    Strictness.STANDARD -> {
                        Res.string.settings_strictness_standard to Res.string.settings_strictness_standard_note
                    }

                    Strictness.STRICT -> {
                        Res.string.settings_strictness_strict to Res.string.settings_strictness_strict_note
                    }
                }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget)
                        .selectable(
                            selected = strictness == selected,
                            onClick = { onSelect(strictness) },
                            role = Role.RadioButton,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = strictness == selected, onClick = null)
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(onDismiss: () -> Unit) {
    val contentColor = MaterialTheme.colorScheme.onErrorContainer
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = contentColor,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Text(stringResource(Res.string.settings_error_storage), modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = contentColor)) {
                Text(stringResource(Res.string.settings_action_dismiss))
            }
        }
    }
}
