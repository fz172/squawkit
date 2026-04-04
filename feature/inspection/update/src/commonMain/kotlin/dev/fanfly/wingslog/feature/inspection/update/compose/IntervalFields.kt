package dev.fanfly.wingslog.feature.inspection.update.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.every
import wingslog.core.ui.generated.resources.or
import wingslog.feature.inspection.update.generated.resources.inspection_due_warning
import wingslog.feature.inspection.update.generated.resources.inspection_interval_description
import wingslog.feature.inspection.update.generated.resources.interval_hours
import wingslog.feature.inspection.update.generated.resources.interval_hours_placeholder
import wingslog.feature.inspection.update.generated.resources.interval_months
import wingslog.feature.inspection.update.generated.resources.interval_months_placeholder
import wingslog.feature.inspection.update.generated.resources.intervals
import wingslog.core.ui.generated.resources.Res as CoreUiRes
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes

@Composable
fun IntervalFields(
  intervalMonths: String,
  onMonthsChange: (String) -> Unit,
  intervalHours: String,
  onHoursChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      stringResource(InspectionRes.string.intervals),
      style = MaterialTheme.typography.labelLarge
    )
    Spacer(modifier = Modifier.height(Spacing.small))
    Row(
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = intervalMonths,
        onValueChange = { onMonthsChange(it.filter { c -> c.isDigit() }) },
        label = { Text(stringResource(InspectionRes.string.interval_months)) },
        placeholder = { Text(stringResource(InspectionRes.string.interval_months_placeholder)) },
        modifier = Modifier.weight(1f),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        prefix = {
          Text(
            stringResource(CoreUiRes.string.every),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      )
      Text(
        stringResource(CoreUiRes.string.or),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.outline
      )
      OutlinedTextField(
        value = intervalHours,
        onValueChange = { onHoursChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(stringResource(InspectionRes.string.interval_hours)) },
        placeholder = { Text(stringResource(InspectionRes.string.interval_hours_placeholder)) },
        modifier = Modifier.weight(1f),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        prefix = {
          Text(
            stringResource(CoreUiRes.string.every),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      )
    }

    if (intervalMonths.isNotBlank() || intervalHours.isNotBlank()) {
      Spacer(modifier = Modifier.height(Spacing.small))
      Text(
        stringResource(InspectionRes.string.inspection_due_warning),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
      )
    } else {
      Spacer(modifier = Modifier.height(Spacing.small))
      Text(
        stringResource(InspectionRes.string.inspection_interval_description),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
      )
    }
  }
}
