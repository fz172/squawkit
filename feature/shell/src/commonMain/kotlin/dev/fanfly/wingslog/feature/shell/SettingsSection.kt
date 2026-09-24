package dev.fanfly.wingslog.feature.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.analytics.trackScreenViews
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.feature.login.upgrade.AccountUpgradeViewModel
import dev.fanfly.wingslog.feature.settings.SettingsContent

/** Nested route for the Settings list itself, hosted inside the content pane in sidebar mode. */
private const val SETTINGS_ROOT_ROUTE = "settings_root"

/**
 * The Settings section body. In sidebar mode it hosts a nested NavHost so the list and its detail
 * pages render in the content pane (the sidebar stays put); on compact tiers it renders the list
 * directly and detail pages open as full-screen routes off [rootNavController] (via
 * [settingsDetailRoutes] on the root graph).
 */
@Composable
internal fun SettingsSection(
  rootNavController: NavController,
  upgradeViewModel: AccountUpgradeViewModel,
  navigationMirror: ShellNavigationMirror?,
) {
  if (LocalLayoutTier.current.hasFullSidebar) {
    val settingsNav: NavHostController = rememberNavController()
    if (navigationMirror != null) {
      DisposableEffect(navigationMirror, settingsNav) {
        navigationMirror.attachSettingsNav(settingsNav)
        onDispose { navigationMirror.detachSettingsNav(settingsNav) }
      }
    }
    // Page-view feeder 3: sidebar-tier settings sub-pages run on this separate NavController,
    // which the root observer doesn't watch.
    val analytics = LocalAnalytics.current
    LaunchedEffect(settingsNav) {
      settingsNav.trackScreenViews(analytics)
    }
    NavHost(
      navController = settingsNav,
      startDestination = SETTINGS_ROOT_ROUTE,
      modifier = Modifier.fillMaxSize(),
    ) {
      composable(SETTINGS_ROOT_ROUTE) {
        SettingsContent(
          navController = rootNavController,
          sectionNavController = settingsNav,
          accountUpgradeViewModel = upgradeViewModel,
        )
      }
      settingsDetailRoutes(settingsNav)
    }
  } else {
    SettingsContent(
      navController = rootNavController,
      accountUpgradeViewModel = upgradeViewModel,
    )
  }
}
