package dev.fanfly.wingslog.feature.logs.update.form.work

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.FormValueField
import dev.fanfly.wingslog.core.ui.common.compose.LabelledChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubComponentDropdown(
  label: String,
  /** Each option's label, and the serial it stands for. */
  options: List<LabelledChoice<String>>,
  selectedSerial: String?,
  onSelected: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel =
    options.firstOrNull { it.value == selectedSerial }?.label ?: ""

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier
  ) {
    FormValueField(
      value = selectedLabel,
      label = label,
      interactive = true,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }) {
      DisableSelection {
        options.forEach { (displayLabel, serial) ->
          DropdownMenuItem(
            text = { Text(displayLabel) },
            onClick = {
              onSelected(serial)
              expanded = false
            }
          )
        }
      }
    }
  }
}
