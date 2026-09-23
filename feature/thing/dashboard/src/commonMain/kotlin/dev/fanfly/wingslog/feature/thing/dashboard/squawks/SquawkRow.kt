package dev.fanfly.wingslog.feature.thing.dashboard.squawks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionCard
import dev.fanfly.wingslog.core.ui.common.compose.SwipeRevealController
import dev.fanfly.wingslog.core.ui.common.compose.jumpTargetHighlight
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.viewing.hiddenMatchNote
import dev.fanfly.wingslog.feature.search.viewing.wordsIn
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.viewing.ResolveOptionsMenu
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkCard
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkQuickActionCallbacks
import dev.fanfly.wingslog.feature.squawk.viewing.quickActions
import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewAction
import dev.fanfly.wingslog.feature.thing.dashboard.ThingOverviewUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.match_serial
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

@Composable
internal fun SquawkRow(
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
  val shownFields =
    setOf(SquawkAdapter.FIELD_TITLE, SquawkAdapter.FIELD_DESCRIPTION)
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
      highlight = matches.wordsIn(
        SquawkAdapter.FIELD_TITLE,
        SquawkAdapter.FIELD_DESCRIPTION
      ),
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
