package dev.fanfly.wingslog.feature.notifications.devoptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.notifications.engine.ScanRecord
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.devoptions.generated.resources.Res
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_diagnostics_at
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_diagnostics_counts
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_diagnostics_never
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_diagnostics_title
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_diagnostics_trigger

/**
 * Design §11's diagnostics. Read from the persisted [ScanRecord] rather than from whatever this
 * process happens to have run, so a background scan that happened while the app was closed —
 * the case the §6.6 metric is about — is still visible here.
 */
@Composable
internal fun ScanDiagnosticsRow(lastScan: ScanRecord?) {
  Spacer(Modifier.height(Spacing.medium))
  Text(
    text = stringResource(Res.string.notifications_devoptions_diagnostics_title),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(bottom = Spacing.small),
  )
  if (lastScan == null) {
    Text(
      text = stringResource(Res.string.notifications_devoptions_diagnostics_never),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  Column {
    Text(
      text = stringResource(
        Res.string.notifications_devoptions_diagnostics_at,
        lastScan.at.toString(),
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = stringResource(
        Res.string.notifications_devoptions_diagnostics_trigger,
        lastScan.trigger.name,
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = stringResource(
        Res.string.notifications_devoptions_diagnostics_counts,
        lastScan.recordsExamined,
        lastScan.crossingsFound,
        lastScan.crossingsSuppressed,
        lastScan.notificationsPosted,
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
