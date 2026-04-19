package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.all
import wingslog.core.ui.generated.resources.retry
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.sharedassets.generated.resources.add_first_maintenance_log
import wingslog.feature.logs.sharedassets.generated.resources.no_maintenance_logs_description
import wingslog.feature.logs.sharedassets.generated.resources.no_maintenance_logs_title
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.logs.viewing.generated.resources.clear_filter
import wingslog.feature.logs.viewing.generated.resources.failed_to_load_logs
import wingslog.feature.logs.viewing.generated.resources.no_logs_match_filter
import wingslog.feature.logs.viewing.generated.resources.search_logs
import wingslog.feature.logs.viewing.generated.resources.showing_x_of_y

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogListContent(
  uiState: MaintenanceLogListUiState,
  downloadingIds: Set<String>,
  onSearchQueryChange: (String) -> Unit,
  onComponentFilterChange: (ComponentType?) -> Unit,
  onClearFilter: () -> Unit,
  onRetry: () -> Unit,
  onLogClick: (MaintenanceLog) -> Unit,
  onDismissDetail: () -> Unit,
  onEditLog: (String) -> Unit,
  onAddLog: () -> Unit,
  onAttachmentTap: (Attachment) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    when (val state = uiState) {
      MaintenanceLogListUiState.Loading -> CircularProgressIndicator()

      MaintenanceLogListUiState.Error -> Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          stringResource(MaintenanceRes.string.failed_to_load_logs),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRetry) {
          Text(stringResource(CoreRes.string.retry))
        }
      }

      is MaintenanceLogListUiState.Success -> {
        if (state.totalCount == 0) {
          EmptyState(
            title = stringResource(SharedRes.string.no_maintenance_logs_title),
            description = stringResource(SharedRes.string.no_maintenance_logs_description),
            icon = Icons.Default.History,
            actionText = stringResource(SharedRes.string.add_first_maintenance_log),
            onActionClick = onAddLog
          )
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
              value = state.filter.query,
              onValueChange = onSearchQueryChange,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenPadding, vertical = Spacing.small),
              placeholder = { Text(stringResource(MaintenanceRes.string.search_logs)) },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
              trailingIcon = {
                if (state.filter.query.isNotBlank()) {
                  IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = null)
                  }
                }
              },
              singleLine = true,
            )

            val components = listOf(
              null,
              ComponentType.COMPONENT_AIRFRAME,
              ComponentType.COMPONENT_ENGINE,
              ComponentType.COMPONENT_PROPELLER,
              ComponentType.COMPONENT_AVIONICS,
            )
            SingleChoiceSegmentedButtonRow(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenPadding),
            ) {
              components.forEachIndexed { index, component ->
                val label = component?.displayName() ?: stringResource(CoreRes.string.all)
                SegmentedButton(
                  selected = state.filter.component == component,
                  onClick = { onComponentFilterChange(component) },
                  shape = SegmentedButtonDefaults.itemShape(index = index, count = components.size),
                  icon = {},
                  label = { Text(label) },
                )
              }
            }

            if (state.filter.isActive) {
              Text(
                text = stringResource(
                  MaintenanceRes.string.showing_x_of_y,
                  state.logs.size,
                  state.totalCount
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                  horizontal = Spacing.screenPadding,
                  vertical = Spacing.tiny
                )
              )
            }

            if (state.logs.isEmpty()) {
              Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                  Text(
                    text = stringResource(MaintenanceRes.string.no_logs_match_filter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Button(onClick = onClearFilter) {
                    Text(stringResource(MaintenanceRes.string.clear_filter))
                  }
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                  start = Spacing.screenPadding,
                  end = Spacing.screenPadding,
                  top = Spacing.small,
                  bottom = 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                items(state.logs, key = { it.id }) { log ->
                  MaintenanceLogCard(
                    log = log,
                    onClick = { onLogClick(log) }
                  )
                }
              }
            }

            state.selectedLog?.let { log ->
              MaintenanceLogDetailSheet(
                log = log,
                availableCards = state.availableCards,
                onDismiss = onDismissDetail,
                onEditClick = {
                  onDismissDetail()
                  onEditLog(log.id)
                },
                onAttachmentTap = onAttachmentTap,
                downloadingIds = downloadingIds,
              )
            }
          }
        }
      }
    }
  }
}
