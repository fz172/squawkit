package dev.fanfly.wingslog.feature.logs.viewing.list

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.logEmptyHint
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.ui.adaptive.listdetail.ListDetailSection
import dev.fanfly.wingslog.core.ui.list.EmptyState
import dev.fanfly.wingslog.core.ui.list.SkeletonList
import dev.fanfly.wingslog.core.ui.list.animateScrollToCenter
import dev.fanfly.wingslog.core.ui.swipe.rememberSwipeRevealController
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DataLogRowInfo
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.logs.viewing.DeleteLogConfirmDialog
import dev.fanfly.wingslog.feature.logs.viewing.detail.MaintenanceLogDetailSheet
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.viewing.NoRecordsMatch
import dev.fanfly.wingslog.feature.search.viewing.RecordCountRow
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterBar
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.feature.logs.sharedassets.generated.resources.add_first_maintenance_log
import wingslog.feature.logs.sharedassets.generated.resources.no_maintenance_logs_title
import wingslog.feature.logs.viewing.generated.resources.failed_to_load_logs
import wingslog.feature.search.sharedassets.generated.resources.search_placeholder
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogListContent(
  uiState: MaintenanceLogListUiState,
  /** The typed filter, read synchronously from the ViewModel; `uiState` carries the results. */
  filter: RecordFilter,
  syncStates: Map<String, BlobSyncState> = emptyMap(),
  dataLogs: Map<DataLogId, DataLogRowInfo>? = null,
  onSearchQueryChange: (String) -> Unit,
  onComponentFilterToggle: (ComponentType) -> Unit,
  onTimeWindowChange: (TimeWindow) -> Unit,
  onFacetToggle: (Facet) -> Unit = {},
  onClearFilter: () -> Unit,
  onRetry: () -> Unit,
  onLogClick: (MaintenanceLog) -> Unit,
  onDismissDetail: () -> Unit,
  onEditLog: ((String) -> Unit)?,
  /** Delete from a card's swipe panel; null (a read-only caller) leaves the cards gesture-free. */
  onDeleteLog: ((MaintenanceLog) -> Unit)? = null,
  onCancelDeleteLog: () -> Unit = {},
  onConfirmDeleteLog: () -> Unit = {},
  onAddLog: (() -> Unit)?,
  onAttachmentTap: (Attachment) -> Unit,
  openError: String? = null,
  onTaskClick: ((String) -> Unit)? = null,
  onSquawkClick: ((String) -> Unit)? = null,
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
  // One controller for the list, so opening a card closes whichever was open (PRD R5); filtering
  // rebuilds what is on screen, so it closes there too.
  val revealController = rememberSwipeRevealController()
  LaunchedEffect(filter) { revealController.close() }

  // Jump-to-log: pin the requested log and hold it through the tab's load churn. Right after the tab
  // opens the logs list can re-emit empty for a frame (the auth state re-settles, briefly nulling the
  // storage scope), which clamps the list to the top; re-asserting the scroll on every emission puts
  // it back on the target. We hold until the user grabs the list (a real drag, never our own
  // programmatic scroll) or an absolute cap elapses, then stop — we never clear the request here,
  // because that has to happen at the section level while this tab is off screen.
  val currentLogs by rememberUpdatedState(
    (uiState as? MaintenanceLogListUiState.Success)?.logs.orEmpty()
  )
  val currentAllLogs by rememberUpdatedState(
    (uiState as? MaintenanceLogListUiState.Success)?.allLogs.orEmpty()
  )
  val adsManager: AdsManager = koinInject()
  val showAds by adsManager.shouldShowsAds()
    .collectAsState(initial = false)
  // Set once the scroll has landed, so the highlight plays on a row that is on screen.
  var landedLogId by remember(scrollToLogId) { mutableStateOf<String?>(null) }
  // The display list, not the item list: logs under month headers, on a spine, on every tier.
  // Everything index-based below must agree with what the LazyColumn actually renders.
  val lines by remember {
    derivedStateOf {
      logListLines(
        currentLogs,
        showAds,
        allLogs = currentAllLogs
      )
    }
  }
  LaunchedEffect(scrollToLogId) {
    if (scrollToLogId == null) return@LaunchedEffect
    // A jump target must always be reachable: a search query or component filter left over from
    // earlier browsing would otherwise silently exclude it from `lines`, leaving nothing to scroll to
    // or highlight — the same "stale narrowing state hides the jump target" gap the Squawks/Tasks
    // tabs have on their Open/Closed and Active/Complied splits, just via a filter here instead of a
    // segmented toggle.
    if (filter.isActive) onClearFilter()
    coroutineScope {
      val pinning = launch {
        // Resolve against the DISPLAY list. Using the item index would drift by the number of ads
        // above the target once slots are interleaved, landing the pilot on the wrong log — and the
        // error grows further down the list.
        snapshotFlow {
          lines.indexOfFirst { it is LogListLine.Entry && it.log.id == scrollToLogId }
        }.collect { index ->
          if (index >= 0) {
            logListState.animateScrollToCenter(index)
            landedLogId = scrollToLogId
          }
        }
      }
      withTimeoutOrNull(8000.milliseconds) {
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
      // In the shape of what is coming, filter bar included, so nothing jumps when it lands.
      MaintenanceLogListUiState.Loading -> SkeletonList()

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
            title = stringResource(
              SharedRes.string.no_maintenance_logs_title,
              LexiconFormatter.sentenceCase(LocalThingLexicon.current.logNoun),
            ),
            description = LocalThingLexicon.current.logEmptyHint,
            icon = Icons.Default.History,
            actionText = onAddLog?.let { stringResource(SharedRes.string.add_first_maintenance_log) },
            onActionClick = onAddLog
          )
        } else {
          // The open log's detail: a pane beside the list on wide tiers, a sheet on a phone.
          val logDetail: (@Composable () -> Unit)? =
            uiState.selectedLog?.let { log ->
              {
                MaintenanceLogDetailSheet(
                  log = log,
                  availableCards = uiState.availableCards,
                  availableSquawks = uiState.availableSquawks,
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
                  dataLogs = dataLogs,
                  openError = openError,
                  onTaskClick = onTaskClick?.let { cb ->
                    { taskId ->
                      onDismissDetail()
                      cb(taskId)
                    }
                  },
                  onSquawkClick = onSquawkClick?.let { cb ->
                    { squawkId ->
                      onDismissDetail()
                      cb(squawkId)
                    }
                  },
                )
              }
            }

          ListDetailSection(detail = logDetail) {
            Column(modifier = Modifier.fillMaxSize()) {
              val logNounPlural =
                LexiconFormatter.plural(LocalThingLexicon.current.logNoun)
              RecordFilterBar(
                filter = filter,
                placeholder = stringResource(SearchRes.string.search_placeholder),
                showComponentFilter = componentTypesApply,
                componentLabel = { it.displayName() },
                onQueryChange = onSearchQueryChange,
                onOpenFilters = { showFilterSheet = true },
                onRemoveComponent = onComponentFilterToggle,
                onClearTime = { onTimeWindowChange(TimeWindow.All) },
                facetLabel = { (it as? Facet.Technician)?.name.orEmpty() },
                onRemoveFacet = onFacetToggle,
              )
              LogFilterControls(
                expanded = showFilterSheet,
                onDismiss = { showFilterSheet = false },
                filter = filter,
                uiState = uiState,
                onComponentFilterToggle = onComponentFilterToggle,
                onTimeWindowChange = onTimeWindowChange,
                onFacetToggle = onFacetToggle,
                onClearFilter = onClearFilter,
              )
              RecordCountRow(
                count = uiState.logs.size,
                nounSingular = LocalThingLexicon.current.logNoun.singular,
                nounPlural = logNounPlural,
                filterActive = filter.isActive,
                onClear = onClearFilter,
              )

              if (uiState.logs.isEmpty()) {
                Box(
                  modifier = Modifier.weight(1f)
                    .fillMaxWidth(),
                  contentAlignment = Alignment.Center
                ) {
                  NoRecordsMatch(
                    nounPlural = logNounPlural,
                    onClearFilters = onClearFilter
                  )
                }
              } else {
                LogList(
                  lines = lines,
                  listState = logListState,
                  revealController = revealController,
                  uiState = uiState,
                  landedLogId = landedLogId,
                  onLogClick = onLogClick,
                  onDeleteLog = onDeleteLog,
                  modifier = Modifier.weight(1f)
                    .fillMaxWidth(),
                )
              }

              uiState.deletingLog?.let {
                DeleteLogConfirmDialog(
                  onConfirm = onConfirmDeleteLog,
                  onDismiss = onCancelDeleteLog,
                )
              }

            }
          }
        }
      }
    }
  }
}
