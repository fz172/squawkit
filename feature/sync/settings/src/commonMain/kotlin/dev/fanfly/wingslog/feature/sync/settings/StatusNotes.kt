package dev.fanfly.wingslog.feature.sync.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sync.settings.generated.resources.Res
import wingslog.feature.sync.settings.generated.resources.sync_status_error_title
import wingslog.feature.sync.settings.generated.resources.sync_status_off_body
import wingslog.feature.sync.settings.generated.resources.sync_status_off_title
import wingslog.feature.sync.settings.generated.resources.sync_status_restoring

/**
 * "Notes & warnings" (house settings-screen shape): only what the Status card cannot say in a
 * subtitle — the restore progress bar, the full explanation of what "sync is off" costs, and a
 * failure spelled out. Nothing renders when sync is caught up.
 */
@Composable
internal fun StatusNotes(state: SyncSettingsUiState) {
  val colors = MaterialTheme.statusColors
  when {
    state.failure != null -> NoteRow(
      icon = Icons.Default.Warning,
      title = stringResource(Res.string.sync_status_error_title),
      body = state.failure.displayText(),
      tint = colors.critical.accent,
      container = colors.critical.container,
    )

    state.signedIn && state.cloudSyncEnabled && state.hydration is HydrationState.InProgress -> {
      val h = state.hydration
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Spacing.cardCornerRadius))
          .background(MaterialTheme.colorScheme.primaryContainer)
          .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Text(
          text = stringResource(
            Res.string.sync_status_restoring,
            h.completed,
            h.total
          ),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        LinearProgressIndicator(
          progress = {
            (h.completed.toFloat() / h.total.toFloat()).coerceIn(
              0f,
              1f
            )
          },
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
        )
      }
    }

    state.signedIn && !state.cloudSyncEnabled -> NoteRow(
      icon = Icons.Default.CloudOff,
      title = stringResource(Res.string.sync_status_off_title),
      body = stringResource(Res.string.sync_status_off_body),
      tint = colors.neutral.accent,
      container = colors.neutral.container,
    )

    // Fully caught up, or a guest the hero already speaks to: no note.
    else -> Unit
  }
}
