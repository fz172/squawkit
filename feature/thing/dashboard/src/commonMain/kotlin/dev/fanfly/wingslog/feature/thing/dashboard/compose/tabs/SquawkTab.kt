package dev.fanfly.wingslog.feature.thing.dashboard.compose.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.squawkEmptyHint
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.navPillAndFabClearance
import dev.fanfly.wingslog.core.ui.common.compose.DualSegmentedFilter
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.ListRowDivider
import dev.fanfly.wingslog.core.ui.common.compose.SectionHeader
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionCard
import dev.fanfly.wingslog.core.ui.common.compose.SwipeRevealController
import dev.fanfly.wingslog.core.ui.common.compose.animateScrollToCenter
import dev.fanfly.wingslog.core.ui.common.compose.jumpTargetHighlight
import dev.fanfly.wingslog.core.ui.common.compose.rememberSwipeRevealController
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.motionItem
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.comments.datamanager.CommentThreadController
import dev.fanfly.wingslog.feature.datalog.model.dataLogIdOrNull
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.feature.search.model.Facet
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.feature.search.model.countByTime
import dev.fanfly.wingslog.feature.search.viewing.ChoiceChip
import dev.fanfly.wingslog.feature.search.viewing.FilterSection
import dev.fanfly.wingslog.feature.search.viewing.NoRecordsMatch
import dev.fanfly.wingslog.feature.search.viewing.RecordCountRow
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterBar
import dev.fanfly.wingslog.feature.search.viewing.RecordFilterControls
import dev.fanfly.wingslog.feature.search.viewing.hiddenMatchNote
import dev.fanfly.wingslog.feature.search.viewing.wordsIn
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.viewing.DeleteSquawkConfirmDialog
import dev.fanfly.wingslog.feature.squawk.viewing.DismissSquawkDialog
import dev.fanfly.wingslog.feature.squawk.viewing.ResolveOptionsMenu
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkCard
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkDetailSheet
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkQuickActionCallbacks
import dev.fanfly.wingslog.feature.squawk.viewing.quickActions
import dev.fanfly.wingslog.feature.thing.dashboard.compose.RecordCommentComposer
import dev.fanfly.wingslog.feature.thing.dashboard.compose.RecordCommentThread
import dev.fanfly.wingslog.feature.thing.dashboard.data.SQUAWK_TIERS
import dev.fanfly.wingslog.feature.thing.dashboard.data.SquawkAdapter
import dev.fanfly.wingslog.feature.thing.dashboard.data.SquawkListLine
import dev.fanfly.wingslog.feature.thing.dashboard.data.SquawkTabViewModel
import dev.fanfly.wingslog.feature.thing.dashboard.data.ThingOverviewAction
import dev.fanfly.wingslog.feature.thing.dashboard.data.ThingOverviewUiState
import dev.fanfly.wingslog.feature.thing.dashboard.data.squawkListLines
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.SquawkPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.feature.search.sharedassets.generated.resources.filter_q_how_urgent
import wingslog.feature.search.sharedassets.generated.resources.filter_q_when_reported
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_closed
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_open
import wingslog.feature.search.sharedassets.generated.resources.match_serial
import wingslog.feature.search.sharedassets.generated.resources.search_placeholder
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.closed_with_count
import wingslog.feature.squawk.sharedassets.generated.resources.no_closed_squawks
import wingslog.feature.squawk.sharedassets.generated.resources.no_open_squawks
import wingslog.feature.squawk.sharedassets.generated.resources.open_with_count
import wingslog.feature.squawk.sharedassets.generated.resources.priority_high
import wingslog.feature.squawk.sharedassets.generated.resources.priority_low
import wingslog.feature.squawk.sharedassets.generated.resources.priority_medium
import kotlin.time.Clock
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

private val squawkOrder = compareByDescending<SquawkWithStatus> {
  it.squawk.priority
}.thenBy { it.squawk.created_at?.getEpochSecond() ?: Long.MAX_VALUE }

