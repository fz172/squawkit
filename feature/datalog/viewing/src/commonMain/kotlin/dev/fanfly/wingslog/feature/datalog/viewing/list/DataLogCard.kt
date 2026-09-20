package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.ListRow
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_ground_run
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_series_count
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_tail_mismatch

/** One row of the list (PRD R34, mocks 1a and 2a): date and route or ground run, then time · ident · duration · product. */
@Composable
fun DataLogCard(
  row: DataLogRow,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  /** Wide layouts show the source product and series count; phones keep the row to two lines. */
  showDetails: Boolean = true,
) {
  val title = row.titleText(stringResource(Res.string.data_log_ground_run))
  val details = buildList {
    add(row.startLocal.time.toClockText())
    if (showDetails && row.airborne && row.startLocationIdent.isNotBlank()) add(
      row.startLocationIdent
    )
    add(formatDuration(row.durationSeconds))
    if (showDetails && row.product.isNotBlank()) add(row.product)
  }

  ListRow(
    title = title,
    metadata = details.joinToString(" · "),
    metadataStyle = WingslogTypography.dataSmall,
    onClick = onClick,
    modifier = modifier,
    leading = {
      Icon(
        Icons.Filled.ShowChart,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(Spacing.extraLarge),
      )
    },
    trailing = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // A data-integrity warning, not decoration: the file says it belongs to another thing.
        if (row.identityMismatch) {
          StatusChip(
            label = stringResource(Res.string.data_log_tail_mismatch),
            tier = StatusTier.CAUTION,
          )
        }
        if (showDetails) {
          Text(
            text = stringResource(Res.string.data_log_series_count, row.seriesCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}

/** "Sep 02, 2026 · KPAO", or the date alone for an airborne log with no ident; ground runs say so. */
fun DataLogRow.titleText(groundRun: String): String {
  val date = startLocal.date.toDisplayFormat(numberOnly = false)
  val route =
    if (airborne && startLocationIdent.isNotBlank()) startLocationIdent
    else if (airborne) "" else groundRun
  return if (route.isEmpty()) date else "$date · $route"
}
