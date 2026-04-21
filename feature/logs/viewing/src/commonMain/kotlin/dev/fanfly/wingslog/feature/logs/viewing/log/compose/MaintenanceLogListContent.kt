package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import wingslog.core.ui.generated.resources.retry
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.sharedassets.generated.resources.add_first_maintenance_log
import wingslog.feature.logs.sharedassets.generated.resources.no_maintenance_logs_description
import wingslog.feature.logs.sharedassets.generated.resources.no_maintenance_logs_title
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.logs.viewing.generated.resources.clear_filter
import wingslog.feature.logs.viewing.generated.resources.failed_to_load_logs
import wingslog.feature.logs.viewing.generated.resources.filter_by_component
import wingslog.feature.logs.viewing.generated.resources.no_logs_match_filter
import wingslog.feature.logs.viewing.generated.resources.search_logs
import wingslog.feature.logs.viewing.generated.resources.showing_x_of_y

@Composable
fun MaintenanceLogListContent(
  uiState: MaintenanceLogListUiState,
  downloadingIds: Set<String>,
  onSearchQueryChange: (String) -> Unit,
  onComponentFilterToggle: (ComponentType) -> Unit,
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
    contentAlignment = Alignment.Center,
  ) {
    when (uiState) {
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
        if (uiState.totalCount == 0) {
          EmptyState(
            title = stringResource(SharedRes.string.no_maintenance_logs_title),
            description = stringResource(SharedRes.string.no_maintenance_logs_description),
            icon = Icons.Default.History,
            actionText = stringResource(SharedRes.string.add_first_maintenance_log),
            onActionClick = onAddLog
          )
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(
                  start = Spacing.screenPadding,
                  end = Spacing.small,
                  top = Spacing.small,
                  bottom = Spacing.small
                ),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = uiState.filter.query,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(MaintenanceRes.string.search_logs)) },
                leadingIcon = {
                  Icon(
                    Icons.Default.Search,
                    contentDescription = null
                  )
                },
                trailingIcon = {
                  if (uiState.filter.query.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                      Icon(
                        Icons.Default.Close,
                        contentDescription = null
                      )
                    }
                  }
                },
                singleLine = true,
              )

              var showFilterMenu by remember { mutableStateOf(false) }
              val filterLabel = stringResource(MaintenanceRes.string.filter_by_component)
              val activeCount = uiState.filter.components.size

              Box {
                BadgedBox(
                  badge = {
                    if (activeCount > 0) Badge { Text(activeCount.toString()) }
                  }
                ) {
                  IconButton(
                    onClick = { showFilterMenu = true },
                  ) {
                    Icon(
                      Icons.Default.FilterList,
                      contentDescription = filterLabel,
                      tint = if (activeCount > 0) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                val components = listOf(
                  ComponentType.COMPONENT_AIRFRAME,
                  ComponentType.COMPONENT_ENGINE,
                  ComponentType.COMPONENT_PROPELLER,
                  ComponentType.COMPONENT_AVIONICS,
                )
                DropdownMenu(
                  expanded = showFilterMenu,
                  onDismissRequest = { showFilterMenu = false },
                ) {
                  components.forEach { component ->
                    val checked = component in uiState.filter.components
                    DropdownMenuItem(
                      text = { Text(component.displayName()) },
                      onClick = { onComponentFilterToggle(component) },
                      leadingIcon = {
                        Checkbox(
                          checked = checked,
                          onCheckedChange = null,
                        )
                      },
                    )
                  }
                }
              }
            }

            if (uiState.filter.isActive) {
              Text(
                text = stringResource(
                  MaintenanceRes.string.showing_x_of_y,
                  uiState.logs.size,
                  uiState.totalCount
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                  horizontal = Spacing.screenPadding,
                  vertical = Spacing.tiny
                )
              )
            }

            if (uiState.logs.isEmpty()) {
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
                items(
                  uiState.logs,
                  key = { it.id }) { log ->
                  MaintenanceLogCard(
                    log = log,
                    onClick = { onLogClick(log) })
                }
              }
            }

            uiState.selectedLog?.let { log ->
              MaintenanceLogDetailSheet(
                log = log,
                availableCards = uiState.availableCards,
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