@Composable
fun SquawkTab(
  state: ThingOverviewUiState.Success,
  onAction: (ThingOverviewAction) -> Unit,
  onMutationAction: ((ThingOverviewAction) -> Unit)? = onAction,
  onLogClick: ((logId: String) -> Unit)? = null,
  onOpenDataLog: ((DataLogId) -> Unit)? = null,
  /** Jumped-to squawk (from a log's Resolved Squawks): switch to its sub-view and scroll to it. */
  scrollToSquawkId: String? = null,
  /** The open squawk sheet's comment thread; null leaves the sheet without comments. */
  commentThread: CommentThreadController? = null,
  showHeader: Boolean = true,
  modifier: Modifier = Modifier,
) {
  var showClosed by rememberSaveable { mutableStateOf(false) }
  val analytics = LocalAnalytics.current
  val attachmentOpener: AttachmentOpener = koinInject()
  val adsManager: AdsManager = koinInject()
  val coroutineScope = rememberCoroutineScope()
  var openError by remember { mutableStateOf<String?>(null) }
  var showFilterSheet by remember { mutableStateOf(false) }
  val tabViewModel: SquawkTabViewModel =
    koinViewModel(
      key = "squawks:${state.thing.id}",
      parameters = {
        parametersOf(
          state.thing.id,
          state.thing.template?.id.orEmpty()
        )
      },
    )
  val tabState by tabViewModel.uiState.collectAsStateWithLifecycle()
  val squawkFilter by tabViewModel.filter.collectAsStateWithLifecycle()
  val setFilter = tabViewModel::onFilterChange
  val squawkNoun = LocalThingLexicon.current.squawkNoun

  val openSquawks = tabState.squawks
    .filter { it.status == SquawkStatus.OPEN }
    .sortedWith(squawkOrder)
  val closedSquawks = tabState.squawks
    .filter { it.status == SquawkStatus.ADDRESSED || it.status == SquawkStatus.DISMISSED }
    .sortedByDescending { it.squawk.created_at?.getEpochSecond() ?: 0L }

  val listState = rememberLazyListState()
  // One controller for the whole list, so opening a card closes whichever was open — across the
  // grid's columns on a wide tier too (PRD R5).
  val revealController = rememberSwipeRevealController()
  LaunchedEffect(showClosed) { revealController.close() }

  // Each sub-view is its own list with its own counter — switching the toggle re-evaluates from
  // scratch, which falls out of wrapping the filtered list rather than the union.
  val displayList = if (showClosed) closedSquawks else openSquawks
  val showAds by adsManager.shouldShowsAds()
    .collectAsState(initial = false)
  val columns = LocalLayoutTier.current.cardColumns
  // Only the open list is in priority order, so only it is grouped; closed is newest first.
  val lines = remember(displayList, showClosed, columns, showAds) {
    squawkListLines(displayList, grouped = !showClosed, columns = columns, showAds = showAds)
  }
  val currentLines by rememberUpdatedState(lines)
  // Lazy items ahead of the lines: the optional title, then the controls.
  val leadingItems = if (showHeader) 2 else 1
  // Set once the scroll has landed, so the highlight plays on a row that is on screen.
  var landedSquawkId by remember(scrollToSquawkId) { mutableStateOf<String?>(null) }

  // A jump target must be reachable whatever was filtered before.
  LaunchedEffect(scrollToSquawkId) {
    if (scrollToSquawkId != null && squawkFilter.isActive) tabViewModel.clearFilter()
  }
  val scrollTarget =
    scrollToSquawkId?.let { id -> tabState.squawks.find { it.squawk.id == id } }
  // Keyed on the target's status too, not just its id: a tapped notification can arrive and be acted
  // on before the local sync pull carrying the very status change it announced has landed, so this
  // can first see the squawk as still OPEN. Re-running once the real status shows up is what
  // corrects showClosed and the scroll target instead of leaving both stuck on Open.
  LaunchedEffect(scrollToSquawkId, scrollTarget?.status) {
    val target = scrollTarget ?: return@LaunchedEffect
    showClosed = target.status != SquawkStatus.OPEN
    // Wait for the (possibly just-switched) sub-view to hold the target, then scroll once.
    val index = snapshotFlow {
      currentLines.indexOfFirst { line ->
        line is SquawkListLine.Records && line.items.any { it.squawk.id == target.squawk.id }
      }
    }.first { it >= 0 }
    listState.animateScrollToCenter(leadingItems + index)
    landedSquawkId = target.squawk.id
  }

  // Open and Closed are different lists, not one list that changed: keyed apart, so switching
  // swaps them rather than fading one set of rows out through the other.
  key(showClosed) {
    LazyColumn(
      state = listState,
      modifier = modifier
        .fillMaxSize()
        .nestedScroll(revealController.closeOnScroll),
      contentPadding = PaddingValues(
        start = Spacing.screenPadding,
        end = Spacing.screenPadding,
        // Clear the floating pill this content scrolls beneath (0 on non-compact tiers), and the FAB.
        bottom = navPillAndFabClearance + Spacing.buttonHeight + Spacing.screenPadding,
      ),
      // No arrangement gap: flat rows meet a hairline, and the headers pad themselves.
    ) {
      if (showHeader) {
        item(key = "title") {
          Text(
            text = LexiconFormatter.titleCasePlural(squawkNoun),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.medium),
          )
        }
      }

      item(key = "controls") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
          RecordFilterBar(
            filter = squawkFilter,
            placeholder = stringResource(SearchRes.string.search_placeholder),
            // Squawks are filed against the thing, not a component, so the section would be dead.
            showComponentFilter = false,
            componentLabel = { it.displayName() },
            onQueryChange = { setFilter(squawkFilter.copy(query = it)) },
            onOpenFilters = { showFilterSheet = true },
            onRemoveComponent = { setFilter(squawkFilter.toggleComponent(it)) },
            onClearTime = { setFilter(squawkFilter.copy(time = TimeWindow.All)) },
            horizontalPadding = Spacing.none,
            facetLabel = {
              (it as? Facet.Priority)?.let { p -> priorityLabel(p.value) }
                .orEmpty()
            },
            onRemoveFacet = { setFilter(squawkFilter.toggleFacet(it)) },
          )
          // Unfiltered, so a chip's count does not move every time another chip is tapped.
          val countAdapter =
            remember { SquawkAdapter(TimeZone.currentSystemDefault()) }
          val today = remember {
            Clock.System.now()
              .toLocalDateTime(TimeZone.currentSystemDefault()).date
          }
          val subView = if (showClosed) {
            state.squawks.filter { it.status != SquawkStatus.OPEN }
          } else {
            state.squawks.filter { it.status == SquawkStatus.OPEN }
          }
          RecordFilterControls(
            expanded = showFilterSheet,
            inline = LocalLayoutTier.current.hasSideNav,
            scopeLabel = if (showClosed) {
              stringResource(SearchRes.string.filter_scope_closed, squawkNoun.plural)
            } else {
              stringResource(SearchRes.string.filter_scope_open, squawkNoun.plural)
            },
            filter = squawkFilter,
            // Squawks are filed against the thing, not a component, so the section would be dead.
            showComponentFilter = false,
            componentQuestion = "",
            componentLabel = { it.displayName() },
            onComponentToggle = { setFilter(squawkFilter.toggleComponent(it)) },
            timeQuestion = stringResource(SearchRes.string.filter_q_when_reported),
            onTimeWindowChange = { setFilter(squawkFilter.copy(time = it)) },
            onClear = { setFilter(squawkFilter.withoutFilters()) },
            onDismiss = { showFilterSheet = false },
            resultCount = (if (showClosed) closedSquawks else openSquawks).size,
            totalCount = subView.size,
            nounSingular = squawkNoun.singular,
            nounPlural = squawkNoun.plural,
            timeCount = { window ->
              subView.countByTime(
                countAdapter,
                window,
                today
              )
            },
            horizontalPadding = Spacing.none,
            facetSection = {
              FilterSection(
                stringResource(SearchRes.string.filter_q_how_urgent),
                pickOne = false
              ) {
                SQUAWK_TIERS.forEach { priority ->
                  val facet = Facet.Priority(priority)
                  ChoiceChip(
                    label = priorityLabel(priority),
                    selected = facet in squawkFilter.facets,
                    count = subView.count { it.squawk.priority == priority },
                    onClick = { setFilter(squawkFilter.toggleFacet(facet)) },
                  )
                }
              }
            },
          )

          DualSegmentedFilter(
            option1 = stringResource(Res.string.open_with_count, openSquawks.size),
            option2 = stringResource(
              Res.string.closed_with_count,
              closedSquawks.size
            ),
            selectedIndex = if (showClosed) 1 else 0,
            onSelect = {
              showClosed = it == 1
              analytics.logScreenView("shell/squawks/${if (it == 1) "closed" else "open"}")
            },
          )

          RecordCountRow(
            count = displayList.size,
            nounSingular = squawkNoun.singular,
            nounPlural = squawkNoun.plural,
            filterActive = squawkFilter.isActive,
            onClear = { tabViewModel.clearFilter() },
            horizontalPadding = Spacing.none,
          )
        }
      }

      if (displayList.isEmpty()) {
        item(key = "empty") {
          if (squawkFilter.isActive) {
            NoRecordsMatch(
              nounPlural = squawkNoun.plural,
              onClearFilters = { tabViewModel.clearFilter() },
              modifier = Modifier.padding(top = Spacing.medium),
            )
          } else if (!showClosed) {
            EmptyState(
              title = stringResource(Res.string.no_open_squawks, squawkNoun.plural),
              description = LocalThingLexicon.current.squawkEmptyHint,
              icon = Icons.Default.CheckCircle,
            )
          } else {
            Text(
              text = stringResource(Res.string.no_closed_squawks, squawkNoun.plural),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = Spacing.large),
            )
          }
        }
      }

      lines.forEachIndexed { index, line ->
        when (line) {
          // The header carries the tier and its count, so the rows under it carry neither.
          is SquawkListLine.TierHeader -> stickyHeader(
            key = line.key,
            contentType = "tier-header",
          ) {
            SectionHeader(
              title = priorityLabel(line.tier),
              count = line.count,
            )
          }

          // An ad is a full-width line, never one cell of the grid (design §5.2, PRD §6.5).
          is SquawkListLine.Ad -> item(key = line.key, contentType = "ad") {
            AdSlot(
              surface = AdSurface.SQUAWKS,
              slotIndex = line.slotIndex,
              modifier = Modifier.padding(vertical = Spacing.small),
            )
          }

          is SquawkListLine.Records -> item(key = line.key, contentType = "records") {
            val previous = lines.getOrNull(index - 1) as? SquawkListLine.Records
            // One animated node per key: the rule travels with its line.
            Column(modifier = motionItem()) {
              if (previous != null) ListRowDivider()
              Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                line.items.forEach { item ->
                  SquawkRow(
                    item = item,
                    state = state,
                    matches = tabState.matches[item.squawk.id].orEmpty(),
                    showPriority = showClosed,
                    isJumpTarget = item.squawk.id == landedSquawkId,
                    revealController = revealController,
                    onAction = onAction,
                    onMutationAction = onMutationAction,
                    modifier = Modifier.weight(1f),
                  )
                }
                // Keep a short last line's cells aligned with the grid above.
                repeat(columns - line.items.size) { Spacer(Modifier.weight(1f)) }
              }
            }
          }
        }
      }
    }
  }

  // Rendered at tab level, not inside the card, so they are not clipped by the swipe container.
  if (state.dismissingSquawkId != null) {
    DismissSquawkDialog(
      onConfirm = { onAction(ThingOverviewAction.ConfirmDismissSquawk(it)) },
      onDismiss = { onAction(ThingOverviewAction.CancelDismissSquawk) },
    )
  }
  if (state.deletingSquawkId != null) {
    DeleteSquawkConfirmDialog(
      onConfirm = { onAction(ThingOverviewAction.ConfirmDeleteSquawk) },
      onDismiss = { onAction(ThingOverviewAction.CancelDeleteSquawk) },
    )
  }

  state.selectedSquawk?.let { selected ->
    SquawkDetailSheet(
      item = selected,
      addressingLog = state.logForSelectedSquawk,
      onLogClick = onLogClick,
      onDismiss = {
        openError = null
        onAction(ThingOverviewAction.DismissSquawkDetail)
      },
      onAttachmentTap = { attachment ->
        openError = null
        attachment.dataLogIdOrNull()
          ?.let { dataLogId ->
            onAction(ThingOverviewAction.DismissSquawkDetail)
            onOpenDataLog?.invoke(dataLogId)
            return@SquawkDetailSheet
          }
        val openFlow = attachmentOpener.open(attachment)
        coroutineScope.launch {
          openFlow.collect { openState ->
            if (openState is OpenState.Failed) openError =
              openState.error.message
          }
        }
      },
      syncStates = state.syncStates,
      dataLogs = state.dataLogs,
      openError = openError,
      onFixedClick = onMutationAction?.let { mutate ->
        {
          onAction(ThingOverviewAction.DismissSquawkDetail)
          mutate(ThingOverviewAction.SquawkFixedClick(selected.squawk.id))
        }
      },
      onDismissNoWorkPlanned = onMutationAction?.let { mutate ->
        {
          onAction(ThingOverviewAction.DismissSquawkDetail)
          mutate(ThingOverviewAction.SquawkDismissClick(selected.squawk.id))
        }
      },
      comments = commentThread?.let { thread -> { RecordCommentThread(thread) } },
      commentComposer = commentThread?.let { thread ->
        { RecordCommentComposer(thread, state.isAnonymous) }
      },
      onReopenClick = onMutationAction?.let { mutate ->
        {
          onAction(ThingOverviewAction.DismissSquawkDetail)
          mutate(ThingOverviewAction.SquawkReopenClick(selected.squawk.id))
        }
      },
      onEditClick = onMutationAction?.let { mutate ->
        {
          onAction(ThingOverviewAction.DismissSquawkDetail)
          mutate(
            ThingOverviewAction.EditSquawkClick(
              state.thing.id,
              selected.squawk.id
            )
          )
        }
      },
    )
  }
}

