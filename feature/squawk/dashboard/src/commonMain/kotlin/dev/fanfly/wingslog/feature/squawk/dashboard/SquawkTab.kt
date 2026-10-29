package dev.fanfly.wingslog.feature.squawk.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.adaptive.listdetail.ListDetailSection
import dev.fanfly.wingslog.core.ui.adaptive.shell.LocalSnackbarHostState
import dev.fanfly.wingslog.core.ui.adaptive.shell.navpill.navPillAndFabClearance
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.list.ListRowDivider
import dev.fanfly.wingslog.core.ui.list.SectionHeader
import dev.fanfly.wingslog.core.ui.list.animateScrollToCenter
import dev.fanfly.wingslog.core.ui.perf.SwitchTrace
import dev.fanfly.wingslog.core.ui.swipe.rememberSwipeRevealController
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.feature.squawk.dashboard.detail.SquawkDetailViewModel
import dev.fanfly.wingslog.feature.squawk.dashboard.detail.SquawkTabDialogs
import dev.fanfly.wingslog.feature.squawk.dashboard.detail.squawkDetailFor
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.viewing.statusTier
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.TemplateId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val squawkOrder = compareByDescending<SquawkWithStatus> {
  it.squawk.priority
}.thenBy { it.squawk.created_at?.getEpochSecond() ?: Long.MAX_VALUE }

