package dev.fanfly.wingslog.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.trackScreenViews
import dev.fanfly.wingslog.core.nav.Screen
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.compose.koinInject

/** Pops the whole back stack to the login route whenever Firebase reports a signed-out state. */
@Composable
fun NavigateToLoginOnSignOut(navController: NavController) {
  val firebaseAuth: FirebaseAuth = koinInject()
  LaunchedEffect(Unit) {
    firebaseAuth.authStateChanged.collect { user ->
      if (user == null) {
        navController.navigate(Screen.Login.route) {
          popUpTo(0) { inclusive = true }
        }
      }
    }
  }
}

/**
 * Page-view feeder 1: logs every route on [navController] except the shell container — its
 * in-shell sections are ViewModel state, not routes, so [AdaptiveShellRoute] logs them itself.
 * [analytics] is a parameter (not [dev.fanfly.wingslog.core.analytics.LocalAnalytics]) because
 * hosts may wrap the platform manager (e.g. web's browser-title wrapper) before providing it.
 */
@Composable
fun TrackRootScreenViews(
  navController: NavController,
  analytics: AnalyticsManager,
) {
  LaunchedEffect(navController) {
    navController.trackScreenViews(
      analytics,
      suppress = setOf(Screen.AdaptiveShell.route),
    )
  }
}

// A notification tap has no helper here on purpose: no variant navigates any more. Every one of them
// lands the pilot on a record inside a shell section, which is ViewModel state rather than a route,
// so AdaptiveShellRoute applies the whole thing (design §5.3). That also removes the need for the
// auth gate this used to carry — the shell composes only after the auth graph hands off, so a tap
// that cold-starts the app stays pending until there is somewhere to put it.
