package dev.fanfly.wingslog.core.ui.sheet

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.adaptive.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.listdetail.DetailPresentation
import dev.fanfly.wingslog.core.ui.adaptive.listdetail.LocalDetailPresentation
import dev.fanfly.wingslog.core.ui.popup.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * A standardized template for displaying record details, adaptive by [dev.fanfly.wingslog.core.ui.adaptive.layout.LocalLayoutTier]:
 * - **COMPACT** — a [ModalBottomSheet] (the phone / legacy presentation).
 * - **MEDIUM and wider** — an end-aligned side drawer over a scrim, matching the adaptive web/tablet
 *   shell (see `docs/web/web_adaptive_layout_design.html` §4.4).
 * - **Inside a detail pane** ([LocalDetailPresentation] is [DetailPresentation.Pane]) — the same
 *   body drawn inline, full height, with a close control: a list-detail scaffold hosts it beside
 *   the list, so no dialog and no scrim.
 *
 * Both presentations share the same header + body layout. The tier defaults to COMPACT outside the
 * shell, so the legacy stack is unaffected.
 *
 * Features:
 * - Consistent horizontal padding ([dev.fanfly.wingslog.core.ui.theme.Spacing.extraLarge]).
 * - Built-in vertical scrolling.
 * - Standardized header layout: a title slot with an optional icon-only [headerAction] at its end;
 *   the record's actions go in a [DetailSheetActionRow] in the body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  /** A [DetailSheetEditAction], typically — small enough to share the title row. */
  headerAction: (@Composable () -> Unit)? = null,
  /** Pinned under the scrolling body — for an input that must stay reachable, such as comments. */
  bottomBar: (@Composable () -> Unit)? = null,
  headerSlot: @Composable ColumnScope.() -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (LocalDetailPresentation.current == DetailPresentation.Pane) {
    DetailBody(
      fillHeight = true,
      headerAction = headerAction,
      bottomBar = bottomBar,
      headerSlot = headerSlot,
      content = content,
      onClose = onDismiss,
    )
    return
  }
  if (LocalLayoutTier.current == LayoutTier.COMPACT) {
    ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = sheetState,
      modifier = modifier,
    ) {
      DetailBody(
        headerAction = headerAction,
        bottomBar = bottomBar,
        headerSlot = headerSlot,
        content = content
      )
    }
  } else {
    DetailEndDrawer(onDismiss = onDismiss, modifier = modifier) {
      DetailBody(
        fillHeight = true,
        headerAction = headerAction,
        bottomBar = bottomBar,
        headerSlot = headerSlot,
        content = content
      )
    }
  }
}
