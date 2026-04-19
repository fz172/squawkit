package dev.fanfly.wingslog.feature.logs.update.aircraft.compose

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.update.aircraft.viewmodel.EditAircraftViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.make
import wingslog.feature.logs.update.generated.resources.model
import wingslog.feature.logs.update.generated.resources.serial
import wingslog.feature.logs.update.generated.resources.tail_number

@Composable
fun AirframeSection(
  aircraft: Aircraft, viewModel: EditAircraftViewModel, showValidationErrors: Boolean,
) {
  Card(
    modifier = Modifier.padding(vertical = Spacing.small),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(modifier = Modifier.padding(Spacing.medium)) {

      // --- Make Number ---
      InputField(
        value = aircraft.make, // Read from ViewModel
        onValueChange = { viewModel.onMakeChanged(it) }, // Update ViewModel
        label = stringResource(Res.string.make),
        enabled = aircraft.id == "",
        isError = showValidationErrors && aircraft.make.isBlank()
      )
      // --- Model Number ---
      InputField(
        value = aircraft.model, // Read from ViewModel
        onValueChange = { viewModel.onModelChanged(it) }, // Update ViewModel
        label = stringResource(Res.string.model),
        enabled = aircraft.id == "",
        isError = showValidationErrors && aircraft.model.isBlank()
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.large)
      ) {
        // --- Serial Number ---
        InputField(
          value = aircraft.serial, // Read from ViewModel
          onValueChange = { viewModel.onSerialChanged(it) }, // Update ViewModel
          label = stringResource(Res.string.serial),
          modifier = Modifier.weight(1f), // Takes up 50%
          enabled = aircraft.id == "",
          isError = showValidationErrors && aircraft.serial.isBlank(),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        // --- Tail Number ---
        InputField(
          value = aircraft.tail_number, // Read from ViewModel
          onValueChange = { viewModel.onTailNumberChanged(it) }, // Update ViewModel
          label = stringResource(Res.string.tail_number),
          modifier = Modifier.weight(1f), // Takes up 50%
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
      }
    }
  }
}