package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.component_engine
import wingslog.feature.logs.sharedassets.generated.resources.blade
import wingslog.feature.logs.sharedassets.generated.resources.propeller_hub
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.airframe_serial
import wingslog.feature.logs.update.generated.resources.loading_aircraft
import wingslog.feature.logs.update.generated.resources.no_engines_found
import wingslog.feature.logs.update.generated.resources.no_propeller_components_found
import wingslog.feature.logs.update.generated.resources.propeller_component

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentSection(
  aircraft: Aircraft?,
  selectedComponentType: ComponentType,
  selectedSubComponent: String?,
  onComponentTypeChange: (ComponentType) -> Unit,
  onSubComponentChange: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    // Component Type segmented button
    val componentOptions = listOf(
      ComponentType.COMPONENT_AIRFRAME,
      ComponentType.COMPONENT_ENGINE,
      ComponentType.COMPONENT_PROPELLER,
      ComponentType.COMPONENT_AVIONICS,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      componentOptions.forEachIndexed { index, option ->
        SegmentedButton(
          selected = selectedComponentType == option,
          onClick = { onComponentTypeChange(option) },
          shape = SegmentedButtonDefaults.itemShape(index = index, count = componentOptions.size),
          icon = {},
          label = { Text(option.displayName()) },
        )
      }
    }

    when (selectedComponentType) {
      ComponentType.COMPONENT_AIRFRAME -> {
        // Display aircraft serial (read-only)
        val serial = aircraft?.serial ?: ""
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant,
              shape = RoundedCornerShape(Spacing.chipCornerRadius)
            )
            .background(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
              shape = RoundedCornerShape(Spacing.chipCornerRadius)
            )
            .padding(horizontal = Spacing.large),
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = stringResource(Res.string.airframe_serial),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = serial,
            style = WingslogTypography.dataMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      ComponentType.COMPONENT_ENGINE -> {
        if (aircraft == null) {
          Text(
            text = stringResource(Res.string.loading_aircraft),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          val engines = aircraft.engine
          if (engines.isEmpty()) {
            Text(
              text = stringResource(Res.string.no_engines_found),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            val options = engines.map { engine ->
              val label = buildString {
                if (engine.make.isNotEmpty()) append(engine.make)
                if (engine.model.isNotEmpty()) {
                  if (isNotEmpty()) append(" ")
                  append(engine.model)
                }
                if (engine.serial.isNotEmpty()) append(" (${engine.serial})")
              }
              label to engine.serial
            }
            SubComponentDropdown(
              label = stringResource(CoreRes.string.component_engine),
              options = options,
              selectedSerial = selectedSubComponent,
              onSelected = onSubComponentChange,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }

      ComponentType.COMPONENT_PROPELLER -> {
        if (aircraft == null) {
          Text(
            text = stringResource(Res.string.loading_aircraft),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          // Collect all propeller components from all engines
          val options = mutableListOf<Pair<String, String>>()
          aircraft.engine.forEach { engine ->
            val prop = engine.propeller
            val hub = prop?.hub
            if (hub?.serial?.isNotEmpty() == true) {
              val label = buildString {
                append(stringResource(wingslog.feature.logs.sharedassets.generated.resources.Res.string.propeller_hub))
                if (hub.make.isNotEmpty()) append(" - ${hub.make}")
                if (hub.model.isNotEmpty()) append(" ${hub.model}")
                append(" (${hub.serial})")
              }
              options.add(label to hub.serial)
            }
            prop?.blades?.forEach { blade ->
              if (blade.serial.isNotEmpty()) {
                val label = buildString {
                  append(stringResource(wingslog.feature.logs.sharedassets.generated.resources.Res.string.blade))
                  if (blade.make.isNotEmpty()) append(" - ${blade.make}")
                  if (blade.model.isNotEmpty()) append(" ${blade.model}")
                  append(" (${blade.serial})")
                }
                options.add(label to blade.serial)
              }
            }
          }

          if (options.isEmpty()) {
            Text(
              text = stringResource(Res.string.no_propeller_components_found),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            SubComponentDropdown(
              label = stringResource(Res.string.propeller_component),
              options = options,
              selectedSerial = selectedSubComponent,
              onSelected = onSubComponentChange,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }

      else -> {
        // UNKNOWN — no sub-component
      }
    }
  }
}