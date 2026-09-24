package dev.fanfly.wingslog.feature.notifications.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.settings.generated.resources.Res
import wingslog.feature.notifications.settings.generated.resources.notification_settings_signin_cta
import wingslog.feature.notifications.settings.generated.resources.notification_settings_signin_footer
import wingslog.feature.notifications.settings.generated.resources.notification_settings_sync_off_cta
import wingslog.feature.notifications.settings.generated.resources.notification_settings_sync_off_footer

/**
 * §9.3: signed-out and sync-off each get their own footer under collaboration only — urgency is
 * untouched either way. Signed-out takes priority when both are true; a guest turning on sync
 * before signing in still has no account for the server to attribute collaboration events to.
 */
@Composable
internal fun CollaborationFooter(
  state: NotificationSettingsUiState,
  navController: NavController,
) {
  when {
    !state.isSignedIn -> FooterRow(
      body = stringResource(Res.string.notification_settings_signin_footer),
      cta = stringResource(Res.string.notification_settings_signin_cta),
      // Embedding the guest-upgrade flow here would pull feature/notifications/settings into a
      // cross-feature dependency on feature/login/upgrade for one button; the account-upgrade CTA
      // is already one tap away on the Settings row this screen was opened from.
      onClick = { navController.popBackStack() },
    )

    !state.isCloudSyncEnabled -> FooterRow(
      body = stringResource(Res.string.notification_settings_sync_off_footer),
      cta = stringResource(Res.string.notification_settings_sync_off_cta),
      onClick = { navController.navigate(Screen.SyncSettings.route) },
    )
  }
}

@Composable
private fun FooterRow(
  body: String,
  cta: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.small, vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onClick) { Text(cta) }
  }
}
