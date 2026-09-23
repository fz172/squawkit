package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.core.template.ComponentField
import dev.fanfly.wingslog.core.template.ComponentRow
import dev.fanfly.wingslog.core.template.valueOf
import dev.fanfly.wingslog.core.ui.form.FormTextField
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.thing.update.generated.resources.make
import wingslog.feature.thing.update.generated.resources.model
import wingslog.feature.thing.update.generated.resources.serial
import wingslog.feature.thing.update.generated.resources.Res as UpdateRes

@Composable
internal fun ComponentFieldInput(
  row: ComponentRow,
  field: ComponentField,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
  modifier: Modifier = Modifier,
  labelOverride: String? = null,
  dense: Boolean = false,
) {
  FormTextField(
    value = row.component?.valueOf(field)
      .orEmpty(),
    onValueChange = { viewModel.onComponentFieldChanged(row.path, field, it) },
    // Just "Make" — the heading above already said which component this is, and "Engine 2 Make"
    // on every input reads as noise once there are three of them.
    label = labelOverride ?: field.caption(),
    modifier = modifier,
    dense = dense,
    textStyle = if (dense) {
      MaterialTheme.typography.bodyMedium
    } else {
      MaterialTheme.typography.bodyLarge
    },
    isError = field == ComponentField.SERIAL &&
      showValidationErrors &&
      row.component?.serial?.isBlank() == true,
    keyboardOptions = if (field == ComponentField.SERIAL) {
      KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
    } else {
      KeyboardOptions.Default
    },
  )
}

/**
 * The three field captions, from `strings.xml` rather than the template.
 *
 * Make, model and serial are the same three words for every component of every kind — they name
 * the field, not the domain, so a template declaring them would be six presets repeating one
 * vocabulary. The domain word is the slot label this is appended to.
 */
@Composable
private fun ComponentField.caption(): String = when (this) {
  ComponentField.MAKE -> stringResource(UpdateRes.string.make)
  ComponentField.MODEL -> stringResource(UpdateRes.string.model)
  ComponentField.SERIAL -> stringResource(UpdateRes.string.serial)
}
