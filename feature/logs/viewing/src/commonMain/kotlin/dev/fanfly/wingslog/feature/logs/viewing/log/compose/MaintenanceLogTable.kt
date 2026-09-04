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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.componentTypesApply
import dev.fanfly.wingslog.core.template.formatMeterValue
import dev.fanfly.wingslog.core.template.primaryReading
import dev.fanfly.wingslog.core.ui.common.compose.jumpTargetHighlight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.model.ListRow
import dev.fanfly.wingslog.feature.ads.viewing.AdSlot
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.unknown_date
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
  rows: List<ListRow<MaintenanceLog>>,
  onLogClick: (MaintenanceLog) -> Unit,
  listState: LazyListState = rememberLazyListState(),
  /** See [MaintenanceLogListContent]'s parameter of the same name. */
  scrollToLogId: String? = null,
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
        items(
          rows,
          key = { row ->
            when (row) {
              is ListRow.Ad -> "ad-${row.slotIndex}"
              is ListRow.Item -> row.value.id
            }
          },
        ) { row ->
          when (row) {
            // A band between rows, not a row. It keeps the table's horizontal insets but adopts
            // none of its column rules, striping or row height — a pilot scanning a column of dates
            // must never have to parse an ad as data (PRD §6.5, F16).
            is ListRow.Ad -> AdSlot(
              surface = AdSurface.LOGS,
              slotIndex = row.slotIndex,
              modifier = Modifier.padding(
                horizontal = Spacing.medium,
                vertical = Spacing.small,
              ),
            )

            is ListRow.Item -> LogRow(
              log = row.value,
              onClick = { onLogClick(row.value) },
              isJumpTarget = row.value.id == scrollToLogId,
            )
          }
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
    // Gone, not blank, where the pill is — see [LogComponentBadge].
    if (componentTypesApply) HeaderCell("Component", W_COMPONENT)
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
  isJumpTarget: Boolean = false,
) {
  val dateStr = log.timestamp?.toLocalDate()
    ?.toDisplayFormat()
    ?: stringResource(SharedRes.string.unknown_date)
  // Same fix as the card: the first declared meter this log recorded, rather than a switch on
  // `component_type` across three aviation fields (#761).
  val template = LocalThingTemplate.current
  val primary = template.primaryReading(log)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      // Flat, undivided by rounded corners like the rest of this table — RectangleShape rather than
      // jumpTargetHighlight's card-shaped default.
      .jumpTargetHighlight(active = isJumpTarget, shape = RectangleShape)
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
    if (componentTypesApply) {
      Box(
        modifier = Modifier.weight(W_COMPONENT)
          .padding(end = Spacing.medium)
      ) {
        LogComponentBadge(log.component_type)
      }
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
      text = primary
        ?.let { (meter, value) -> template.formatMeterValue(meter.key, value) }
        ?: "—",
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

