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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.developeroptions.plugin.DeveloperOptionsExtra
import dev.fanfly.wingslog.feature.notifications.engine.ScanRecord
import dev.fanfly.wingslog.feature.notifications.engine.ScanResult
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanDiagnostics
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanner
import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.devoptions.generated.resources.Res
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_header
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_open_settings_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_permission_title
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_request_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_never_run
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_now_action
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_now_title

/**
 * Developer Options section for the notifications feature.
 *
 * A manual trigger for [NotificationPermission.request] (P1.4), "scan now" via
 * [UrgencyScanner.scan] with [ScanTrigger.MANUAL], a watermark reset, and the scan diagnostics
 * design §11 wanted so §6.6's background-versus-foreground question is debuggable rather than
 * merely reportable.
 *
 * **The per-channel test sends are gone.** They predated the scanner and existed to prove channel
 * routing and the high-priority path without a second account and a real AOG squawk. "Scan now"
 * plus the watermark reset now produce real notifications through the real code path, which is
 * strictly better evidence than a synthetic post — and a synthetic post with a tap target pointing
 * at a nonexistent thing was actively misleading once [ScanTrigger] and tap routing existed.
 */
class NotificationDeveloperOptionsExtra(
  private val permission: NotificationPermission,
  private val scanner: UrgencyScanner,
  private val diagnostics: UrgencyScanDiagnostics,
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
    var lastResult by remember { mutableStateOf<ScanResult?>(null) }
    // An explicit counter, not `lastResult`, as the re-read key: a reset also clears the stored
    // record, and keying on the scan result would not re-fire when reset leaves it at null — which
    // left the row showing a scan that no longer existed.
    var diagnosticsRefresh by remember { mutableIntStateOf(0) }
    var lastScan by remember { mutableStateOf<ScanRecord?>(null) }
    LaunchedEffect(diagnosticsRefresh) { lastScan = diagnostics.lastScan() }
    var scanning by remember { mutableStateOf(false) }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = Spacing.small),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = stringResource(Res.string.notifications_devoptions_scan_now_title),
          style = MaterialTheme.typography.bodyLarge,
        )
        Text(
          text = lastResult?.toLabel()
            ?: stringResource(Res.string.notifications_devoptions_scan_never_run),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      OutlinedButton(
        enabled = !scanning,
        onClick = {
          scanning = true
          scope.launch {
            lastResult = scanner.scan(ScanTrigger.MANUAL)
            diagnosticsRefresh++
            scanning = false
          }
        },
      ) {
        Text(stringResource(Res.string.notifications_devoptions_scan_now_action))
      }
    }
    ResetWatermarksRow(
      scope = scope,
      onReset = {
        val cleared = diagnostics.resetWatermarks()
        lastResult = null
        diagnosticsRefresh++
        cleared
      },
    )

    ScanDiagnosticsRow(lastScan = lastScan)

    // No trailing divider — the host draws one after every extra.
  }

}
