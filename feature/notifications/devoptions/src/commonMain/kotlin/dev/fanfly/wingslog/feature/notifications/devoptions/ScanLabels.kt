package dev.fanfly.wingslog.feature.notifications.devoptions

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.notifications.engine.ScanResult
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.devoptions.generated.resources.Res
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_result_completed
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_result_debounced
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_result_disabled
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_result_no_permission
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_result_no_user
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_scan_result_prefs_unresolved
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_denied
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_granted
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_undetermined
import wingslog.feature.notifications.devoptions.generated.resources.notifications_devoptions_state_unsupported

/** The scan result and permission state as words, for the diagnostics rows below. */
@Composable
internal fun ScanResult.toLabel(): String = when (this) {
  ScanResult.NoUser -> stringResource(Res.string.notifications_devoptions_scan_result_no_user)
  ScanResult.Debounced -> stringResource(Res.string.notifications_devoptions_scan_result_debounced)
  ScanResult.PrefsUnresolved -> stringResource(Res.string.notifications_devoptions_scan_result_prefs_unresolved)
  ScanResult.Disabled -> stringResource(Res.string.notifications_devoptions_scan_result_disabled)
  ScanResult.NoPermission -> stringResource(Res.string.notifications_devoptions_scan_result_no_permission)
  is ScanResult.Completed -> stringResource(
    Res.string.notifications_devoptions_scan_result_completed,
    notificationsPosted
  )
}

internal fun PermissionState.toLabelRes() = when (this) {
  PermissionState.UNDETERMINED -> Res.string.notifications_devoptions_state_undetermined
  PermissionState.GRANTED -> Res.string.notifications_devoptions_state_granted
  PermissionState.DENIED -> Res.string.notifications_devoptions_state_denied
  PermissionState.UNSUPPORTED -> Res.string.notifications_devoptions_state_unsupported
}
