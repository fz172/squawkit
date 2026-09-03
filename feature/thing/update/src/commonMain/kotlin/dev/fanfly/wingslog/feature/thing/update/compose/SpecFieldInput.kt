package dev.fanfly.wingslog.feature.thing.update.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.common.compose.FormValueField
import dev.fanfly.wingslog.thing.SpecField

/**
 * One declared field: a picker when the template lists `options`, a numeric keypad when it says
 * `numeric`, an all-caps keyboard when it says `is_identifier`, word capitalisation when it says
 * `title_case`. Shared by both forms (#739).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpecFieldInput(
  field: SpecField,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  editable: Boolean = true,
  isError: Boolean = false,
) {
  if (field.options.isEmpty() || !editable) {
    FormTextField(
      value = value,
      onValueChange = onValueChange,
      label = field.label,
      modifier = modifier,
      placeholder = field.placeholder.takeIf { it.isNotEmpty() },
      editable = editable,
      isError = isError,
      keyboardOptions = field.keyboardOptions(),
    )
    return
  }
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier,
  ) {
    FormValueField(
      value = value,
      label = field.label,
      isError = isError,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.fillMaxWidth()
        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
    )
    // No blank entry: an "unset" row is an answer nobody means to give.
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }) {
      field.options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option) },
          onClick = {
            onValueChange(option)
            expanded = false
          },
        )
      }
    }
  }
}

/** Numeric beats identifier: a keypad has no capitalisation to apply anyway. */
private fun SpecField.keyboardOptions(): KeyboardOptions = when {
  numeric -> KeyboardOptions(keyboardType = KeyboardType.Number)
  is_identifier -> KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
  title_case -> KeyboardOptions(capitalization = KeyboardCapitalization.Words)
  else -> KeyboardOptions.Default
}
