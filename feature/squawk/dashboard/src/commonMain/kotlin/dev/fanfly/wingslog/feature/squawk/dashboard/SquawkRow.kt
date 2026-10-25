package dev.fanfly.wingslog.feature.squawk.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.list.jumpTargetHighlight
import dev.fanfly.wingslog.core.ui.swipe.SwipeActionCard
import dev.fanfly.wingslog.core.ui.swipe.SwipeRevealController
import dev.fanfly.wingslog.feature.search.model.FieldMatch
import dev.fanfly.wingslog.feature.search.viewing.hiddenMatchNote
import dev.fanfly.wingslog.feature.search.viewing.wordsIn
import dev.fanfly.wingslog.feature.squawk.model.SquawkAdapter
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.squawk.viewing.ResolveOptionsMenu
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkCard
import dev.fanfly.wingslog.feature.squawk.viewing.SquawkQuickActionCallbacks
import dev.fanfly.wingslog.feature.squawk.viewing.quickActions
import dev.fanfly.wingslog.id.SquawkId
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.match_serial
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

@Composable
internal fun SquawkRow(
  item: SquawkWithStatus,
  resolvingSquawkId: SquawkId?,
  matches: List<FieldMatch>,
  showPriority: Boolean,
  isJumpTarget: Boolean,
  revealController: SwipeRevealController,
  onClick: () -> Unit,
  canMutate: Boolean,
  onResolveClick: () -> Unit,
  onDismissResolveMenu: () -> Unit,
  onDismissNoWorkPlanned: () -> Unit,
  onFixedClick: () -> Unit,
  onDeleteClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Whoever may open the edit form may swipe (PRD R20); a read-only caller gets an empty action
  // list, which disables the drag.
  val quickActions = if (canMutate) {
    item.quickActions(
      SquawkQuickActionCallbacks(
        onResolve = onResolveClick,
        onDelete = {
          revealController.close()
          onDeleteClick()
        },
        resolveMenu = {
          ResolveOptionsMenu(
            expanded = resolvingSquawkId == item.id,
            onDismissRequest = {
              revealController.close()
              onDismissResolveMenu()
            },
            onDismissNoWorkPlanned = {
              revealController.close()
              onDismissNoWorkPlanned()
            },
            onFixedClick = {
              revealController.close()
              onFixedClick()
            },
          )
        },
      )
    )
  } else {
    emptyList()
  }
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
      onClick = onClick,
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
