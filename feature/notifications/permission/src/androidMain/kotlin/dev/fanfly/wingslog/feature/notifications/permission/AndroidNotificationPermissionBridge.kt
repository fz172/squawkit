package dev.fanfly.wingslog.feature.notifications.permission

import androidx.activity.result.ActivityResultLauncher
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridges `POST_NOTIFICATIONS`'s runtime prompt into a suspend call.
 *
 * Unlike Google UMP's consent form (`AndroidAdConsentManager`, called directly against whichever
 * `Activity` `CurrentActivityProvider` reports), a runtime permission request goes through Android's
 * Activity Result API, and `registerForActivityResult` must be called before the owning
 * `ComponentActivity` reaches `STARTED` — a plain Koin-managed class can never do that itself. So the
 * host (`MainActivity`) registers a launcher once at `onCreate` and [attach]es it here; this object
 * is what `AndroidNotificationPermission.request()` actually calls through. Mirrors
 * `IosAppCheckBridge`'s shape for the same reason: the thing that can talk to the platform API lives
 * where the platform requires it, and everything else reaches it through a bridge.
 *
 * One request at a time by construction — `AndroidNotificationPermission.request()` is the only
 * caller, and its own contract (§5.1: no-ops unless `UNDETERMINED`) means a second concurrent call
 * from the same process would be a bug upstream of this class, not something to guard against here.
 */
object AndroidNotificationPermissionBridge {

  private var launcher: ActivityResultLauncher<String>? = null
  private var pending: CompletableDeferred<Boolean>? = null

  /** Called by `MainActivity.onCreate`, before the activity is started. */
  fun attach(launcher: ActivityResultLauncher<String>) {
    this.launcher = launcher
  }

  /** Called by `MainActivity.onDestroy`, so a rotated/recreated activity re-attaches its own launcher. */
  fun detach() {
    launcher = null
  }

  /** The Activity Result callback `MainActivity` forwards its launcher's result into. */
  fun onResult(granted: Boolean) {
    pending?.complete(granted)
    pending = null
  }

  /** Launches the OS prompt and suspends for the result. `false` immediately if no host has attached a launcher — a host bug, not a user decision, so it is logged. */
  suspend fun request(permission: String): Boolean {
    val activeLauncher = launcher
    if (activeLauncher == null) {
      log.w { "Notification permission requested but no launcher attached (MainActivity didn't wire it, or the activity is being recreated)" }
      return false
    }
    val deferred = CompletableDeferred<Boolean>()
    pending = deferred
    activeLauncher.launch(permission)
    return deferred.await()
  }

  private val log = Logger.withTag("NotificationPermissionBridge")
}
