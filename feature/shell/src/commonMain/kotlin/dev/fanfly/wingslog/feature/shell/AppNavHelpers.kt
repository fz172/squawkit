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
/**
 * Routes whose screens log their own view, so feeder 1 must not log the raw route as well.
 *
 * **The failure this prevents**, caught in DebugView during #667: opening the squawk form produced
 * *two* `screen_view` events — `squawk_edit/{thingId}/{squawkId}` from the back-stack observer
 * and `squawk_form` from the screen itself. One screen open, counted twice, split across two names,
 * so neither series is the real number.
 *
 * The screen's own name wins because it carries detail the route cannot: the task and log forms
 * report the *tab* the user is on (`task_form/parts`), which is a different screen to a reader and
 * the same route to the navigator.
 *
 * Add a route here when its screen calls [AnalyticsManager.logScreenView] itself — and only then.
 * A route missing from this set is double-counted; a route wrongly in it is not counted at all.
 */
private val SELF_LOGGING_ROUTES = setOf(
  Screen.AddSquawk.route,
  Screen.EditSquawk.route,
  Screen.AddMaintenanceTask.route,
  Screen.EditMaintenanceTask.route,
  Screen.AddMaintenanceLog.route,
  Screen.EditMaintenanceLog.route,
)

@Composable
fun TrackRootScreenViews(
  navController: NavController,
  analytics: AnalyticsManager,
) {
  LaunchedEffect(navController) {
    navController.trackScreenViews(
      analytics,
      suppress = SELF_LOGGING_ROUTES + Screen.AdaptiveShell.route,
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
