package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksSharedRes
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import dev.fanfly.wingslog.core.datetime.toMonthHeading
import dev.fanfly.wingslog.core.ui.common.compose.stickySectionHeader
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.logEmptyHint
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.technicianNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.navPillAndFabClearance
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionCard
import dev.fanfly.wingslog.core.ui.common.compose.animateScrollToCenter
import dev.fanfly.wingslog.core.ui.common.compose.jumpTargetHighlight
import dev.fanfly.wingslog.core.ui.common.compose.rememberSwipeRevealController
import dev.fanfly.wingslog.core.ui.common.compose.SkeletonList
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.motionItem
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DataLogRowInfo
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.logs.viewing.log.data.MaintenanceLogListUiState
import dev.fanfly.wingslog.feature.search.datamanager.LogAdapter
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.countByComponent
import dev.fanfly.wingslog.feature.search.model.countByTime
import dev.fanfly.wingslog.feature.search.viewing.ChoiceChip
import dev.fanfly.wingslog.feature.search.viewing.FacetOption
import dev.fanfly.wingslog.feature.search.viewing.FacetPickerPage
import dev.fanfly.wingslog.feature.search.viewing.FilterSection
import dev.fanfly.wingslog.feature.search.viewing.NoRecordsMatch
import dev.fanfly.wingslog.feature.search.viewing.RecordCountRow
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterBar
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterControls
import dev.fanfly.wingslog.feature.search.viewing.hiddenMatchNote
import dev.fanfly.wingslog.feature.search.viewing.wordsIn
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.feature.logs.sharedassets.generated.resources.add_first_maintenance_log
import wingslog.feature.logs.sharedassets.generated.resources.no_maintenance_logs_title
import wingslog.feature.logs.viewing.generated.resources.failed_to_load_logs
import wingslog.feature.search.sharedassets.generated.resources.filter_all_people
import wingslog.feature.search.sharedassets.generated.resources.filter_q_when_happened
import wingslog.feature.search.sharedassets.generated.resources.filter_q_who_signed
import wingslog.feature.search.sharedassets.generated.resources.filter_q_worked_on
import wingslog.feature.search.sharedassets.generated.resources.match_serial
import wingslog.feature.search.sharedassets.generated.resources.search_placeholder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
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
  // Chip counts are measured against the tab's whole list with the same adapter the search uses,
  // so "Airframe 18" means the same thing the filter will.
  val zone = remember { TimeZone.currentSystemDefault() }
  val countAdapter = remember(zone) { LogAdapter(zone) }
  val today = remember {
    Clock.System.now()
      .toLocalDateTime(zone).date
  }
  var showPeoplePicker by remember { mutableStateOf(false) }
  var peopleQuery by remember { mutableStateOf("") }
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
    derivedStateOf { logListLines(currentLogs, showAds, allLogs = currentAllLogs) }
  }
  val undated = stringResource(TasksSharedRes.string.unknown_date)
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
            val allLogs = uiState.allLogs
            val technicianNoun = LocalThingLexicon.current.technicianNoun
            val technicianPlural = LexiconFormatter.plural(technicianNoun)
            // Ordered by who signed the most recent log here, so "recent" means recent on this
            // thing rather than whoever happens to sort first.
            val people = remember(allLogs, filter.facets) {
              allLogs.mapNotNull { it.technician?.name?.takeIf(String::isNotBlank) }
                .distinct()
                .map { name ->
                  FacetOption(
                    name = name,
                    count = allLogs.count { it.technician?.name == name },
                    selected = Facet.Technician(name) in filter.facets,
                  )
                }
            }
            val recentPeople = remember(allLogs, people) {
              val order = allLogs.mapNotNull { it.technician?.name }
                .distinct()
              people.sortedBy {
                order.indexOf(it.name)
                  .takeIf { i -> i >= 0 } ?: Int.MAX_VALUE
              }
            }
            // Anyone already chosen is promoted into a chip slot, so a selection is never hidden
            // behind "All 24 people" where it cannot be seen or undone.
            val quickPeople = remember(recentPeople) {
              (recentPeople.filter { it.selected } + recentPeople).distinct()
                .take(2)
            }
            RecordFilterControls(
              expanded = showFilterSheet,
              inline = LocalLayoutTier.current.hasSideNav,
              scopeLabel = logNounPlural,
              filter = filter,
              showComponentFilter = componentTypesApply,
              componentQuestion = stringResource(SearchRes.string.filter_q_worked_on),
              componentLabel = { it.displayName() },
              onComponentToggle = onComponentFilterToggle,
              timeQuestion = stringResource(SearchRes.string.filter_q_when_happened),
              onTimeWindowChange = onTimeWindowChange,
              onClear = { onClearFilter() },
              onDismiss = { showFilterSheet = false },
              resultCount = uiState.logs.size,
              totalCount = uiState.totalCount,
              nounSingular = LocalThingLexicon.current.logNoun.singular,
              nounPlural = logNounPlural,
              componentCount = { c ->
                uiState.allLogs.countByComponent(
                  countAdapter,
                  c
                )
              },
              timeCount = { w ->
                uiState.allLogs.countByTime(
                  countAdapter,
                  w,
                  today
                )
              },
              facetSection = if (people.isEmpty()) null else {
                {
                  FilterSection(
                    stringResource(SearchRes.string.filter_q_who_signed),
                    pickOne = false,
                  ) {
                    // Two chips, whatever the roster does; the rest live behind the picker.
                    quickPeople.forEach { option ->
                      val facet = Facet.Technician(option.name)
                      ChoiceChip(
                        label = option.name,
                        selected = option.selected,
                        count = option.count,
                        onClick = { onFacetToggle(facet) },
                      )
                    }
                    if (people.size > quickPeople.size) {
                      ChoiceChip(
                        label = stringResource(
                          SearchRes.string.filter_all_people,
                          people.size,
                          technicianPlural,
                        ),
                        selected = false,
                        onClick = { showPeoplePicker = true },
                      )
                    }
                  }
                }
              },
              page = if (!showPeoplePicker) null else {
                {
                  FacetPickerPage(
                    title = stringResource(SearchRes.string.filter_q_who_signed),
                    options = people,
                    recent = recentPeople,
                    nounSingular = technicianNoun.singular,
                    nounPlural = technicianPlural,
                    query = peopleQuery,
                    onQueryChange = { peopleQuery = it },
                    onToggle = { onFacetToggle(Facet.Technician(it.name)) },
                    onBack = {
                      peopleQuery = ""
                      showPeoplePicker = false
                    },
                  )
                }
              },
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
              LazyColumn(
                state = logListState,
                modifier = Modifier.weight(1f)
                  .fillMaxWidth()
                  .nestedScroll(revealController.closeOnScroll),
                contentPadding = PaddingValues(
                  start = Spacing.screenPadding,
                  end = Spacing.screenPadding,
                  top = Spacing.small,
                  // Room for the add-FAB, plus the floating pill this list now scrolls beneath
                  bottom = navPillAndFabClearance
                ),
                // No arrangement gap: the spine has to run unbroken from one entry into the next.
              ) {
                lines.forEach { line ->
                  when (line) {
                    is LogListLine.MonthHeader -> stickySectionHeader(
                      key = line.key,
                      title = line.month?.toMonthHeading() ?: undated,
                      count = line.count,
                    )

                    is LogListLine.Gap -> item(key = line.key, contentType = "gap") {
                      LogGapRow(omitted = line.omitted, modifier = motionItem())
                    }

                    is LogListLine.Ad -> item(key = line.key, contentType = "ad") {
                      AdSlot(
                        surface = AdSurface.LOGS,
                        slotIndex = line.slotIndex,
                        modifier = motionItem().padding(vertical = Spacing.small),
                      )
                    }

                    is LogListLine.Entry -> item(key = line.key, contentType = "entry") {
                      val log = line.log
                      SwipeActionCard(
                        // A null callback yields no actions, which disables the drag (PRD R20).
                        actions = logQuickActions(
                          onDelete = onDeleteLog?.let { delete ->
                            {
                              revealController.close()
                              delete(log)
                            }
                          },
                        ),
                        controller = revealController,
                        key = log.id,
                        modifier = motionItem(),
                      ) {
                        MaintenanceLogCard(
                          log = log,
                          onClick = { onLogClick(log) },
                          connectsUp = line.connectsUp,
                          connectsDown = line.connectsDown,
                          isLatest = line.isLatest,
                          highlight = uiState.matches[log.id].orEmpty()
                            .wordsIn(
                              LogAdapter.FIELD_DESCRIPTION,
                              LogAdapter.FIELD_TECHNICIAN
                            ),
                          matchNote = logMatchNote(uiState.matches[log.id].orEmpty(), log),
                          modifier = Modifier.jumpTargetHighlight(active = log.id == landedLogId),
                        )
                      }
                    }
                  }
                }
              }
            }

            uiState.deletingLog?.let {
              DeleteLogConfirmDialog(
                onConfirm = onConfirmDeleteLog,
                onDismiss = onCancelDeleteLog,
              )
            }

            uiState.selectedLog?.let { log ->
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
        }
      }
    }
  }
}

/** The serial is searched but not shown on a log card; say so when it is the only match. */
@Composable
private fun logMatchNote(
  matches: List<FieldMatch>,
  log: MaintenanceLog
): AnnotatedString? =
  hiddenMatchNote(
    matches,
    setOf(
      LogAdapter.FIELD_DESCRIPTION,
      LogAdapter.FIELD_TECHNICIAN
    )
  ) { match ->
    if (match.field == LogAdapter.FIELD_SERIAL) stringResource(
      SearchRes.string.match_serial,
      log.component_serial
    ) else null
  }
