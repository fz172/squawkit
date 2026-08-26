package dev.fanfly.wingslog.feature.notifications.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import dev.fanfly.wingslog.core.lifecycle.CurrentActivityProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android: `POST_NOTIFICATIONS` runtime permission (API 33+), requested via
 * [AndroidNotificationPermissionBridge] against whatever `MainActivity` has attached.
 *
 * `minSdk` is 33 across the tree, so every device this ships to has the runtime-permission surface;
 * there is no sub-33 branch to write. [NotificationManagerCompat.areNotificationsEnabled] is used
 * for the read rather than a raw `checkSelfPermission` because it also reflects the pre-33 app-level
 * "Notifications" system toggle, so the same read stays correct if `minSdk` ever drops.
 *
 * Distinguishing [PermissionState.UNDETERMINED] from [PermissionState.DENIED] needs state Android
 * does not expose directly — there is no "was this ever requested" query. [hasRequestedBefore]
 * tracks it locally: `false` and not granted is undetermined (an OS prompt will work); `true` and
 * not granted is denied (only system settings can fix it — a second prompt after a hard "don't ask
 * again" would silently no-op).
 */
class AndroidNotificationPermission(
  private val context: Context,
  private val activityProvider: CurrentActivityProvider,
) : NotificationPermission {

  private val prefs =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val _state = MutableStateFlow(currentState())
  override fun observe(): StateFlow<PermissionState> = _state.asStateFlow()

  override suspend fun refresh() {
    _state.value = currentState()
  }

  override suspend fun request(): PermissionState {
    if (_state.value != PermissionState.UNDETERMINED) return _state.value
    prefs.edit {
      putBoolean(KEY_HAS_REQUESTED, true)
    }
    val granted =
      AndroidNotificationPermissionBridge.request(Manifest.permission.POST_NOTIFICATIONS)
    val resolved = if (granted) PermissionState.GRANTED else currentState()
    _state.value = resolved
    return resolved
  }

  override val canOpenSystemSettings: Boolean = true

  override fun openSystemSettings() {
    context.startActivity(
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }

  private fun currentState(): PermissionState {
    if (NotificationManagerCompat.from(context)
        .areNotificationsEnabled()
    ) return PermissionState.GRANTED
    if (!prefs.getBoolean(
        KEY_HAS_REQUESTED,
        false
      )
    ) return PermissionState.UNDETERMINED

    // Requested before and still not granted. shouldShowRequestPermissionRationale distinguishes a
    // soft "not now" (still askable) from a hard "don't ask again" (system-settings-only) — but it
    // needs a foreground Activity, which may not exist here (called from a background scan, or
    // between activities during rotation). Default to DENIED when we cannot check: a second prompt
    // the OS would silently ignore is a worse failure than a settings banner shown one refresh early.
    val activity = activityProvider.current() ?: return PermissionState.DENIED
    return if (ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS
      )
    ) {
      PermissionState.UNDETERMINED
    } else {
      PermissionState.DENIED
    }
  }

  companion object {
    private const val PREFS_NAME = "notification_permission"
    private const val KEY_HAS_REQUESTED = "has_requested"
  }
}
