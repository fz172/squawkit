package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.datamanager.pickerMillisToDate
import dev.fanfly.wingslog.thing.MeterDef
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.core.sharedassets.generated.resources.select_date
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_prefix_at
import wingslog.feature.tasks.update.generated.resources.initial_due_subtitle
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The first-due field: a date row for a dated schedule, a meter reading for a metered one, and a
 * clear affordance once something is set. Setting a value is what turns the override on — there
 * is no switch to remember to flip, and an empty field is simply no override.
 */
@Composable
internal fun FirstDueCard(
  dated: Boolean,
  controls: InitialDueControls,
  meter: MeterDef?,
  meterUnit: String,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        MaterialTheme.colorScheme.outlineVariant,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    if (dated) {
      val dateStr = controls.forcedDateMillis?.pickerMillisToDate()
        ?.toDisplayFormat()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Spacing.cardCornerRadius))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
          .clickable(role = Role.Button) { controls.onDateClick() }
          .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Icon(
          Icons.Default.CalendarToday,
          contentDescription = null,
          modifier = Modifier.size(Spacing.large),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          dateStr ?: stringResource(CoreRes.string.select_date),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (dateStr != null) FontWeight.Bold else FontWeight.Normal,
          color = if (dateStr != null) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        if (dateStr != null) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(CoreRes.string.remove),
            modifier = Modifier
              .size(Spacing.xLarge)
              .clip(RoundedCornerShape(Spacing.smallCornerRadius))
              .clickable {
                controls.onForcedDateMillisChange(null)
                controls.onForceOverrideDateChange(false)
              }
              .padding(Spacing.extraSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      IntervalNumberInput(
        value = controls.forcedEngineHours,
        onChange = { v ->
          val filtered = v.filter { c -> c.isDigit() || c == '.' }
          controls.onForcedEngineHoursChange(filtered)
          controls.onForceOverrideEngineChange(
            filtered.toFloatOrNull()
              ?.let { it > 0f } == true)
        },
        suffix = meterUnit,
        prefix = stringResource(Res.string.adj_reschedule_prefix_at),
        keyboard = if (meter?.decimal != false) KeyboardType.Decimal else KeyboardType.Number,
      )
    }
    Text(
      stringResource(Res.string.initial_due_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
