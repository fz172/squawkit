package dev.fanfly.wingslog.feature.thing.update.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.customSpecs
import dev.fanfly.wingslog.core.ui.common.compose.DashedButton
import dev.fanfly.wingslog.core.ui.common.compose.FormKeyboard
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.viewmodel.EditThingViewModel
import dev.fanfly.wingslog.thing.Thing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.thing.update.generated.resources.custom_field_add
import wingslog.feature.thing.update.generated.resources.custom_field_name
import wingslog.feature.thing.update.generated.resources.custom_field_value
import wingslog.feature.thing.update.generated.resources.custom_fields_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.thing.update.generated.resources.Res as UpdateRes

/**
 * Fields the user names themselves, up to the template's `custom_spec_fields` allowance.
 *
 * The one place in the app where a LABEL is user input. `custom` is why: it declares no spec fields
 * because what someone tracks under it — a espresso machine's water hardness, a generator's fuel
 * mix — cannot be known when the preset is authored. The template still decides how many.
 */
@Composable
fun CustomFieldsSection(
  thing: Thing,
  viewModel: EditThingViewModel,
) {
  val limit = LocalThingTemplate.current?.custom_spec_fields ?: 0
  if (limit <= 0) return
  val fields = thing.customSpecs()

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
      Text(
        stringResource(UpdateRes.string.custom_fields_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      fields.forEach { field ->
        Row(
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FormTextField(
            value = field.label.trim(),
            onValueChange = {
              viewModel.onCustomFieldChanged(field.key, it, field.value_)
            },
            label = stringResource(UpdateRes.string.custom_field_name),
            modifier = Modifier.weight(1f),
            keyboardOptions = FormKeyboard.WordsNext,
          )
          FormTextField(
            value = field.value_,
            onValueChange = {
              viewModel.onCustomFieldChanged(field.key, field.label, it)
            },
            label = stringResource(UpdateRes.string.custom_field_value),
            modifier = Modifier.weight(1f),
            keyboardOptions = FormKeyboard.WordsDone,
          )
          IconButton(onClick = { viewModel.onRemoveCustomField(field.key) }) {
            Icon(
              Icons.Default.Close,
              contentDescription = stringResource(CoreRes.string.remove),
            )
          }
        }
      }
      // Gone rather than disabled once the allowance is spent: a button that never works is a
      // question the user keeps asking.
      if (fields.size < limit) {
        DashedButton(
          label = stringResource(UpdateRes.string.custom_field_add),
          onClick = { viewModel.onAddCustomField() },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
