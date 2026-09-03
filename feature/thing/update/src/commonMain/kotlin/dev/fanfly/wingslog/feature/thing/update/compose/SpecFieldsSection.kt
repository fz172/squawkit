package dev.fanfly.wingslog.feature.thing.update.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.viewmodel.EditThingViewModel
import dev.fanfly.wingslog.thing.SpecField
import dev.fanfly.wingslog.thing.Thing

/**
 * The identity fields, from whatever the template declares (#729).
 *
 * Replaces `AirframeSection`, which asked for make, model, serial and tail number because those
 * were the four an airplane has. A home is asked for an address and a year built, a car for a VIN,
 * because that is what their templates say — this composable knows none of those words.
 */
@Composable
fun SpecFieldsSection(
  thing: Thing,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  val template = LocalThingTemplate.current
  val fields = template?.spec_fields.orEmpty()
  if (fields.isEmpty()) return

  val askForSerials = LocalThingCapabilities.current.component_serial_prompt

  Card(
    modifier = Modifier.padding(vertical = Spacing.small),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      // Consecutive fields the template marks `compact` share a line — serial beside tail number,
      // where both are short. Everything else takes the full width.
      val visible =
        fields.filterNot { it.key == SpecKeys.SERIAL && !askForSerials }
      var index = 0
      while (index < visible.size) {
        val field = visible[index]
        val partner = visible.getOrNull(index + 1)
          ?.takeIf { field.compact && it.compact }
        if (partner == null) {
          ThingSpecFieldInput(
            field,
            thing,
            viewModel,
            showValidationErrors,
            Modifier.fillMaxWidth()
          )
          index++
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            ThingSpecFieldInput(
              field,
              thing,
              viewModel,
              showValidationErrors,
              Modifier.weight(1f),
            )
            ThingSpecFieldInput(
              partner,
              thing,
              viewModel,
              showValidationErrors,
              Modifier.weight(1f),
            )
          }
          index += 2
        }
      }
    }
  }
}

@Composable
private fun ThingSpecFieldInput(
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

/**
 * Fields that cannot be edited after creation — what the thing IS, as opposed to what is true of it
 * today. `SpecField` has no immutability flag yet; inventing one belongs with the other §4.2 gaps
 * on #732.
 */
private val LOCKED_AFTER_CREATION =
  setOf(SpecKeys.MAKE, SpecKeys.MODEL, SpecKeys.SERIAL, SpecKeys.NAME)
