package dev.fanfly.wingslog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.fanfly.wingslog.feature.login.EmailLinkDeepLinks

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Deliver a launch-time email sign-in link (App Links). AuthFlow ignores non-sign-in URLs.
    handleDeepLink(intent)
    setContent {
      AppEntry()
    }
  }

  // singleTask (see manifest) routes a tapped sign-in link to the running instance here.
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLink(intent)
  }

  private fun handleDeepLink(intent: Intent?) {
    val data = intent?.data?.toString() ?: return
    EmailLinkDeepLinks.deliver(data)
  }
}
