package dev.fanfly.wingslog.feature.notifications.permission

import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.js.Promise

/**
 * Web: the `Notification` API, reached via `dynamic`/`js()` rather than `org.w3c.notifications`
 * externals — the same house pattern `AttachmentOpenerWeb` uses for DOM surfaces the bundled
 * externals model incompletely or not at all. This one specifically needs to ask "does this even
 * exist," which a typed external cannot represent.
 *
 * [PermissionState.UNSUPPORTED] (design §5.1, delta D15) is the reason this class exists at all:
 * an insecure origin, or a browser old enough not to implement the API, means `Notification` is
 * genuinely absent — not merely refused. `canOpenSystemSettings = false` unconditionally: no browser
 * API opens the browser's own site-settings UI.
 */
class WebNotificationPermission : NotificationPermission {

  private val _state = MutableStateFlow(currentState())
  override fun observe(): StateFlow<PermissionState> = _state.asStateFlow()

  override suspend fun refresh() {
    _state.value = currentState()
  }

  override suspend fun request(): PermissionState {
    if (_state.value != PermissionState.UNDETERMINED) return _state.value
    val result = requestPermissionPromise().await()
    val resolved = permissionStringToState(result)
    _state.value = resolved
    return resolved
  }

  override val canOpenSystemSettings: Boolean = false

  override fun openSystemSettings() = Unit

  private fun currentState(): PermissionState {
    if (!isSupported()) return PermissionState.UNSUPPORTED
    return permissionStringToState(currentPermissionString())
  }

  private fun permissionStringToState(value: String): PermissionState =
    when (value) {
      "granted" -> PermissionState.GRANTED
      "denied" -> PermissionState.DENIED
      else -> PermissionState.UNDETERMINED // "default"
    }

  private fun isSupported(): Boolean =
    js("(typeof Notification !== 'undefined')") as Boolean

  private fun currentPermissionString(): String =
    js("Notification.permission") as String

  private fun requestPermissionPromise(): Promise<String> =
    js("Notification.requestPermission()") as Promise<String>
}
