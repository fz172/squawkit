package dev.fanfly.wingslog.feature.logs.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.template.primaryReading
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.squawk.dashboard.RailComponentTypeBadge
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksRes

@Composable
fun RecentLogRow(log: MaintenanceLog, onClick: () -> Unit) {
  val date = log.timestamp?.toLocalDate()
    ?.toDisplayFormat()
    ?: stringResource(TasksRes.string.unknown_date)
  // The first meter this template declares that the log recorded. Switching on `component_type`
  // across the three aviation hour fields left a car's log — which records an odometer — matching
  // no branch and showing nothing (#761).
  val primary = LocalThingTemplate.current.primaryReading(log)
  // The pill, the description and the date sit on one baseline, so the words read along a single
  // line however tall the pill's padding makes it. The Box carries the row's height and centres
  // that baseline group, which a Row would otherwise pin to the top of the 64.dp minimum.
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 64.dp)
      .clickable(onClick = onClick)
      .padding(vertical = Spacing.small),
    contentAlignment = Alignment.CenterStart,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      // Same rule as the log card: the pill names an aviation part, so a home or a car gets none.
      if (componentTypesApply && log.component_type != ComponentType.COMPONENT_UNKNOWN) {
        RailComponentTypeBadge(
          type = log.component_type,
          modifier = Modifier.alignByBaseline(),
        )
      }
      Text(
        text = log.work_description.asSummaryLine(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
          .alignByBaseline(),
      )
      Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.alignByBaseline(),
      ) {
        Text(
          date,
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (primary != null) {
          Text(
            LocalThingTemplate.current.formatMeterValue(
              primary.meter.key,
              primary.value
            ),
            style = WingslogTypography.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
          )
        }
      }
    }
  }
}
