package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.ui.common.compose.DualSegmentedFilter
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkCard
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkDetailSheet
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.add_squawk
import wingslog.feature.squawk.sharedassets.generated.resources.closed_with_count
import wingslog.feature.squawk.sharedassets.generated.resources.no_closed_squawks
import wingslog.feature.squawk.sharedassets.generated.resources.no_open_squawks
import wingslog.feature.squawk.sharedassets.generated.resources.open_with_count
import wingslog.feature.squawk.sharedassets.generated.resources.squawks

private val priorityOrder = compareByDescending<SquawkWithStatus> {
  when (it.squawk.priority) {
    SquawkPriority.SQUAWK_PRIORITY_AOG    -> 4
    SquawkPriority.SQUAWK_PRIORITY_HIGH   -> 3
    SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> 2
    SquawkPriority.SQUAWK_PRIORITY_LOW    -> 1
    else                                   -> 0
  }
}

@Composable
fun SquawkTab(
  state: AircraftOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showClosed by rememberSaveable { mutableStateOf(false) }

  val openSquawks = state.squawks
    .filter { it.status == SquawkStatus.OPEN }
    .sortedWith(priorityOrder)
  val closedSquawks = state.squawks
    .filter { it.status == SquawkStatus.ADDRESSED || it.status == SquawkStatus.DISMISSED }
    .sortedByDescending { it.squawk.created_at?.getEpochSecond() ?: 0L }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Spacing.screenPadding),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Spacer(Modifier.height(Spacing.medium))

    Text(
      text = stringResource(Res.string.squawks),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )

    DualSegmentedFilter(
      option1 = stringResource(Res.string.open_with_count, openSquawks.size),
      option2 = stringResource(Res.string.closed_with_count, closedSquawks.size),
      selectedIndex = if (showClosed) 1 else 0,
      onSelect = { showClosed = it == 1 },
    )

    val displayList = if (showClosed) closedSquawks else openSquawks

    if (displayList.isEmpty()) {
      if (!showClosed) {
        OpenEmptyState(
          onAddClick = { onAction(AircraftOverviewAction.AddSquawkClick(state.aircraft.id)) }
        )
      } else {
        Text(
          text = stringResource(Res.string.no_closed_squawks),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large),
        )
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        displayList.forEach { item ->
          SquawkCard(
            item = item,
            onClick = { onAction(AircraftOverviewAction.ShowSquawkDetail(item)) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    Spacer(Modifier.height(Spacing.buttonHeight + Spacing.screenPadding))
  }

  state.selectedSquawk?.let { selected ->
    SquawkDetailSheet(
      item = selected,
      addressingLog = state.logForSelectedSquawk,
      onDismiss = { onAction(AircraftOverviewAction.DismissSquawkDetail) },
      onEditClick = {
        onAction(AircraftOverviewAction.DismissSquawkDetail)
        onAction(AircraftOverviewAction.EditSquawkClick(state.aircraft.id, selected.squawk.id))
      },
    )
  }
}

@Composable
private fun OpenEmptyState(onAddClick: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = stringResource(Res.string.no_open_squawks),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      OutlinedButton(
        onClick = onAddClick,
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.small))
        Text(stringResource(Res.string.add_squawk).uppercase())
      }
    }
  }
}
