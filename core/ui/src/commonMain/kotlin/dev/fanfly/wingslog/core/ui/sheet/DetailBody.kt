package dev.fanfly.wingslog.core.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.fanfly.wingslog.core.ui.selection.TextSelectionLayer
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.dismiss

@Composable
internal fun DetailBody(
  headerAction: (@Composable () -> Unit)?,
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

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Top,
        ) {
          Column(modifier = Modifier.weight(1f)) { headerSlot() }
          headerAction?.invoke()
        }

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

// DetailEndDrawer lives beside the body rather than in its own file: the popup-scope check wants
// the file that opens a raw Dialog to also be the one that starts the selection scope, and the body
// is that scope for both presentations.
/**
 * Right-aligned modal drawer used for record details on tablet/desktop widths. Rendered in a
 * full-screen [Dialog] so it overlays the whole app and dismisses on scrim tap / back / escape.
 */
@Composable
internal fun DetailEndDrawer(
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
