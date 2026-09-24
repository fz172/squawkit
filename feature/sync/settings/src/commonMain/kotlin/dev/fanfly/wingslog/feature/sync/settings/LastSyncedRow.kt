package dev.fanfly.wingslog.feature.sync.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayTime
import dev.fanfly.wingslog.core.ui.grouped.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.grouped.GroupedRow
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sync.settings.generated.resources.Res
import wingslog.feature.sync.settings.generated.resources.sync_last_synced_pending
import wingslog.feature.sync.settings.generated.resources.sync_last_synced_title
import wingslog.feature.sync.settings.generated.resources.sync_last_synced_up_to_date
import wingslog.feature.sync.settings.generated.resources.sync_status_off_title
import wingslog.feature.sync.settings.generated.resources.sync_status_restoring
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_signin

private val StatusDotSize = 8.dp

/**
 * When the cloud and this device last agreed, with a dot that says how things stand now: green
 * while healthy, the critical tone under a failure. The subtitle names the state in words; the
 * dot is never the only signal.
 */
@Composable
internal fun LastSyncedRow(state: SyncSettingsUiState) {
  val colors = MaterialTheme.statusColors
  val hydration = state.hydration
  val (subtitle, dot) = when {
    state.failure != null -> state.failure.displayText() to colors.critical.accent
    !state.signedIn -> stringResource(Res.string.sync_subtitle_signin) to null
    !state.cloudSyncEnabled -> stringResource(Res.string.sync_status_off_title) to null
    hydration is HydrationState.InProgress ->
      stringResource(
        Res.string.sync_status_restoring,
        hydration.completed,
        hydration.total
      ) to
        colors.caution.accent

    state.lastSyncedAt == null -> stringResource(Res.string.sync_last_synced_pending) to null
    else -> stringResource(Res.string.sync_last_synced_up_to_date) to colors.positive.accent
  }
  val title = stringResource(Res.string.sync_last_synced_title)
  GroupedRow(
    title = title,
    subtitle = subtitle,
    leading = {
      GroupedLeadingIconChip(
        icon = Icons.Default.Schedule,
        contentDescription = title,
      )
    },
    trailing = if (state.lastSyncedAt == null) null else ({
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        if (dot != null) {
          Box(
            modifier = Modifier
              .size(StatusDotSize)
              .clip(CircleShape)
              .background(dot),
          )
        }
        Text(
          text = state.lastSyncedAt.toDisplayTime(),
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }),
  )
}
