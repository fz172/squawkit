package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.AircraftDataCard
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.LogOnboardingCard
import dev.fanfly.wingslog.feature.aircraft.dashboard.compose.LogStatsSection
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.squawk.viewing.AogAlertSection
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.viewing.CriticalAlertsSection
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.make_model_template
import wingslog.core.ui.generated.resources.Res as CoreRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
  state: AircraftOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
  onViewSquawksTab: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
  ) {
    FlowRow(
      modifier = Modifier
        .padding(horizontal = Spacing.screenPadding)
        .padding(top = Spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Text(
        text = stringResource(
          CoreRes.string.make_model_template,
          state.aircraft.make.trim(),
          state.aircraft.model.trim()
        ),
        style = WingslogTypography.heroDisplay,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = state.aircraft.tail_number,
        style = WingslogTypography.heroDisplay,
        color = MaterialTheme.colorScheme.primary
      )
    }

    val overdueTasks =
      state.activeTasks.filter { it.dueStatus.status == DueStatus.OVERDUE || it.dueStatus.status == DueStatus.DUE_SOON }

    Column(modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
      AircraftDataCard(
        state.aircraft,
        initiallyExpanded = overdueTasks.isEmpty()
      )
    }

    if (state.aogSquawks.isNotEmpty()) {
      AogAlertSection(
        aogSquawks = state.aogSquawks,
        onViewSquawksClick = onViewSquawksTab,
        modifier = Modifier.padding(horizontal = Spacing.screenPadding),
      )
    }

    if (overdueTasks.isNotEmpty()) {
      CriticalAlertsSection(
        overdueTasks = overdueTasks,
        onCardClick = { onAction(AircraftOverviewAction.TaskCardClick(it)) },
        modifier = Modifier.padding(horizontal = Spacing.screenPadding)
      )
    }

    state.logStats?.let { stats ->
      if (stats.total == 0L) {
        LogOnboardingCard(
          onAddLogClick = { onAction(AircraftOverviewAction.AddLogClick(state.aircraft.id)) },
          modifier = Modifier.padding(horizontal = Spacing.screenPadding)
        )
      } else {
        LogStatsSection(
          stats = stats,
          modifier = Modifier.padding(horizontal = Spacing.screenPadding)
        )
      }
    }

    Spacer(Modifier.height(Spacing.screenPadding))
  }
}
