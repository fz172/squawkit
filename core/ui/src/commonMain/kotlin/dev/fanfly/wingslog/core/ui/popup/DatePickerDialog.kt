package dev.fanfly.wingslog.core.ui.popup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.DatePickerDialog as M3DatePickerDialog

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
