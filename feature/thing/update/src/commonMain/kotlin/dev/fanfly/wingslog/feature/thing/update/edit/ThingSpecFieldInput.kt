package dev.fanfly.wingslog.feature.thing.update.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.Thing

@Composable
internal fun ThingSpecFieldInput(
  field: SpecField,
  thing: Thing,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
  modifier: Modifier = Modifier,
) {
  val value = thing.specValue(field.key)
  SpecFieldInput(
    field = field,
    value = value,
    onValueChange = { viewModel.onSpecChanged(field.key, it) },
    modifier = modifier,
    // Make, model and serial identify the thing and are fixed once it exists, as they always were.
    editable = thing.id.isEmpty() || field.key !in LOCKED_AFTER_CREATION,
    isError = showValidationErrors && field.required && value.isBlank(),
  )
}
