package dev.fanfly.wingslog.feature.tasks.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import dev.fanfly.wingslog.core.ui.bar.DualSegmentedFilter
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.list.EmptyState
import dev.fanfly.wingslog.core.ui.list.ListRowDivider
import dev.fanfly.wingslog.core.ui.list.SectionHeader
import dev.fanfly.wingslog.core.ui.list.jumpTargetHighlight
import dev.fanfly.wingslog.core.ui.swipe.SwipeAction
import dev.fanfly.wingslog.core.ui.swipe.SwipeActionCard
import dev.fanfly.wingslog.core.ui.swipe.SwipeRevealController
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.viewing.hiddenMatchNote
import dev.fanfly.wingslog.feature.search.viewing.wordsIn
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.feature.tasks.model.TaskAdapter
import dev.fanfly.wingslog.feature.tasks.viewing.TaskCardItem
import dev.fanfly.wingslog.feature.tasks.viewing.statusTier
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.feature.search.sharedassets.generated.resources.match_reference
import wingslog.feature.tasks.sharedassets.generated.resources.due_with_count
import wingslog.feature.tasks.sharedassets.generated.resources.history_with_count
import wingslog.feature.tasks.sharedassets.generated.resources.no_tasks_yet
import wingslog.feature.tasks.sharedassets.generated.resources.suggestions_empty_action
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes

@Composable
fun ComplianceSection(
  activeTasks: List<MaintenanceTaskWithStatus>,
  completedTasks: List<MaintenanceTaskWithStatus>,
  showComplied: Boolean,
  onToggleComplied: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  onCardClick: (MaintenanceTaskWithStatus) -> Unit = {},
  /** Offers the template's curated list from the empty state; null when there is none. */
  onBrowseSuggestions: (() -> Unit)? = null,
  /** Task to report the on-screen position of, so the tab can scroll it into view. */
  scrollTargetId: String? = null,
  /** Receives the vertical middle of the [scrollTargetId] card, in root coordinates. */
  onTargetPositioned: (Float) -> Unit = {},
  /** The jumped-to card, once the scroll to it has landed. */
  highlightedId: String? = null,
  showHeader: Boolean = true,
  /** The per-tab search and filter bar, under the header, and the result count below it. */
  filterBar: @Composable () -> Unit,
  countRow: @Composable () -> Unit,
  /** Shown instead of the empty states when a filter left nothing to list. */
  noMatch: (@Composable () -> Unit)? = null,
  /** The words the active search matched on a task, for highlighting. */
  matchesFor: (MaintenanceTaskWithStatus) -> List<FieldMatch> = { emptyList() },
  /**
   * Swipe quick actions per card, and the one-open-card controller the list shares. Both null (the
   * default) leaves the section gesture-free, which is what the Overview tab's rail wants.
   */
  revealController: SwipeRevealController? = null,
  quickActionsFor: (@Composable (MaintenanceTaskWithStatus) -> List<SwipeAction>)? = null,
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

    filterBar()

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

    countRow()

    val displayList = if (showComplied) completedTasks else activeTasks
    val isGrouped = !showComplied && displayList.any {
      it.dueStatus.status == DueStatus.OVERDUE || it.dueStatus.status == DueStatus.DUE_SOON
    }
    val adsManager: AdsManager = koinInject()
    val showAds by adsManager.shouldShowsAds()
      .collectAsState(initial = false)
    val columns = LocalLayoutTier.current.cardColumns
    val lines = remember(displayList, isGrouped, columns, showAds) {
      taskListLines(
        tasks = displayList,
        grouped = isGrouped,
        columns = columns,
        showAds = showAds,
      )
    }

    if (displayList.isEmpty()) {
      if (noMatch != null) {
        noMatch()
      } else if (!showComplied) {
        EmptyState(
          title = stringResource(
            SharedRes.string.no_tasks_yet,
            LocalThingLexicon.current.taskNoun.plural,
          ),
          description = LocalThingLexicon.current.taskEmptyHint,
          icon = Icons.Default.CheckCircle,
          actionText = onBrowseSuggestions?.let {
            stringResource(
              SharedRes.string.suggestions_empty_action,
              LocalThingLexicon.current.taskNoun.plural,
            )
          },
          onActionClick = onBrowseSuggestions,
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
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.none),
      ) {
        lines.forEachIndexed { index, line ->
          when (line) {
            is TaskListLine.StatusHeader -> {
              SectionHeader(
                title = taskDueStatusLabel(line.status),
                count = line.count,
                tier = line.status.statusTier(),
              )
            }

            is TaskListLine.Ad -> {
              AdSlot(
                surface = AdSurface.TASKS,
                slotIndex = line.slotIndex,
                modifier = Modifier.padding(vertical = Spacing.small),
              )
            }

            is TaskListLine.Records -> {
              val previous = lines.getOrNull(index - 1) as? TaskListLine.Records
              Column {
                if (previous != null) ListRowDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                  line.items.forEach { item ->
                    val isJumpTarget = item.card.id == scrollTargetId
                    val matches = matchesFor(item)
                    val taskCard = @Composable {
                      TaskCardItem(
                        cardWithStatus = item,
                        onClick = { onCardClick(item) },
                        showDueBadge = false,
                        highlight = matches.wordsIn(
                          TaskAdapter.FIELD_TITLE,
                          TaskAdapter.FIELD_NOTES
                        ),
                        matchNote = hiddenMatchNote(
                          matches,
                          setOf(
                            TaskAdapter.FIELD_TITLE,
                            TaskAdapter.FIELD_NOTES
                          )
                        ) { match ->
                          when (match.field) {
                            TaskAdapter.FIELD_REFERENCE -> stringResource(
                              SearchRes.string.match_reference,
                              item.card.reference_number
                            )

                            TaskAdapter.FIELD_AUTHORITY -> item.card.compliance_authority
                            TaskAdapter.FIELD_DETAILS -> item.card.compliance_details
                            else -> null
                          }
                        },
                        modifier = Modifier.fillMaxWidth()
                          .then(
                            if (isJumpTarget) {
                              Modifier.onGloballyPositioned {
                                onTargetPositioned(it.positionInRoot().y + it.size.height / 2f)
                              }
                            } else {
                              Modifier
                            }
                          )
                          .jumpTargetHighlight(active = item.card.id == highlightedId),
                      )
                    }
                    if (revealController == null || quickActionsFor == null) {
                      Box(modifier = Modifier.weight(1f)) {
                        taskCard()
                      }
                    } else {
                      SwipeActionCard(
                        actions = quickActionsFor(item),
                        controller = revealController,
                        key = item.card.id,
                        modifier = Modifier.weight(1f),
                        content = taskCard,
                      )
                    }
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
}
