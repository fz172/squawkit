package dev.fanfly.wingslog.feature.notifications.devoptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.developeroptions.plugin.DeveloperOptionsExtra
import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.devoptions.generated.resources.Res
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_channel_collaboration
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_channel_grounded
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_channel_urgency_update
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_header
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_open_settings_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_permission_title
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_request_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_send_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_denied
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_granted
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_undetermined
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_unsupported
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_test_body
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_test_sends_header

/**
 * Developer Options section for the notifications feature.
 *
 * **Partial delivery of design §11 / task P1.11.** The design also specifies a run-scan-now action, a
 * watermark reset, and scan diagnostics — those still can't exist, since they need `UrgencyScanner`
 * (P2) to call. What ships here is everything buildable without it: a manual trigger for
 * [NotificationPermission.request] (P1.4's first UI caller), and one test-send button per
 * [NotificationChannel], now that `LocalNotifier` (P1.5) exists to post through. The rest of §11 lands
 * incrementally as its prerequisites do; this class is where later work adds to it, not a stand-in
 * that gets replaced.
 */
class NotificationDeveloperOptionsExtra(
  private val permission: NotificationPermission,
  private val notifier: LocalNotifier,
) : DeveloperOptionsExtra {

  override val order: Int = 500

  @Composable
  override fun Content(onNavigate: (route: String) -> Unit) {
    val state by permission.observe()
      .collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Spacer(Modifier.height(Spacing.extraLarge))
    Text(
      text = stringResource(Res.string.notifications_devoptions_header),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(bottom = Spacing.small),
    )
    HorizontalDivider()

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = Spacing.medium),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = stringResource(Res.string.notifications_devoptions_permission_title),
          style = MaterialTheme.typography.bodyLarge,
        )
        Text(
          text = stringResource(state.toLabelRes()),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      when (state) {
        PermissionState.UNDETERMINED -> Button(onClick = { scope.launch { permission.request() } }) {
          Text(stringResource(Res.string.notifications_devoptions_request_action))
        }

        PermissionState.DENIED -> if (permission.canOpenSystemSettings) {
          OutlinedButton(onClick = { permission.openSystemSettings() }) {
            Text(stringResource(Res.string.notifications_devoptions_open_settings_action))
          }
        }

        PermissionState.GRANTED, PermissionState.UNSUPPORTED -> Unit
      }
    }

    Spacer(Modifier.height(Spacing.medium))
    Text(
      text = stringResource(Res.string.notifications_devoptions_test_sends_header),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(bottom = Spacing.small),
    )
    val testBody = stringResource(Res.string.notifications_devoptions_test_body)
    NotificationChannel.entries.forEach { channel ->
      val label = stringResource(channel.toLabelRes())
      TestSendRow(
        label = label,
        onSend = {
          scope.launch { notifier.post(channel.toTestNotification(title = label, body = testBody)) }
        },
      )
    }
    // No trailing divider — the host draws one after every extra.
  }

  @Composable
  private fun TestSendRow(label: String, onSend: () -> Unit) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = Spacing.small),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
      OutlinedButton(onClick = onSend) {
        Text(stringResource(Res.string.notifications_devoptions_send_action))
      }
    }
  }

  private fun PermissionState.toLabelRes() = when (this) {
    PermissionState.UNDETERMINED -> Res.string.notifications_devoptions_state_undetermined
    PermissionState.GRANTED -> Res.string.notifications_devoptions_state_granted
    PermissionState.DENIED -> Res.string.notifications_devoptions_state_denied
    PermissionState.UNSUPPORTED -> Res.string.notifications_devoptions_state_unsupported
  }

  private fun NotificationChannel.toLabelRes(): StringResource = when (this) {
    NotificationChannel.COLLABORATION -> Res.string.notifications_devoptions_channel_collaboration
    NotificationChannel.URGENCY_UPDATE -> Res.string.notifications_devoptions_channel_urgency_update
    NotificationChannel.GROUNDED -> Res.string.notifications_devoptions_channel_grounded
  }

  /**
   * `tapTarget` points at a nonexistent aircraft since `NotificationTapRouter` (P2.9) doesn't exist
   * yet to resolve it either way — a dev-only test send has nowhere real to land a tap.
   */
  private fun NotificationChannel.toTestNotification(title: String, body: String): PendingNotification =
    PendingNotification(
      id = "devoptions-test:$name",
      channel = this,
      title = title,
      body = body,
      highPriority = this == NotificationChannel.GROUNDED,
      tapTarget = NotificationTapTarget.Aircraft(aircraftId = "devoptions-test"),
    )
}