@Composable
private fun SquawkRow(
  item: SquawkWithStatus,
  state: ThingOverviewUiState.Success,
  matches: List<FieldMatch>,
  showPriority: Boolean,
  isJumpTarget: Boolean,
  revealController: SwipeRevealController,
  onAction: (ThingOverviewAction) -> Unit,
  onMutationAction: ((ThingOverviewAction) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  // Whoever may open the edit form may swipe (PRD R20); a read-only caller gets an empty action
  // list, which disables the drag.
  val quickActions = onMutationAction?.let { mutate ->
    item.quickActions(
      SquawkQuickActionCallbacks(
        onResolve = { mutate(ThingOverviewAction.SquawkResolveClick(item)) },
        onDelete = {
          revealController.close()
          mutate(ThingOverviewAction.DeleteSquawkClick(item))
        },
        resolveMenu = {
          ResolveOptionsMenu(
            expanded = state.resolvingSquawkId == item.squawk.id,
            onDismissRequest = {
              revealController.close()
              mutate(ThingOverviewAction.DismissSquawkResolveMenu)
            },
            onDismissNoWorkPlanned = {
              revealController.close()
              mutate(ThingOverviewAction.SquawkDismissClick(item.squawk.id))
            },
            onFixedClick = {
              revealController.close()
              mutate(ThingOverviewAction.SquawkFixedClick(item.squawk.id))
            },
          )
        },
      )
    )
  }
    .orEmpty()
  val shownFields = setOf(SquawkAdapter.FIELD_TITLE, SquawkAdapter.FIELD_DESCRIPTION)
  SwipeActionCard(
    actions = quickActions,
    controller = revealController,
    key = item.squawk.id,
    modifier = modifier,
  ) {
    SquawkCard(
      item = item,
      onClick = { onAction(ThingOverviewAction.ShowSquawkDetail(item)) },
      showPriority = showPriority,
      highlight = matches.wordsIn(SquawkAdapter.FIELD_TITLE, SquawkAdapter.FIELD_DESCRIPTION),
      matchNote = hiddenMatchNote(matches, shownFields) { match ->
        if (match.field == SquawkAdapter.FIELD_SERIAL) stringResource(
          SearchRes.string.match_serial,
          item.squawk.component_serial
        ) else null
      },
      modifier = Modifier.fillMaxWidth()
        .jumpTargetHighlight(active = isJumpTarget),
    )
  }
}

@Composable
private fun priorityLabel(priority: SquawkPriority): String = when (priority) {
  SquawkPriority.SQUAWK_PRIORITY_AOG -> LexiconFormatter.titleCase(
    LocalThingLexicon.current.down_status
  )

  SquawkPriority.SQUAWK_PRIORITY_HIGH -> stringResource(Res.string.priority_high)
  SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> stringResource(Res.string.priority_medium)
  else -> stringResource(Res.string.priority_low)
}
