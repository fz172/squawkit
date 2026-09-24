package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.datetime.toDayOfMonth
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.list.TimelineRow
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_ground_run
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_recorded_as
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_series_count

/**
 * One entry of the archive's timeline (PRD R34), under its month header: how long it ran, where
 * from, and whether it left the ground; then when, and how much it recorded. No gutter — a start
 * time is not what anyone scans this list for. A tail mismatch gets a line saying what the file
 * was recorded as; no badge, because the line already says something is wrong and what.
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
    add(row.startedText())
    add(stringResource(Res.string.data_log_series_count, row.seriesCount))
    if (showDetails && row.product.isNotBlank()) add(row.product)
  }

  TimelineRow(
    gutter = null,
    modifier = modifier,
    connectsUp = connectsUp,
    connectsDown = connectsDown,
    lit = isLatest,
    onClick = onClick,
  ) {
    Text(
      text = row.headline(stringResource(Res.string.data_log_ground_run)),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = details.joinToString(" · "),
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    // A data-integrity warning: the file says it belongs to another thing. The line names what it
    // was recorded as, which a badge could not.
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

/** "Sep 02, 2026 · KPAO", or the date alone for an airborne log with no ident; ground runs say so. */
fun DataLogRow.titleText(groundRun: String): String {
  val date = startLocal.date.toDisplayFormat(numberOnly = false)
  val route =
    if (airborne && startLocationIdent.isNotBlank()) startLocationIdent
    else if (airborne) "" else groundRun
  return if (route.isEmpty()) date else "$date · $route"
}

/** "17m 13s · E16 · Ground run" — duration, the starting location when known, and a ground run says so. */
fun DataLogRow.headline(groundRun: String): String = listOfNotNull(
  formatDuration(durationSeconds),
  startLocationIdent.takeIf { it.isNotBlank() },
  groundRun.takeIf { !airborne },
).joinToString(" · ")

/** "Sep 3, 10:06" — under a month header, which carries the year. */
fun DataLogRow.startedText(): String =
  "${startLocal.date.toDayOfMonth()}, ${startLocal.time.toClockText()}"

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
