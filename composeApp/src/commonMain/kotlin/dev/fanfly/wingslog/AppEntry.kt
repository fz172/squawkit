package dev.fanfly.wingslog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.storage.DatabaseHealth
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.core.ui.theme.resolveDarkTheme
import dev.fanfly.wingslog.feature.login.AuthFlow
import dev.fanfly.wingslog.feature.shell.AdaptiveShellRoute
import dev.fanfly.wingslog.feature.shell.NavigateToLoginOnSignOut
import dev.fanfly.wingslog.feature.shell.TrackRootScreenViews
import dev.fanfly.wingslog.feature.shell.formDialogs
import dev.fanfly.wingslog.feature.shell.settingsDetailRoutes
import dev.fanfly.wingslog.feature.shell.sharingRoutes
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val GRAPH_AUTH = "graph_auth"
private const val GRAPH_SHELL = "graph_shell"

/**
 * Android/iOS host entry. The navigation graph itself (shell route, form dialogs, settings
 * detail pages) is shared with webApp via `feature:shell`; this host adds the DB-integrity
 * gate, the theme wrapper, and the auth graph.
 */
@Composable
fun AppEntry() {
  val health: DatabaseHealth = koinInject()
  val checker: DatabaseIntegrityChecker = koinInject()
  val authManager: AuthManager = koinInject()
  val appCapability: AppCapability = koinInject()
  val analytics: AnalyticsManager = koinInject()
  val appearanceController: AppearanceController = koinInject()
  val appearanceMode by appearanceController.mode.collectAsState()
  val darkTheme = appearanceMode.resolveDarkTheme()
  val scope = rememberCoroutineScope()

  if (health.isCorrupted) {
    WingslogTheme(darkTheme = darkTheme) {
      IntegrityRecoveryDialog(
        onWipe = {
          // wipeAllData() is now suspend (async-generated queries); log out only after it completes.
          scope.launch {
            checker.wipeAllData()
            authManager.logOut()
          }
        },
      )
    }
    return
  }

  WingslogTheme(darkTheme = darkTheme) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      val navController = rememberNavController()

      NavigateToLoginOnSignOut(navController)
      TrackRootScreenViews(navController, analytics)

      CompositionLocalProvider(LocalAnalytics provides analytics) {
        NavHost(
          navController,
          startDestination = GRAPH_AUTH
        ) {
          authGraph(navController)
          shellGraph(navController, appCapability.isStressTestSupported)
          formDialogs(navController)
          sharingRoutes(navController)
          // Compact tiers (no sidebar) open settings detail pages as full-screen routes; the sidebar
          // tier hosts its own nested copy of these inside the Settings section (see SettingsSection).
          settingsDetailRoutes(navController, appCapability.isStressTestSupported)
        }
      }
    }
  }
}

private fun NavGraphBuilder.authGraph(
  navController: NavController,
) {
  navigation(
    startDestination = Screen.Login.route,
    route = GRAPH_AUTH
  ) {
    composable(Screen.Login.route) {
      // The whole sign-in + onboarding flow now lives in feature/login as AuthFlow.
      AuthFlow(
        onComplete = {
          navController.navigate(GRAPH_SHELL) {
            popUpTo(GRAPH_AUTH) { inclusive = true }
          }
        },
      )
    }
  }
}

private fun NavGraphBuilder.shellGraph(
  navController: NavController,
  isStressTestSupported: Boolean,
) {
  navigation(
    startDestination = Screen.AdaptiveShell.route,
    route = GRAPH_SHELL
  ) {
    composable(Screen.AdaptiveShell.route) {
      AdaptiveShellRoute(
        navController = navController,
        isStressTestSupported = isStressTestSupported,
      )
    }
  }
}
