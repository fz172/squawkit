package dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.compose.ComplianceSection
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.data.AircraftOverviewUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.inspection.sharedassets.generated.resources.add_inspection
import wingslog.feature.inspection.sharedassets.generated.resources.Res as InspectionRes

@Composable
fun MaintenanceTasksTab(
  state: AircraftOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  var showComplied by rememberSaveable { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)
    ) {
      Spacer(Modifier.height(Spacing.medium))

      ComplianceSection(
        activeInspections = state.activeInspections,
        compliedInspections = state.compliedInspections,
        showComplied = showComplied,
        onToggleComplied = { showComplied = it },
        onAddClick = { onAction(AircraftOverviewAction.AddInspectionClick(state.aircraft.id)) },
        onCardClick = { onAction(AircraftOverviewAction.InspectionCardClick(it)) },
      )

      Spacer(Modifier.height(Spacing.buttonHeight + Spacing.screenPadding))
    }

    ExtendedFloatingActionButton(
      onClick = { onAction(AircraftOverviewAction.AddInspectionClick(state.aircraft.id)) },
      icon = { Icon(Icons.Default.Add, contentDescription = null) },
      text = { Text(stringResource(InspectionRes.string.add_inspection)) },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(Spacing.screenPadding)
    )
  }
}
