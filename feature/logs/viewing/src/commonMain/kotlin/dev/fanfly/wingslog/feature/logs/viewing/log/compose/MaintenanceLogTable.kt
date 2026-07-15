package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.formatToOneDecimalPlace
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.viewing.generated.resources.hours_abbr_value
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as SharedRes

// Shared column weights so the header and every row stay aligned.
private const val W_DATE = 0.9f
private const val W_COMPONENT = 0.9f
private const val W_DESC = 2.2f
private const val W_HOURS = 0.7f
private const val W_TECH = 1.1f

/**
 * Tabular presentation of maintenance logs for wide screens (the adaptive shell's Logs section on
 * EXPANDED/LARGE). On phone/rail tiers the list falls back to [MaintenanceLogCard]s. A fixed header
 * sits above a scrolling [LazyColumn]; each row is clickable and opens the same detail overlay.
 */
@Composable
fun MaintenanceLogTable(
  logs: List<MaintenanceLog>,
  onLogClick: (MaintenanceLog) -> Unit,
  listState: LazyListState = rememberLazyListState(),
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      HeaderRow()
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Spacing.large),
      ) {
        items(logs, key = { it.id }) { log ->
          LogRow(
            log = log,
            onClick = { onLogClick(log) })
          HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(
              alpha = 0.4f
            )
          )
        }
      }
    }
  }
}

@Composable
private fun HeaderRow() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    HeaderCell("Date", W_DATE)
    HeaderCell("Component", W_COMPONENT)
    HeaderCell("Description", W_DESC)
    HeaderCell("Hours", W_HOURS, TextAlign.End)
    HeaderCell("Technician", W_TECH)
    Box(modifier = Modifier.width(28.dp)) // chevron column
  }
}

@Composable
private fun RowScope.HeaderCell(
  text: String,
  weight: Float,
  align: TextAlign = TextAlign.Start
) {
  Text(
    text = text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = align,
    modifier = Modifier.weight(weight)
      .padding(end = Spacing.medium),
  )
}

@Composable
private fun LogRow(
  log: MaintenanceLog,
  onClick: () -> Unit,
) {
  val dateStr = log.timestamp?.toLocalDate()
    ?.toDisplayFormat()
    ?: stringResource(SharedRes.string.unknown_date)
  val hours = log.primaryTableHours()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = dateStr,
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(W_DATE)
        .padding(end = Spacing.medium),
    )
    Box(
      modifier = Modifier.weight(W_COMPONENT)
        .padding(end = Spacing.medium)
    ) {
      ComponentTypeBadge(log.component_type)
    }
    Text(
      text = log.work_description,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(W_DESC)
        .padding(end = Spacing.medium),
    )
    Text(
      text = if (hours > 0.0) {
        stringResource(
          MaintenanceRes.string.hours_abbr_value,
          hours.formatToOneDecimalPlace()
        )
      } else {
        "—"
      },
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(W_HOURS)
        .padding(end = Spacing.medium),
    )
    Text(
      text = log.technician?.name?.takeIf { it.isNotBlank() } ?: "—",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(W_TECH)
        .padding(end = Spacing.medium),
    )
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(28.dp),
    )
  }
}

private fun MaintenanceLog.primaryTableHours(): Double = when (component_type) {
  ComponentType.COMPONENT_ENGINE -> engine_hour
  ComponentType.COMPONENT_AIRFRAME -> airframe_time
  ComponentType.COMPONENT_PROPELLER -> prop_time
  else -> engine_hour.takeIf { it > 0.0 } ?: airframe_time.takeIf { it > 0.0 }
  ?: prop_time
}
