package dev.fanfly.wingslog.core.ui.popup

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog as M3AlertDialog

/*
 * Material popups with the selection scope reset at their boundary. Same names and signatures as
 * the Material 3 originals, so a call site only swaps the import; the `no-raw-popups` hook rejects
 * the raw Material import. Why every popup needs this: see TextSelectionLayer (core/ui/adaptive).
 *
 * Text inside these popups is not selectable on web. They are the small, transient surfaces —
 * confirmations, menus, pickers — where that is no loss; the form dialogs and detail sheets that
 * carry real content start a scope of their own with TextSelectionLayer instead.
 */

@Composable
fun AlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: @Composable (() -> Unit)? = null,
  icon: @Composable (() -> Unit)? = null,
  title: @Composable (() -> Unit)? = null,
  text: @Composable (() -> Unit)? = null,
  shape: Shape = AlertDialogDefaults.shape,
  containerColor: Color = AlertDialogDefaults.containerColor,
  iconContentColor: Color = AlertDialogDefaults.iconContentColor,
  titleContentColor: Color = AlertDialogDefaults.titleContentColor,
  textContentColor: Color = AlertDialogDefaults.textContentColor,
  tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
  properties: DialogProperties = DialogProperties(),
) {
  M3AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = { DisableSelection(confirmButton) },
    modifier = modifier,
    dismissButton = dismissButton?.let { { DisableSelection(it) } },
    icon = icon?.let { { DisableSelection(it) } },
    title = title?.let { { DisableSelection(it) } },
    text = text?.let { { DisableSelection(it) } },
    shape = shape,
    containerColor = containerColor,
    iconContentColor = iconContentColor,
    titleContentColor = titleContentColor,
    textContentColor = textContentColor,
    tonalElevation = tonalElevation,
    properties = properties,
  )
}
