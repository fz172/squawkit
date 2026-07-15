package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListUiState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.done
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.feature.logs.sharedassets.generated.resources.add_first_maintenance_log
import wingslog.feature.logs.sharedassets.generated.resources.no_maintenance_logs_description
import wingslog.feature.logs.sharedassets.generated.resources.no_maintenance_logs_title
import wingslog.feature.logs.viewing.generated.resources.clear_filter
import wingslog.feature.logs.viewing.generated.resources.failed_to_load_logs
import wingslog.feature.logs.viewing.generated.resources.filter_by_type
import wingslog.feature.logs.viewing.generated.resources.log_count_n_entries
import wingslog.feature.logs.viewing.generated.resources.log_count_one_entry
import wingslog.feature.logs.viewing.generated.resources.no_logs_match_filter
import wingslog.feature.logs.viewing.generated.resources.search_logs
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogListContent(
  uiState: MaintenanceLogListUiState,
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  onSearchQueryChange: (String) -> Unit,
  onComponentFilterToggle: (ComponentType) -> Unit,
  onClearFilter: () -> Unit,
  onRetry: () -> Unit,
  onLogClick: (MaintenanceLog) -> Unit,
  onDismissDetail: () -> Unit,
  onEditLog: ((String) -> Unit)?,
  onAddLog: (() -> Unit)?,
  onAttachmentTap: (Attachment) -> Unit,
  openError: String? = null,
  onTaskClick: ((String) -> Unit)? = null,
  /**
   * When set, scroll the list to the log with this id (a jump from a squawk's work history). The
   * caller must not toggle this back to null while the Logs tab is on screen — doing so remounts this
   * tab and drops its scroll position; it should be cleared only once the section is left.
   */
  scrollToLogId: String? = null,
  modifier: Modifier = Modifier,
) {
  // Hoisted above the when(uiState) so it is one stable instance across Loading→Success flips and is
  // shared by both the compact card list and the wide table (only one is composed at a time). A jump
  // from a squawk's work history can then scroll whichever layout is on screen.
  val logListState = rememberLazyListState()

  // Jump-to-log: pin the requested log and hold it through the tab's load churn. Right after the tab
  // opens the logs list can re-emit empty for a frame (the auth state re-settles, briefly nulling the
  // storage scope), which clamps the list to the top; re-asserting the scroll on every emission puts
  // it back on the target. We hold until the user grabs the list (a real drag, never our own
  // programmatic scroll) or an absolute cap elapses, then stop — we never clear the request here,
  // because that has to happen at the section level while this tab is off screen.
  val currentLogs by rememberUpdatedState(
    (uiState as? MaintenanceLogListUiState.Success)?.logs.orEmpty()
  )
  LaunchedEffect(scrollToLogId) {
    if (scrollToLogId == null) return@LaunchedEffect
    coroutineScope {
      val pinning = launch {
        snapshotFlow { currentLogs }.collect { logs ->
          val index = logs.indexOfFirst { it.id == scrollToLogId }
          if (index >= 0) logListState.scrollToItem(index)
        }
      }
      withTimeoutOrNull(8000) {
        logListState.interactionSource.interactions.first { it is DragInteraction.Start }
      }
      pinning.cancel()
    }
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    when (uiState) {
      MaintenanceLogListUiState.Loading -> CircularProgressIndicator()

      MaintenanceLogListUiState.Error -> Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.large)
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
        var showFilterSheet by remember { mutableStateOf(false) }

        if (uiState.totalCount == 0) {
          EmptyState(
            title = stringResource(SharedRes.string.no_maintenance_logs_title),
            description = stringResource(SharedRes.string.no_maintenance_logs_description),
            icon = Icons.Default.History,
            actionText = onAddLog?.let { stringResource(SharedRes.string.add_first_maintenance_log) },
            onActionClick = onAddLog
          )
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            // Search bar + filter button
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
              horizontalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
              val filterActive = uiState.filter.components.isNotEmpty()
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
                shape = RoundedCornerShape(Spacing.smallCornerRadius),
                colors = OutlinedTextFieldDefaults.colors(
                  unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                  focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
              )

              Surface(
                onClick = { showFilterSheet = true },
                shape = RoundedCornerShape(Spacing.smallCornerRadius),
                color = if (filterActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                  Spacing.hairline,
                  if (filterActive) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.size(Spacing.buttonHeight),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Default.FilterList,
                    contentDescription = stringResource(MaintenanceRes.string.filter_by_type),
                    tint = if (filterActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            // Active filter chips
            if (uiState.filter.components.isNotEmpty()) {
              LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                modifier = Modifier.fillMaxWidth()
                  .padding(bottom = Spacing.small),
              ) {
                items(uiState.filter.components.toList()) { component ->
                  ActiveFilterChip(
                    label = component.displayName(),
                    onDismiss = { onComponentFilterToggle(component) },
                  )
                }
              }
            }

            // Entry count label
            val count = uiState.logs.size
            val countLabel =
              if (count == 1) stringResource(MaintenanceRes.string.log_count_one_entry)
              else stringResource(
                MaintenanceRes.string.log_count_n_entries,
                count
              )
            Text(
              text = countLabel.uppercase(),
              style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(
                horizontal = Spacing.screenPadding,
                vertical = Spacing.extraSmall
              ),
            )

            if (uiState.logs.isEmpty()) {
              Box(
                modifier = Modifier.weight(1f)
                  .fillMaxWidth(),
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
            } else if (LocalLayoutTier.current.hasSideNav) {
              // MEDIUM and wider: a real data table instead of cards.
              MaintenanceLogTable(
                logs = uiState.logs,
                onLogClick = onLogClick,
                listState = logListState,
                modifier = Modifier
                  // fill = false so the bordered table wraps its content height when there are
                  // few entries instead of stretching to fill the whole viewport; it still caps
                  // at the available space and scrolls internally once there are enough rows.
                  .weight(1f, fill = false)
                  .fillMaxWidth()
                  .padding(horizontal = Spacing.screenPadding),
              )
            } else {
              LazyColumn(
                state = logListState,
                modifier = Modifier.weight(1f)
                  .fillMaxWidth(),
                contentPadding = PaddingValues(
                  start = Spacing.screenPadding,
                  end = Spacing.screenPadding,
                  top = Spacing.small,
                  bottom = Spacing.buttonHeight + Spacing.extraLarge
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
              ) {
                items(
                  uiState.logs,
                  key = { it.id }) { log ->
                  MaintenanceLogCard(
                    log = log,
                    onClick = { onLogClick(log) },
                  )
                }
              }
            }

            uiState.selectedLog?.let { log ->
              MaintenanceLogDetailSheet(
                log = log,
                availableCards = uiState.availableCards,
                attachmentsUnavailable = uiState.attachmentsUnavailable,
                onDismiss = onDismissDetail,
                authorship = uiState.selectedAuthorship,
                onEditClick = onEditLog?.let { edit ->
                  {
                    onDismissDetail()
                    edit(log.id)
                  }
                },
                onAttachmentTap = onAttachmentTap,
                syncStates = syncStates,
                openError = openError,
                onTaskClick = onTaskClick?.let { cb ->
                  { taskId ->
                    onDismissDetail()
                    cb(taskId)
                  }
                },
              )
            }
          }
        }

        if (showFilterSheet) {
          ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
          ) {
            FilterSheetContent(
              activeComponents = uiState.filter.components,
              onToggle = onComponentFilterToggle,
              onDone = { showFilterSheet = false },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ActiveFilterChip(
  label: String,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(Spacing.chipCornerRadius),
    color = MaterialTheme.colorScheme.primaryContainer,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.primary
    ),
  ) {
    Row(
      modifier = Modifier.padding(
        start = Spacing.small,
        end = Spacing.extraSmall,
        top = Spacing.extraSmall,
        bottom = Spacing.extraSmall
      ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(Spacing.large),
      ) {
        Icon(
          Icons.Default.Close,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(Spacing.small),
        )
      }
    }
  }
}

@Composable
private fun FilterSheetContent(
  activeComponents: Set<ComponentType>,
  onToggle: (ComponentType) -> Unit,
  onDone: () -> Unit,
) {
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          start = Spacing.xLarge,
          end = Spacing.medium,
          bottom = Spacing.medium
        ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(MaintenanceRes.string.filter_by_type),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onDone) {
        Text(stringResource(CoreRes.string.done))
      }
    }

    val filterTypes = listOf(
      ComponentType.COMPONENT_AIRFRAME,
      ComponentType.COMPONENT_ENGINE,
      ComponentType.COMPONENT_PROPELLER,
      ComponentType.COMPONENT_UNKNOWN,
    )
    filterTypes.forEachIndexed { index, type ->
      FilterTypeRow(
        type = type,
        checked = type in activeComponents,
        onClick = { onToggle(type) },
      )
      if (index < filterTypes.lastIndex) {
        HorizontalDivider(
          color = MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.padding(horizontal = Spacing.xLarge),
        )
      }
    }

    Spacer(Modifier.height(Spacing.huge))
  }
}

@Composable
private fun FilterTypeRow(
  type: ComponentType,
  checked: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(
        horizontal = Spacing.xLarge,
        vertical = Spacing.medium
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ComponentTypeBadge(type)
    Spacer(Modifier.weight(1f))
    Box(
      modifier = Modifier
        .size(Spacing.xLarge)
        .then(
          if (checked) Modifier else Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(Spacing.extraSmall),
          )
        )
        .then(
          if (checked) Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(Spacing.extraSmall),
          ) else Modifier
        ),
      contentAlignment = Alignment.Center,
    ) {
      if (checked) {
        Surface(
          modifier = Modifier.size(Spacing.xLarge),
          color = MaterialTheme.colorScheme.primary,
          shape = RoundedCornerShape(Spacing.extraSmall),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Default.Check,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(Spacing.medium),
            )
          }
        }
      }
    }
  }
}
