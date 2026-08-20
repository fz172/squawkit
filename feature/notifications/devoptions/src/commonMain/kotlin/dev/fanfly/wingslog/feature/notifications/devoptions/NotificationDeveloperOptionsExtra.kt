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
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.devoptions.generated.resources.Res
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_header
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_open_settings_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_permission_title
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_request_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_denied
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_granted
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_undetermined
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_unsupported

/**
 * Developer Options section for the notifications feature.
 *
 * **Partial delivery of design §11 / task P1.11.** The design specifies one test-send action per
 * channel, a run-scan-now action, a watermark reset, and scan diagnostics — none of which can exist
 * yet, since they all need `LocalNotifier` (P1.5) and `UrgencyScanner` (P2) to call. What ships here
 * is the one thing already buildable: a manual trigger for [NotificationPermission.request], the
 * first Android runtime-permission flow this app has ever shipped (P1.4) and — until this class
 * existed — one with no UI caller anywhere in the app. The rest of §11 lands incrementally as its
 * prerequisites do; this class is where later work adds to it, not a stand-in that gets replaced.
 */
class NotificationDeveloperOptionsExtra(
  private val permission: NotificationPermission,
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
    // No trailing divider — the host draws one after every extra.
  }

  private fun PermissionState.toLabelRes() = when (this) {
    PermissionState.UNDETERMINED -> Res.string.notifications_devoptions_state_undetermined
    PermissionState.GRANTED -> Res.string.notifications_devoptions_state_granted
    PermissionState.DENIED -> Res.string.notifications_devoptions_state_denied
    PermissionState.UNSUPPORTED -> Res.string.notifications_devoptions_state_unsupported
  }
}
