package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskEmptyHint
import dev.fanfly.wingslog.core.template.taskHistoryEmptyHint
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.AdaptiveCardList
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.common.compose.DualSegmentedFilter
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.jumpTargetHighlight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.model.ListRow
import dev.fanfly.wingslog.feature.ads.model.withAdSlots
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.viewing.TaskCardItem
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.feature.tasks.sharedassets.generated.resources.due_with_count
import wingslog.feature.tasks.sharedassets.generated.resources.history_with_count
import wingslog.feature.tasks.sharedassets.generated.resources.no_tasks_yet
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes

@Composable
fun ComplianceSection(
  activeTasks: List<MaintenanceTaskWithStatus>,
  completedTasks: List<MaintenanceTaskWithStatus>,
  showComplied: Boolean,
  onToggleComplied: (Boolean) -> Unit,
  onCardClick: (MaintenanceTaskWithStatus) -> Unit = {},
  /** Task to report the on-screen position of, so the tab can scroll it into view. */
  scrollTargetId: String? = null,
  onTargetPositioned: (Float) -> Unit = {},
  showHeader: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
  ) {
    if (showHeader) {
      Text(
        text = LexiconFormatter.titleCasePlural(LocalThingLexicon.current.taskNoun),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    DualSegmentedFilter(
      option1 = stringResource(
        SharedRes.string.due_with_count,
        activeTasks.size
      ),
      option2 = stringResource(
        SharedRes.string.history_with_count,
        completedTasks.size
      ),
      selectedIndex = if (showComplied) 1 else 0,
      onSelect = { onToggleComplied(it == 1) },
    )

    val displayList = if (showComplied) completedTasks else activeTasks
    // Due / History are independent lists with independent counters, exactly like squawks
    // Open / Closed — this is a toggle over flat lists, not the grouped list the PRD describes.
    val adsManager: AdsManager = koinInject()
    val showAds by adsManager.shouldShowsAds()
      .collectAsState(initial = false)
    val rows = remember(displayList, showAds) {
      if (showAds) withAdSlots(displayList) else displayList.map {
        ListRow.Item(
          it
        )
      }
    }

    if (displayList.isEmpty()) {
      if (!showComplied) {
        EmptyState(
          title = stringResource(
            SharedRes.string.no_tasks_yet,
            LocalThingLexicon.current.taskNoun.plural,
          ),
          description = LocalThingLexicon.current.taskEmptyHint,
          icon = Icons.Default.CheckCircle,
        )
      } else {
        Text(
          text = LocalThingLexicon.current.taskHistoryEmptyHint,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = Spacing.large)
        )
      }
    } else {
      AdaptiveCardList(
        items = rows,
        columns = LocalLayoutTier.current.cardColumns,
        spacing = Spacing.medium,
        isSpanning = { it is ListRow.Ad },
      ) { row ->
        when (row) {
          is ListRow.Ad -> AdSlot(
            surface = AdSurface.TASKS,
            slotIndex = row.slotIndex,
          )

          is ListRow.Item -> {
            val item = row.value
            val isJumpTarget = item.card.id == scrollTargetId
            TaskCardItem(
              cardWithStatus = item,
              onClick = { onCardClick(item) },
              modifier = Modifier.fillMaxWidth()
                .then(
                  if (isJumpTarget) {
                    Modifier.onGloballyPositioned { onTargetPositioned(it.positionInRoot().y) }
                  } else {
                    Modifier
                  }
                )
                .jumpTargetHighlight(active = isJumpTarget),
            )
          }
        }
      }
    }
  }
}
