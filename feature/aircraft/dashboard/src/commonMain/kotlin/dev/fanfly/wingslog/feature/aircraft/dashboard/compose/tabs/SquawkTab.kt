package dev.fanfly.wingslog.feature.aircraft.dashboard.compose.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.ui.adaptive.compose.AdaptiveCardList
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalNavPillClearance
import dev.fanfly.wingslog.core.ui.common.compose.DualSegmentedFilter
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewAction
import dev.fanfly.wingslog.feature.aircraft.dashboard.data.AircraftOverviewUiState
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.datamanager.OpenState
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkCard
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkDetailSheet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.closed_with_count
import wingslog.feature.squawk.sharedassets.generated.resources.no_closed_squawks
import wingslog.feature.squawk.sharedassets.generated.resources.no_open_squawks
import wingslog.feature.squawk.sharedassets.generated.resources.no_open_squawks_description
import wingslog.feature.squawk.sharedassets.generated.resources.open_with_count
import wingslog.feature.squawk.sharedassets.generated.resources.squawks

private val squawkOrder = compareByDescending<SquawkWithStatus> {
  it.squawk.priority
}.thenBy { it.squawk.created_at?.getEpochSecond() ?: Long.MAX_VALUE }

@Composable
fun SquawkTab(
  state: AircraftOverviewUiState.Success,
  onAction: (AircraftOverviewAction) -> Unit,
  onMutationAction: ((AircraftOverviewAction) -> Unit)? = onAction,
  onLogClick: ((logId: String) -> Unit)? = null,
  /** Jumped-to squawk (from a log's Resolved Squawks): switch to its sub-view and scroll to it. */
  scrollToSquawkId: String? = null,
  showHeader: Boolean = true,
  modifier: Modifier = Modifier,
) {
  var showClosed by rememberSaveable { mutableStateOf(false) }
  val analytics = LocalAnalytics.current
  val attachmentOpener: AttachmentOpener = koinInject()
  val coroutineScope = rememberCoroutineScope()
  var openError by remember { mutableStateOf<String?>(null) }

  val openSquawks = state.squawks
    .filter { it.status == SquawkStatus.OPEN }
    .sortedWith(squawkOrder)
  val closedSquawks = state.squawks
    .filter { it.status == SquawkStatus.ADDRESSED || it.status == SquawkStatus.DISMISSED }
    .sortedByDescending { it.squawk.created_at?.getEpochSecond() ?: 0L }

  // Jump-to-squawk from a log: switch to the sub-view that holds the target, then scroll it to the
  // top of the (non-lazy) list. Scroll offsets use root coordinates — the target card and the scroll
  // container each report their top, and the difference plus the current scroll is the content
  // offset to animate to.
  val scrollState = rememberScrollState()
  var contentTopY by remember { mutableStateOf(0f) }
  var targetCardY by remember(scrollToSquawkId) { mutableStateOf<Float?>(null) }
  LaunchedEffect(scrollToSquawkId) {
    val id = scrollToSquawkId ?: return@LaunchedEffect
    val target = state.squawks.find { it.squawk.id == id } ?: return@LaunchedEffect
    showClosed = target.status != SquawkStatus.OPEN
    // Wait until the target card lays out in the (possibly just-switched) sub-view, then scroll once.
    val cardY = snapshotFlow { targetCardY }.filterNotNull().first()
    scrollState.animateScrollTo(
      (scrollState.value + (cardY - contentTopY)).roundToInt().coerceAtLeast(0)
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .onGloballyPositioned { contentTopY = it.positionInRoot().y }
      .padding(horizontal = Spacing.screenPadding)
      // Clear the floating pill this content now scrolls beneath (0 on non-compact tiers).
      .padding(bottom = LocalNavPillClearance.current),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Spacer(Modifier.height(Spacing.medium))

    if (showHeader) {
      Text(
        text = stringResource(Res.string.squawks),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }

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

    val displayList = if (showClosed) closedSquawks else openSquawks

    if (displayList.isEmpty()) {
      if (!showClosed) {
        EmptyState(
          title = stringResource(Res.string.no_open_squawks),
          description = stringResource(Res.string.no_open_squawks_description),
          icon = Icons.Default.CheckCircle,
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
      AdaptiveCardList(
        items = displayList,
        columns = LocalLayoutTier.current.cardColumns,
        spacing = Spacing.medium,
      ) { item ->
        SquawkCard(
          item = item,
          onClick = { onAction(AircraftOverviewAction.ShowSquawkDetail(item)) },
          modifier = Modifier.fillMaxWidth()
            .then(
              if (item.squawk.id == scrollToSquawkId) {
                Modifier.onGloballyPositioned { targetCardY = it.positionInRoot().y }
              } else {
                Modifier
              }
            ),
        )
      }
    }

    Spacer(Modifier.height(Spacing.buttonHeight + Spacing.screenPadding))
  }

  state.selectedSquawk?.let { selected ->
    SquawkDetailSheet(
      item = selected,
      addressingLog = state.logForSelectedSquawk,
      onLogClick = onLogClick,
      onDismiss = {
        openError = null
        onAction(AircraftOverviewAction.DismissSquawkDetail)
      },
      onAttachmentTap = { attachment ->
        openError = null
        val openFlow = attachmentOpener.open(attachment)
        coroutineScope.launch {
          openFlow.collect { openState ->
            if (openState is OpenState.Failed) openError =
              openState.error.message
          }
        }
      },
      syncStates = state.syncStates,
      openError = openError,
      onEditClick = onMutationAction?.let { mutate ->
        {
          onAction(AircraftOverviewAction.DismissSquawkDetail)
          mutate(
            AircraftOverviewAction.EditSquawkClick(
              state.aircraft.id,
              selected.squawk.id
            )
          )
        }
      },
    )
  }
}
