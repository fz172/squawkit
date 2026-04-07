package dev.fanfly.wingslog.feature.maintenance.update.aircraft.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Engine
import dev.fanfly.wingslog.aircraft.PropellerHub
import dev.fanfly.wingslog.core.ui.common.compose.DashedButton
import dev.fanfly.wingslog.feature.maintenance.update.aircraft.viewmodel.EditAircraftViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.maintenance.sharedassets.generated.resources.blade_serial_numbers
import wingslog.feature.maintenance.sharedassets.generated.resources.blade_with_index
import wingslog.feature.maintenance.sharedassets.generated.resources.engine_with_index
import wingslog.feature.maintenance.sharedassets.generated.resources.propeller_hub
import wingslog.feature.maintenance.update.generated.resources.Res
import wingslog.feature.maintenance.update.generated.resources.add_blade
import wingslog.feature.maintenance.update.generated.resources.make
import wingslog.feature.maintenance.update.generated.resources.model
import wingslog.feature.maintenance.update.generated.resources.remove_blade
import wingslog.feature.maintenance.update.generated.resources.remove_engine
import wingslog.feature.maintenance.update.generated.resources.serial
import wingslog.feature.maintenance.sharedassets.generated.resources.Res as SharedRes

@Composable
fun EngineSection(
  engineIndex: Int,
  engine: Engine,
  viewModel: EditAircraftViewModel,
  showValidationErrors: Boolean,
) {
  Card(
    modifier = Modifier.padding(vertical = 8.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  ) {

    Column(modifier = Modifier.padding(12.dp)) {
      Box(modifier = Modifier.fillMaxWidth()) {
        Text(
          stringResource(SharedRes.string.engine_with_index, engineIndex + 1),
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.align(Alignment.CenterStart)
        )
        IconButton(
          onClick = { viewModel.onRemoveEngine(engineIndex) },
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(Res.string.remove_engine),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      InputField(
        label = stringResource(Res.string.make),
        value = engine.make,
        isError = showValidationErrors && engine.make.isBlank()
      ) {
        viewModel.onEngineMakeChanged(engineIndex, it)
      }

      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InputField(
          label = stringResource(Res.string.model),
          value = engine.model,
          modifier = Modifier.weight(1f),
          isError = showValidationErrors && engine.model.isBlank()
        ) {
          viewModel.onEngineModelChanged(engineIndex, it)
        }
        InputField(
          label = stringResource(Res.string.serial),
          value = engine.serial,
          modifier = Modifier.weight(1f),
          isError = showValidationErrors && engine.serial.isBlank(),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        ) {
          viewModel.onEngineSerialChanged(engineIndex, it)
        }
      }

      // Propeller Section
      Text(
        stringResource(SharedRes.string.propeller_hub),
        style = MaterialTheme.typography.labelSmall
      )
      val hub = engine.propeller?.hub ?: PropellerHub()
      InputField(
        label = stringResource(Res.string.make),
        value = hub.make,

        isError = showValidationErrors && hub.make.isBlank()
      ) {
        viewModel.onPropellerHubMakeChanged(engineIndex, it)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InputField(
          label = stringResource(Res.string.model),
          value = hub.model,
          modifier = Modifier.weight(1f),
          isError = showValidationErrors && hub.model.isBlank()
        ) {
          viewModel.onPropellerHubModelChanged(engineIndex, it)
        }
        InputField(
          label = stringResource(
            Res.string.serial,
            ""
          ),
          value = hub.serial,
          modifier = Modifier.weight(1f),
          isError = showValidationErrors && hub.serial.isBlank()
        ) {
          viewModel.onPropellerHubSerialChanged(engineIndex, it)
        }
      }


      // Blade Serial Numbers - Dynamic List
      Text(stringResource(SharedRes.string.blade_serial_numbers))
      val blades = engine.propeller?.blades ?: emptyList()
      // Chunked(2) allows us to create rows of 2 for that 50/50 look
      blades.withIndex().chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          pair.forEach { (bladeIndex, blade) ->
            InputField(
              label = stringResource(
                SharedRes.string.blade_with_index,
                bladeIndex + 1
              ),
              value = blade.serial,
              modifier = Modifier.weight(1f),
              trailingIcon = {
                IconButton(onClick = {
                  viewModel.onRemoveBlade(
                    engineIndex,
                    bladeIndex
                  )
                }) {
                  Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.remove_blade)
                  )
                }
              },
              isError = showValidationErrors && blade.serial.isBlank(),
              keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            ) {
              viewModel.onPropellerBladeSerialChanged(engineIndex, bladeIndex, it)
            }
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashedButton(
          label = stringResource(Res.string.add_blade),
          modifier = Modifier
            .weight(1f)
            .padding(vertical = 8.dp),

          onClick = { viewModel.onAddBlade(engineIndex) })
        Spacer(Modifier.weight(1f))
      }
    }
  }
}