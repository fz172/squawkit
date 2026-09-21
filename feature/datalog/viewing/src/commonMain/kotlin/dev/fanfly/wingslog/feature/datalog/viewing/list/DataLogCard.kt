package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.datetime.toDayOfMonth
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.common.compose.TimelineRow
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_ground_run
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_recorded_as
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_series_count
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_tail_mismatch

/**
 * One entry of the archive's timeline (PRD R34), under its month header: the start time in the
 * gutter, day and route or ground run, then duration · series. A tail mismatch keeps its badge and
 * says what the file was recorded as — the badge alone says something is wrong, not what.
 */
@Composable
fun DataLogCard(
  row: DataLogRow,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  /** Whether the spine reaches the entry directly above / below this one. */
  connectsUp: Boolean = false,
  connectsDown: Boolean = false,
  /** The newest log, whose dot is lit. */
  isLatest: Boolean = false,
  /** Wide layouts add the source product to the metadata line. */
  showDetails: Boolean = true,
) {
  val details = buildList {
    add(formatDuration(row.durationSeconds))
    add(stringResource(Res.string.data_log_series_count, row.seriesCount))
    if (showDetails && row.product.isNotBlank()) add(row.product)
  }

  TimelineRow(
    gutter = row.startLocal.time.toClockText(),
    modifier = modifier,
    connectsUp = connectsUp,
    connectsDown = connectsDown,
    lit = isLatest,
    onClick = onClick,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = row.titleText(stringResource(Res.string.data_log_ground_run), underMonthHeader = true),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
      // A data-integrity warning, not decoration: the file says it belongs to another thing.
      if (row.identityMismatch) {
        StatusChip(
          label = stringResource(Res.string.data_log_tail_mismatch),
          tier = StatusTier.CAUTION,
        )
      }
    }
    Text(
      text = details.joinToString(" · "),
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (row.identityMismatch && row.identity.isNotBlank()) {
      Text(
        text = stringResource(Res.string.data_log_recorded_as, row.identity),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.statusColors.caution.accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * "Sep 02, 2026 · KPAO", or the date alone for an airborne log with no ident; ground runs say so.
 * [underMonthHeader] shortens the date to "Sep 2" where a month header already carries the rest.
 */
fun DataLogRow.titleText(groundRun: String, underMonthHeader: Boolean = false): String {
  val date = if (underMonthHeader) {
    startLocal.date.toDayOfMonth()
  } else {
    startLocal.date.toDisplayFormat(numberOnly = false)
  }
  val route =
    if (airborne && startLocationIdent.isNotBlank()) startLocationIdent
    else if (airborne) "" else groundRun
  return if (route.isEmpty()) date else "$date · $route"
}

/** A run of consecutive rows that fall in one month, newest first as the list already is. */
data class DataLogMonth(
  /** First day of the month. */
  val month: LocalDate,
  val rows: List<DataLogRow>,
) {
  /** Keyed by its first row, so a month that heads two runs still has two distinct keys. */
  val key: String get() = "month-${rows.first().id.value_}"
}

/** Groups [this] under month headers without reordering: a header wherever the month changes. */
fun List<DataLogRow>.byMonth(): List<DataLogMonth> =
  fold(mutableListOf()) { months: MutableList<DataLogMonth>, row ->
    val month = LocalDate(row.startLocal.year, row.startLocal.month, 1)
    val last = months.lastOrNull()
    if (last != null && last.month == month) {
      months[months.lastIndex] = last.copy(rows = last.rows + row)
    } else {
      months += DataLogMonth(month, listOf(row))
    }
    months
  }
