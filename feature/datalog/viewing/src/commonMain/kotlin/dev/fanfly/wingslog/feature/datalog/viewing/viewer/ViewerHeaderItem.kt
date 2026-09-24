package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.ui.badge.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogRow
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_tail_mismatch
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_utc_offset

/** What this log is: its identity, the tail-number warning, and the start · duration · product line. */
@Composable
internal fun ViewerHeaderItem(row: DataLogRow, record: DataLog) {
  Column {
    if (row.identity.isNotBlank()) Text(
      row.identity,
      style = WingslogTypography.dataMedium
    )
    if (row.identityMismatch) StatusChip(
      label = stringResource(Res.string.data_log_tail_mismatch),
      tier = StatusTier.CAUTION,
      modifier = Modifier.padding(top = Spacing.medium)
    )
    Text(
      text = listOf(
        stringResource(
          Res.string.data_log_viewer_utc_offset,
          row.startLocal.time.toClockText(),
          offsetText(record.utc_offset_minutes)
        ),
        formatDuration(row.durationSeconds),
        row.product,
      ).filter { it.isNotBlank() }
        .joinToString(" · "),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = Spacing.medium),
    )
  }
}
