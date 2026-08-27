package dev.fanfly.wingslog.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.trackScreenViews
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.notifications.viewing.NotificationTapRouter
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

/**
 * Pops back to the shell destination whenever a notification tap is pending (#559). No variant of a
 * tap navigates — every one lands the pilot on a record inside a shell section, which is ViewModel
 * state rather than a route, and [AdaptiveShellRoute] applies the whole thing (design §5.3). But
 * applying it requires [AdaptiveShellRoute] to actually be composed and `STARTED`, and anything
 * pushed on top of the shell (Settings, Developer Options, an edit sheet) drops it below that
 * threshold — `collectAsStateWithLifecycle` there simply doesn't fire while covered, so the tap sat
 * un-consumed until the pilot backed out on their own and the effect fired late.
 *
 * This composable is mounted at the app root (same level as [NavigateToLoginOnSignOut]), not inside
 * a nav destination, so it stays subscribed regardless of what is on top and can clear the way. It
 * only pops; [AdaptiveShellRoute]'s own collector still does the actual consuming, so there is no
 * double-handling — popping just makes it `STARTED` again in time to see the still-pending value.
 *
 * A tap that cold-starts the app before the auth graph hands off has nothing to pop to yet
 * ([Screen.AdaptiveShell] isn't on the back stack) — `popBackStack` is a no-op then, same as it is
 * when the shell is already the top destination, and the tap simply stays pending until the shell
 * composes on its own, exactly as before.
 */
@Composable
fun PopToShellOnNotificationTap(navController: NavController) {
  val pending by NotificationTapRouter.pending.collectAsStateWithLifecycle()
  LaunchedEffect(pending) {
    if (pending == null) return@LaunchedEffect
    navController.popBackStack(Screen.AdaptiveShell.route, inclusive = false)
  }
}
