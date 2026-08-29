package dev.fanfly.wingslog.feature.aircraft.update.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.update.viewmodel.EditThingViewModel
import dev.fanfly.wingslog.thing.Thing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.aircraft.update.generated.resources.Res
import wingslog.feature.aircraft.update.generated.resources.make
import wingslog.feature.aircraft.update.generated.resources.model
import wingslog.feature.aircraft.update.generated.resources.serial
import wingslog.feature.aircraft.update.generated.resources.tail_number

@Composable
fun AirframeSection(
  thing: Thing,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
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
    Column(modifier = Modifier.padding(Spacing.medium)) {

      // --- Make Number ---
      FormTextField(
        value = thing.make, // Read from ViewModel
        onValueChange = { viewModel.onMakeChanged(it) }, // Update ViewModel
        label = stringResource(Res.string.make),
        editable = thing.id == "",
        isError = showValidationErrors && thing.make.isBlank()
      )
      // --- Model Number ---
      FormTextField(
        value = thing.model, // Read from ViewModel
        onValueChange = { viewModel.onModelChanged(it) }, // Update ViewModel
        label = stringResource(Res.string.model),
        editable = thing.id == "",
        isError = showValidationErrors && thing.model.isBlank()
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.large)
      ) {
        // --- Serial Number ---
        FormTextField(
          value = thing.serial, // Read from ViewModel
          onValueChange = { viewModel.onSerialChanged(it) }, // Update ViewModel
          label = stringResource(Res.string.serial),
          modifier = Modifier.weight(1f), // Takes up 50%
          editable = thing.id == "",
          isError = showValidationErrors && thing.serial.isBlank(),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        // --- Tail Number ---
        FormTextField(
          value = thing.tail_number, // Read from ViewModel
          onValueChange = { viewModel.onTailNumberChanged(it) }, // Update ViewModel
          label = stringResource(Res.string.tail_number),
          modifier = Modifier.weight(1f), // Takes up 50%
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
      }
    }
  }
}
