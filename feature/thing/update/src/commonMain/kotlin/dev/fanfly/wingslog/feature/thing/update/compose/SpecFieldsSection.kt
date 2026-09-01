package dev.fanfly.wingslog.feature.thing.update.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.SpecKeys
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.viewmodel.EditThingViewModel
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
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(modifier = Modifier.padding(Spacing.medium)) {
      fields.forEach { field ->
        // A serial is the one spec field a template can declare and the capability still remove:
        // no vehicle preset prompts for one at creation (PRD §4.8). Hidden means not required —
        // EditThingUiState relaxes the same way, or the form would block on an invisible field.
        if (field.key == SpecKeys.SERIAL && !askForSerials) return@forEach

        val value = thing.specValue(field.key)
        FormTextField(
          value = value,
          onValueChange = { viewModel.onSpecChanged(field.key, it) },
          label = field.label,
          placeholder = field.placeholder.takeIf { it.isNotEmpty() },
          // Make and model identify the thing and are fixed once it exists, as they always were.
          editable = thing.id.isEmpty() || field.key !in LOCKED_AFTER_CREATION,
          isError = showValidationErrors && field.required && value.isBlank(),
          keyboardOptions = if (field.is_identifier) {
            KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
          } else {
            KeyboardOptions.Default
          },
        )
      }
    }
  }
}

/**
 * Fields that cannot be edited after creation.
 *
 * Carried over rather than derived: `SpecField` has no immutability flag, and the two keys that
 * were locked before this change are make and model. Inventing a schema field for it belongs with
 * the other §4.2 gaps recorded on #732.
 */
private val LOCKED_AFTER_CREATION = setOf(SpecKeys.MAKE, SpecKeys.MODEL)