@Composable
fun SquawkTab(
  thingId: ThingId,
  isAnonymous: Boolean = false,
  canMutate: Boolean = true,
  onNavigateToEditSquawk: (squawkId: SquawkId) -> Unit = {},
  onNavigateToCreateLog: (squawkId: SquawkId) -> Unit = {},
  onLogClick: ((logId: MaintenanceLogId) -> Unit)? = null,
  onOpenDataLog: ((DataLogId) -> Unit)? = null,
  /** Jumped-to squawk (from a log's Resolved Squawks): switch to its sub-view and scroll to it. */
  scrollToSquawkId: String? = null,
  showHeader: Boolean = true,
  modifier: Modifier = Modifier,
) {
  SwitchTrace.step("SquawkTab composing")
  var showClosed by rememberSaveable { mutableStateOf(false) }
  val adsManager: AdsManager = koinInject()
  val templateId = TemplateId(LocalThingTemplate.current?.id.orEmpty())
  val tabViewModel: SquawkTabViewModel =
    koinViewModel(
      key = "squawks:${thingId.value}",
      parameters = {
        parametersOf(thingId, templateId)
      },
    )
  val detailViewModel: SquawkDetailViewModel =
    koinViewModel(
      key = "squawk-detail:${thingId.value}",
      parameters = {
        parametersOf(thingId, templateId)
      },
    )
  SwitchTrace.step("SquawkTab view model ready")
  val tabState by tabViewModel.uiState.collectAsStateWithLifecycle()
  val detailState by detailViewModel.uiState.collectAsStateWithLifecycle()
  val commentThread by detailViewModel.commentThread.collectAsStateWithLifecycle()
  val squawkFilter by tabViewModel.filter.collectAsStateWithLifecycle()
  val setFilter = tabViewModel::onFilterChange
  val squawkNoun = LocalThingLexicon.current.squawkNoun

  // The ViewModel outlives the section: a squawk left open when the user switched away would still
  // be open on their return. Each entry to the section starts with the list alone.
  LaunchedEffect(detailViewModel) { detailViewModel.dismissDetail() }

  val snackbarHostState = LocalSnackbarHostState.current
  var pendingMessage by remember(thingId) { mutableStateOf<UiText?>(null) }
  LaunchedEffect(detailViewModel) {
    detailViewModel.events.collect { message ->
      pendingMessage = message
    }
  }
  val messageText = pendingMessage?.asString()
  LaunchedEffect(messageText) {
    val text = messageText ?: return@LaunchedEffect
    pendingMessage = null
    snackbarHostState?.showSnackbar(text)
  }

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
  // One column beside an open detail pane: the list is then a third of the width. Decided here,
  // where the lines are built, rather than by the tier the pane provides to its list.
  val tier = LocalLayoutTier.current
  val columns =
    if (tier.hasSideNav && detailState.selectedSquawk != null) 1 else tier.cardColumns
  // Only the open list is in priority order, so only it is grouped; closed is newest first.
  val lines = remember(displayList, showClosed, columns, showAds) {
    squawkListLines(
      displayList,
      grouped = !showClosed,
      columns = columns,
      showAds = showAds
    )
  }
  SideEffect {
    SwitchTrace.step("SquawkTab applied: ${tabState.squawks.size} squawks, ${lines.size} lines")
  }
  val currentLines by rememberUpdatedState(lines)
  // Lazy items ahead of the lines: the optional title, then the controls.
  val leadingItems = if (showHeader) 2 else 1
  // Set once the scroll has landed, so the highlight plays on a row that is on screen.
  var landedSquawkId by remember(scrollToSquawkId) {
    mutableStateOf<String?>(
      null
    )
  }

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

  val squawkDetail = squawkDetailFor(
    thingId = thingId,
    isAnonymous = isAnonymous,
    detailState = detailState,
    onDismiss = detailViewModel::dismissDetail,
    canMutate = canMutate,
    onFixedClick = { squawkId ->
      detailViewModel.onFixedClick()
      onNavigateToCreateLog(squawkId)
    },
    onDismissClick = detailViewModel::onDismissClick,
    onReopenClick = detailViewModel::reopenSquawk,
    onEditClick = onNavigateToEditSquawk,
    onLogClick = onLogClick,
    onOpenDataLog = onOpenDataLog,
    commentThread = commentThread,
  )

  // Open and Closed are different lists, not one list that changed: keyed apart, so switching
  // swaps them rather than fading one set of rows out through the other.
  ListDetailSection(detail = squawkDetail) {
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
          SquawkListControls(
            allSquawks = tabState.allSquawks,
            filter = squawkFilter,
            onFilterChange = setFilter,
            onClearFilter = { tabViewModel.clearFilter() },
            showClosed = showClosed,
            onShowClosedChange = { showClosed = it },
            openSquawks = openSquawks,
            closedSquawks = closedSquawks,
          )
        }

        if (tabState.loaded && displayList.isEmpty()) {
          item(key = "empty") {
            SquawkEmptyItem(
              filterActive = squawkFilter.isActive,
              showClosed = showClosed,
              onClearFilters = { tabViewModel.clearFilter() },
            )
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
                tier = line.tier.statusTier(),
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

            is SquawkListLine.Records -> item(
              key = line.key,
              contentType = "records"
            ) {
              val previous =
                lines.getOrNull(index - 1) as? SquawkListLine.Records
              // One animated node per key: the rule travels with its line.
              Column {
                if (previous != null) ListRowDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                  line.items.forEach { item ->
                    SquawkRow(
                      item = item,
                      resolvingSquawkId = detailState.resolvingSquawkId,
                      matches = tabState.matches[item.squawk.id].orEmpty(),
                      showPriority = showClosed,
                      isJumpTarget = item.squawk.id == landedSquawkId,
                      revealController = revealController,
                      onClick = { detailViewModel.showDetail(item) },
                      canMutate = canMutate,
                      onResolveClick = { detailViewModel.onResolveClick(item) },
                      onDismissResolveMenu = detailViewModel::dismissResolveMenu,
                      onDismissNoWorkPlanned = {
                        detailViewModel.onDismissClick(item.id)
                      },
                      onFixedClick = {
                        detailViewModel.onFixedClick()
                        onNavigateToCreateLog(item.id)
                      },
                      onDeleteClick = { detailViewModel.onDeleteClick(item) },
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
  }

  SquawkTabDialogs(
    state = detailState,
    onConfirmDismiss = detailViewModel::confirmDismiss,
    onCancelDismiss = detailViewModel::cancelDismiss,
    onConfirmDelete = detailViewModel::confirmDelete,
    onCancelDelete = detailViewModel::cancelDelete,
  )
}
