package dev.fanfly.wingslog.feature.inspection.update.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.select_date
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes
import wingslog.feature.inspection.update.generated.resources.force_due_engine_hours
import wingslog.feature.inspection.update.generated.resources.force_overrides_safety
import wingslog.feature.inspection.update.generated.resources.override_next_due_date
import wingslog.feature.inspection.update.generated.resources.override_next_due_engine

@Composable
fun ForcedOverrideFields(
  forceOverrideEngine: Boolean,
  onForceOverrideEngineChange: (Boolean) -> Unit,
  forcedEngineHours: String,
  onForcedEngineHoursChange: (String) -> Unit,
  forceOverrideDate: Boolean,
  onForceOverrideDateChange: (Boolean) -> Unit,
  forcedDateMillis: Long?,
  onDateClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      stringResource(InspectionRes.string.force_overrides_safety),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.error
    )
    Spacer(modifier = Modifier.height(Spacing.medium))

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(role = Role.Checkbox) { onForceOverrideEngineChange(!forceOverrideEngine) },
      verticalAlignment = Alignment.CenterVertically
    ) {
      Checkbox(
        checked = forceOverrideEngine,
        onCheckedChange = null // Click handled by Row
      )
      Text(stringResource(InspectionRes.string.override_next_due_engine))
    }
    if (forceOverrideEngine) {
      OutlinedTextField(
        value = forcedEngineHours,
        onValueChange = { onForcedEngineHoursChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(stringResource(InspectionRes.string.force_due_engine_hours)) },
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
      )
      Spacer(modifier = Modifier.height(Spacing.medium))
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(role = Role.Checkbox) { onForceOverrideDateChange(!forceOverrideDate) },
      verticalAlignment = Alignment.CenterVertically
    ) {
      Checkbox(
        checked = forceOverrideDate,
        onCheckedChange = null // Click handled by Row
      )
      Text(stringResource(InspectionRes.string.override_next_due_date))
    }
    if (forceOverrideDate) {
      OutlinedCard(
        onClick = onDateClick,
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
      ) {
        Row(
          modifier = Modifier.padding(Spacing.medium),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.DateRange, contentDescription = null)
          Spacer(modifier = Modifier.width(Spacing.small))
          val dateText = forcedDateMillis?.let {
            Instant.fromEpochMilliseconds(it)
              .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
          } ?: stringResource(CoreRes.string.select_date)
          Text(dateText)
        }
      }
    }
  }
}
