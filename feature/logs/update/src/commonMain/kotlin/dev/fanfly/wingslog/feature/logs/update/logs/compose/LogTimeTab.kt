package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.airframe_time_hours
import wingslog.feature.logs.update.generated.resources.engine_time_hours
import wingslog.feature.logs.update.generated.resources.hours_section_description
import wingslog.feature.logs.update.generated.resources.log_tab_hours
import wingslog.feature.logs.update.generated.resources.prop_time_hours

@Composable
fun LogTimeTab(
  engineTime: String,
  onEngineTimeChange: (String) -> Unit,
  airframeTime: String,
  onAirframeTimeChange: (String) -> Unit,
  propTime: String,
  onPropTimeChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.massive),
  ) {
    LogSection(
      header = stringResource(Res.string.log_tab_hours),
      description = stringResource(Res.string.hours_section_description),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        OutlinedTextField(
          value = engineTime,
          onValueChange = onEngineTimeChange,
          label = { Text(stringResource(Res.string.engine_time_hours)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
          value = airframeTime,
          onValueChange = onAirframeTimeChange,
          label = { Text(stringResource(Res.string.airframe_time_hours)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
          value = propTime,
          onValueChange = onPropTimeChange,
          label = { Text(stringResource(Res.string.prop_time_hours)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
      }
    }
  }
}
