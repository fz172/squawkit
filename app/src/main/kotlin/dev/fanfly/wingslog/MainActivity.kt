package dev.fanfly.wingslog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.feature.notifications.permission.AndroidNotificationPermissionBridge
import dev.fanfly.wingslog.feature.notifications.viewing.NotificationTapRouter
import dev.fanfly.wingslog.feature.sharing.datamanager.AircraftShareDeepLinks

class MainActivity : ComponentActivity() {

  // Registered here, not inside AndroidNotificationPermission, because registerForActivityResult
  // must be called before this activity reaches STARTED — a plain Koin-managed class can never do
  // that itself. AndroidNotificationPermissionBridge is what the rest of the app actually calls.
  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted -> AndroidNotificationPermissionBridge.onResult(granted) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    AndroidNotificationPermissionBridge.attach(notificationPermissionLauncher)
    // Deliver a launch-time email sign-in link (App Links). AuthFlow ignores non-sign-in URLs.
    handleDeepLink(intent)
    setContent {
      AppEntry()
    }
  }

  override fun onDestroy() {
    // A rotated/recreated activity gets a fresh launcher; detach so a stale one is never called.
    AndroidNotificationPermissionBridge.detach()
    super.onDestroy()
  }

  // singleTask (see manifest) routes a tapped sign-in link to the running instance here.
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLink(intent)
  }

  private fun handleDeepLink(intent: Intent?) {
    val data = intent?.data?.toString() ?: return
    // A share invite is parked for the redeem flow; a tapped notification's target goes to
    // NotificationTapRouter (design §5.3); anything else (email sign-in) goes to AuthFlow.
    if (AircraftShareDeepLinks.deliver(data)) return
    if (NotificationTapRouter.deliver(data)) return
    EmailLinkDeepLinks.deliver(data)
  }
}
