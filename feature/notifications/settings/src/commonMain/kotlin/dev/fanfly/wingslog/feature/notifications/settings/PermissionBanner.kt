package dev.fanfly.wingslog.feature.notifications.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.settings.generated.resources.Res
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_body
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_open_settings
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_unsupported_body
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_unsupported_title

/**
 * §9.3's state table, collapsed to the two rows that actually render here — [PermissionState.GRANTED]
 * and [PermissionState.UNDETERMINED] show nothing. Neutral only: this is a convenience on top of a
 * logbook that works without it, never an error (no red, no destructive iconography).
 */
@Composable
internal fun PermissionBanner(
  state: NotificationSettingsUiState,
  onOpenSystemSettings: () -> Unit,
) {
  val colors = MaterialTheme.statusColors.neutral
  val (title, body) = when (state.permission) {
    PermissionState.DENIED ->
      stringResource(Res.string.notification_settings_banner_denied_title) to
        stringResource(Res.string.notification_settings_banner_denied_body)

    PermissionState.UNSUPPORTED ->
      stringResource(Res.string.notification_settings_banner_unsupported_title) to
        stringResource(Res.string.notification_settings_banner_unsupported_body)

    PermissionState.GRANTED, PermissionState.UNDETERMINED -> return
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(colors.container)
      .padding(Spacing.large),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = colors.onContainer
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = colors.onContainer
    )
    // Nothing to open on web (PermissionState.UNSUPPORTED) or wherever the platform exposes no
    // deep link to its own settings page — the button would be a dead end either way.
    if (state.permission == PermissionState.DENIED && state.canOpenSystemSettings) {
      TextButton(onClick = onOpenSystemSettings) {
        Text(
          text = stringResource(Res.string.notification_settings_banner_denied_open_settings),
          color = colors.accent,
        )
      }
    }
  }
}
