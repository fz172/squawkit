package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.tabs

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
import dev.fanfly.wingslog.feature.inspection.model.DueStatus
import dev.fanfly.wingslog.feature.inspection.viewing.CriticalAlertsSection
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.ConfigurationCard
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.LogOnboardingCard
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.LogStatsSection
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.maintenance.sharedassets.generated.resources.make_model_template
import wingslog.feature.maintenance.sharedassets.generated.resources.Res

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
  state: AircraftOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
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
        text = stringResource(Res.string.make_model_template, state.aircraft.make, state.aircraft.model),
        style = WingslogTypography.heroDisplay,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = state.aircraft.tail_number,
        style = WingslogTypography.heroDisplay,
        color = MaterialTheme.colorScheme.primary
      )
    }

    Column(modifier = Modifier.padding(horizontal = Spacing.screenPadding)) {
      ConfigurationCard(state.aircraft)
    }

    val overdueInspections = state.activeInspections.filter { it.dueStatus.status == DueStatus.OVERDUE }
    if (overdueInspections.isNotEmpty()) {
      CriticalAlertsSection(
        overdueInspections = overdueInspections,
        onCardClick = { onAction(AircraftOverviewAction.InspectionCardClick(it)) },
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
