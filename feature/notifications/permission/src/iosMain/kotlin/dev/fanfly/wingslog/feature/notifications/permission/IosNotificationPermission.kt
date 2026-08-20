package dev.fanfly.wingslog.feature.notifications.permission

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS: `UNUserNotificationCenter`, called directly — this is a standard system framework, not one
 * that needs a Swift-side bridge the way Firebase App Check does (`IosAppCheckBridge`), so
 * Kotlin/Native's own Darwin interop is enough.
 *
 * `Provisional` and `Ephemeral` authorization both map to [PermissionState.GRANTED]: both still
 * deliver notifications (silently, for provisional; for the lifetime of an App Clip, for ephemeral)
 * — `DENIED` would be wrong for either. iOS never reports [PermissionState.UNSUPPORTED]; the
 * permission surface always exists.
 */
class IosNotificationPermission : NotificationPermission {

  private val center = UNUserNotificationCenter.currentNotificationCenter()
  private val _state = MutableStateFlow(PermissionState.UNDETERMINED)
  override fun observe(): StateFlow<PermissionState> = _state.asStateFlow()

  override suspend fun refresh() {
    _state.value = currentState()
  }

  override suspend fun request(): PermissionState {
    if (_state.value != PermissionState.UNDETERMINED) return _state.value
    val granted = suspendCancellableCoroutine { cont ->
      center.requestAuthorizationWithOptions(
        options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
      ) { didGrant, _ -> cont.resume(didGrant) }
    }
    val resolved = if (granted) PermissionState.GRANTED else currentState()
    _state.value = resolved
    return resolved
  }

  override val canOpenSystemSettings: Boolean = true

  override fun openSystemSettings() {
    val url = NSURL(string = UIApplicationOpenSettingsURLString)
    UIApplication.sharedApplication.openURL(url)
  }

  private suspend fun currentState(): PermissionState =
    suspendCancellableCoroutine { cont ->
      center.getNotificationSettingsWithCompletionHandler { settings ->
        val state = when (settings?.authorizationStatus) {
          UNAuthorizationStatusAuthorized,
          UNAuthorizationStatusProvisional,
          UNAuthorizationStatusEphemeral,
            -> PermissionState.GRANTED

          UNAuthorizationStatusDenied -> PermissionState.DENIED
          else -> PermissionState.UNDETERMINED
        }
        cont.resume(state)
      }
    }
}
