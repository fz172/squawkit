package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.layout.fillMaxWidth
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubComponentDropdown(
  label: String,
  options: List<Pair<String, String>>, // display label to serial
  selectedSerial: String?,
  onSelected: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = options.firstOrNull { it.second == selectedSerial }?.first ?: ""

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier
  ) {
    FormValueField(
      value = selectedLabel,
      label = label,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .fillMaxWidth()
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
