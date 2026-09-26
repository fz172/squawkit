package dev.fanfly.wingslog.feature.logs.viewing.list

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.fanfly.wingslog.core.datetime.toMonthHeading
import dev.fanfly.wingslog.core.ui.adaptive.shell.navpill.navPillAndFabClearance
import dev.fanfly.wingslog.core.ui.list.jumpTargetHighlight
import dev.fanfly.wingslog.core.ui.list.stickySectionHeader
import dev.fanfly.wingslog.core.ui.swipe.SwipeActionCard
import dev.fanfly.wingslog.core.ui.swipe.SwipeRevealController
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.feature.logs.viewing.list.card.LogGapRow
import dev.fanfly.wingslog.feature.logs.viewing.list.card.MaintenanceLogCard
import dev.fanfly.wingslog.feature.search.datamanager.LogAdapter
import dev.fanfly.wingslog.feature.search.viewing.wordsIn
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksSharedRes

/** The month-sectioned spine of log cards, gap rows and ad slots that [lines] describes. */
@Composable
internal fun LogList(
  lines: List<LogListLine>,
  listState: LazyListState,
  revealController: SwipeRevealController,
  uiState: MaintenanceLogListUiState.Success,
  landedLogId: String?,
  onLogClick: (MaintenanceLog) -> Unit,
  onDeleteLog: ((MaintenanceLog) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val undated = stringResource(TasksSharedRes.string.unknown_date)
  LazyColumn(
    state = listState,
    modifier = modifier.nestedScroll(revealController.closeOnScroll),
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

        is LogListLine.Gap -> item(
          key = line.key,
          contentType = "gap"
        ) {
          LogGapRow(
            omitted = line.omitted,
          )
        }

        is LogListLine.Ad -> item(
          key = line.key,
          contentType = "ad"
        ) {
          AdSlot(
            surface = AdSurface.LOGS,
            slotIndex = line.slotIndex,
            modifier = Modifier.padding(vertical = Spacing.small),
          )
        }

        is LogListLine.Entry -> item(
          key = line.key,
          contentType = "entry"
        ) {
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
              matchNote = logMatchNote(
                uiState.matches[log.id].orEmpty(),
                log
              ),
              modifier = Modifier.jumpTargetHighlight(active = log.id == landedLogId),
            )
          }
        }
      }
    }
  }
}
