package dev.fanfly.wingslog.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.trackScreenViews
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
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
 * Navigates to a tapped notification's target, for the three [NotificationTapTarget] variants that
 * have a real nav-graph destination already (`Screen.EditSquawk` and friends, registered by
 * `formDialogs`). [NotificationTapTarget.Aircraft] is deliberately not handled here —
 * `AdaptiveShellViewModel` collects the same [NotificationTapRouter.pending] itself, since aircraft
 * selection is app-level ViewModel state, not a navigation argument (design §5.3; see that
 * ViewModel's own doc comment).
 */
@Composable
fun HandleNotificationTaps(navController: NavController) {
  LaunchedEffect(Unit) {
    NotificationTapRouter.pending.collect { target ->
      val route = when (target) {
        is NotificationTapTarget.Squawk ->
          Screen.EditSquawk.createRoute(target.aircraftId, target.squawkId)
        is NotificationTapTarget.Task ->
          Screen.EditMaintenanceTask.createRoute(target.aircraftId, target.taskId)
        is NotificationTapTarget.Log ->
          Screen.EditMaintenanceLog.createRoute(target.aircraftId, target.logId)
        is NotificationTapTarget.Aircraft, null -> null
      }
      if (route != null) {
        navController.navigate(route)
        NotificationTapRouter.consume()
      }
    }
  }
}
