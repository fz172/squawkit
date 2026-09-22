package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.fanfly.wingslog.core.ui.adaptive.compose.DetailPresentation
import dev.fanfly.wingslog.core.ui.adaptive.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalDetailPresentation
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.TextSelectionLayer
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.dismiss

/**
 * A standardized template for displaying record details, adaptive by [dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier]:
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
 * - Standardized header layout with a title slot; actions go in a [DetailSheetActionRow] in the body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  /** Pinned under the scrolling body — for an input that must stay reachable, such as comments. */
  bottomBar: (@Composable () -> Unit)? = null,
  headerSlot: @Composable ColumnScope.() -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (LocalDetailPresentation.current == DetailPresentation.Pane) {
    DetailBody(
      fillHeight = true,
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
        bottomBar = bottomBar,
        headerSlot = headerSlot,
        content = content
      )
    }
  } else {
    DetailEndDrawer(onDismiss = onDismiss, modifier = modifier) {
      DetailBody(
        fillHeight = true,
        bottomBar = bottomBar,
        headerSlot = headerSlot,
        content = content
      )
    }
  }
}

@Composable
private fun DetailBody(
  bottomBar: (@Composable () -> Unit)?,
  headerSlot: @Composable ColumnScope.() -> Unit,
  content: @Composable ColumnScope.() -> Unit,
  // The drawer is full height, so its bar belongs at the bottom edge. A bottom sheet hugs its
  // content instead: a short record keeps a short sheet, with the bar right under it.
  fillHeight: Boolean = false,
  /** A pane has no scrim to tap and no handle to drag, so it carries its own close control. */
  onClose: (() -> Unit)? = null,
) {
  TextSelectionLayer {
    Column(
      modifier = Modifier.fillMaxWidth()
        .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = fillHeight)
          .padding(horizontal = Spacing.extraLarge)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        if (onClose != null) {
          IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End)
              .padding(top = Spacing.small),
          ) {
            Icon(
              Icons.Default.Close,
              contentDescription = stringResource(Res.string.dismiss)
            )
          }
        } else {
          Spacer(Modifier.height(Spacing.large))
        }

        headerSlot()

        // Body Content
        content()

        // Footer Spacer
        Spacer(Modifier.height(if (bottomBar == null) Spacing.huge else Spacing.large))
      }

      if (bottomBar != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(
              horizontal = Spacing.extraLarge,
              vertical = Spacing.medium
            ),
        ) {
          bottomBar()
        }
      }
    }
  }
}

/**
 * Right-aligned modal drawer used for record details on tablet/desktop widths. Rendered in a
 * full-screen [Dialog] so it overlays the whole app and dismisses on scrim tap / back / escape.
 */
@Composable
private fun DetailEndDrawer(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  body: @Composable () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Scrim — tap to dismiss.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
          ),
      )
      // Drawer panel — consumes clicks so taps inside don't dismiss.
      Surface(
        modifier = modifier
          .align(Alignment.CenterEnd)
          .fillMaxHeight()
          .widthIn(max = 460.dp)
          .width(460.dp)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
          ),
        tonalElevation = 1.dp,
      ) {
        body()
      }
    }
  }
}

/**
 * The row a sheet's actions sit in — state changes, the route to the edit form, delete. One row,
 * every action the same width and height, so a sheet with one action and a sheet with three read
 * the same way.
 */
@Composable
fun DetailSheetActionRow(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth()
      .height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    content = content,
  )
}

/**
 * One action in a [DetailSheetActionRow]. [primary] is the filled one — at most one per row — and
 * [destructive] is outlined in the error colour. A label wraps rather than truncates when the row
 * is crowded; [menu] is anchored to the button, for an action that opens options.
 */
@Composable
fun RowScope.DetailSheetAction(
  label: String,
  onClick: () -> Unit,
  primary: Boolean = false,
  destructive: Boolean = false,
  menu: @Composable () -> Unit = {},
) {
  Box(
    modifier = Modifier.weight(1f)
      .fillMaxHeight(),
  ) {
    val shape = RoundedCornerShape(Spacing.buttonCornerRadius)
    val buttonModifier = Modifier.fillMaxSize()
    val padding = PaddingValues(Spacing.small)
    val text: @Composable RowScope.() -> Unit =
      { Text(label, textAlign = TextAlign.Center) }
    when {
      destructive -> OutlinedButton(
        onClick = onClick,
        modifier = buttonModifier,
        shape = shape,
        contentPadding = padding,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        content = text,
      )

      primary -> Button(
        onClick = onClick,
        modifier = buttonModifier,
        shape = shape,
        contentPadding = padding,
        content = text,
      )

      else -> OutlinedButton(
        onClick = onClick,
        modifier = buttonModifier,
        shape = shape,
        contentPadding = padding,
        content = text,
      )
    }
    menu()
  }
}
