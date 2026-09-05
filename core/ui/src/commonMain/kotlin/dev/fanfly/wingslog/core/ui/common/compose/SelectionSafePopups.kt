package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.DatePickerDialog as M3DatePickerDialog
import androidx.compose.material3.DropdownMenu as M3DropdownMenu
import androidx.compose.material3.ModalBottomSheet as M3ModalBottomSheet

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(),
  sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
  sheetGesturesEnabled: Boolean = true,
  shape: Shape = BottomSheetDefaults.ExpandedShape,
  containerColor: Color = BottomSheetDefaults.ContainerColor,
  contentColor: Color = contentColorFor(containerColor),
  tonalElevation: Dp = 0.dp,
  scrimColor: Color = BottomSheetDefaults.ScrimColor,
  dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
  contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
  properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
  content: @Composable ColumnScope.() -> Unit,
) {
  M3ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    sheetState = sheetState,
    sheetMaxWidth = sheetMaxWidth,
    sheetGesturesEnabled = sheetGesturesEnabled,
    shape = shape,
    containerColor = containerColor,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    scrimColor = scrimColor,
    dragHandle = dragHandle?.let { { DisableSelection(it) } },
    contentWindowInsets = contentWindowInsets,
    properties = properties,
  ) {
    DisableSelection { content() }
  }
}

@Composable
fun DropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  offset: DpOffset = DpOffset(0.dp, 0.dp),
  scrollState: ScrollState = rememberScrollState(),
  properties: PopupProperties = PopupProperties(focusable = true),
  shape: Shape = MenuDefaults.shape,
  containerColor: Color = MenuDefaults.containerColor,
  tonalElevation: Dp = MenuDefaults.TonalElevation,
  shadowElevation: Dp = MenuDefaults.ShadowElevation,
  border: BorderStroke? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  M3DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    offset = offset,
    scrollState = scrollState,
    properties = properties,
    shape = shape,
    containerColor = containerColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
    border = border,
  ) {
    DisableSelection { content() }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: @Composable (() -> Unit)? = null,
  shape: Shape = DatePickerDefaults.shape,
  tonalElevation: Dp = DatePickerDefaults.TonalElevation,
  colors: DatePickerColors = DatePickerDefaults.colors(),
  properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  content: @Composable ColumnScope.() -> Unit,
) {
  M3DatePickerDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = { DisableSelection(confirmButton) },
    modifier = modifier,
    dismissButton = dismissButton?.let { { DisableSelection(it) } },
    shape = shape,
    tonalElevation = tonalElevation,
    colors = colors,
    properties = properties,
  ) {
    DisableSelection { content() }
  }
}
