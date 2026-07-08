package dev.fanfly.wingslog.feature.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.analytics.trackScreenViews
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.core.ui.adaptive.AdaptiveAppShell
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.feature.aircraft.dashboard.ShellSectionBody
import dev.fanfly.wingslog.feature.aircraft.dashboard.ShellSectionFab
import dev.fanfly.wingslog.feature.fleet.viewing.FleetEmptyState
import dev.fanfly.wingslog.feature.settings.SettingsContent
import dev.fanfly.wingslog.feature.shell.viewmodel.AdaptiveShellViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The adaptive-shell destination body shared by every host: wires [AdaptiveShellViewModel]
 * into [AdaptiveAppShell] and logs section changes as page views. Hosts register it on
 * [Screen.AdaptiveShell]'s route.
 */
@Composable
fun AdaptiveShellRoute(
  navController: NavController,
  isStressTestSupported: Boolean,
) {
  val viewModel = koinViewModel<AdaptiveShellViewModel>()
  val state by viewModel.uiState.collectAsState()
  // Page-view feeder 2: the shell's sections (Dashboard/Tasks/Squawks/Logs/Settings) are
  // ViewModel state under one route, so the root observer can't see them — log on change here.
  val analytics = LocalAnalytics.current
  LaunchedEffect(state.section, state.selectedAircraftId) {
    analytics.logScreenView("shell/${state.section.name.lowercase()}")
  }

  // The shell's own back-stack entry: dialog destinations (add/edit squawk, log, etc.) pushed on
  // top of it write a pending success message here (via previousBackStackEntry) before popping, so
  // this is where cross-screen snackbars land once the dialog closes.
  val shellEntry = remember(navController) {
    navController.getBackStackEntry(Screen.AdaptiveShell.route)
  }
  val successMessage by shellEntry.savedStateHandle
    .getStateFlow<String?>(CROSS_SCREEN_SUCCESS_MESSAGE, null)
    .collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(successMessage) {
    val message = successMessage ?: return@LaunchedEffect
    shellEntry.savedStateHandle[CROSS_SCREEN_SUCCESS_MESSAGE] = null
    snackbarHostState.showSnackbar(message = message)
  }

  AdaptiveAppShell(
    state = state,
    snackbarHostState = snackbarHostState,
    onSelectSection = viewModel::selectSection,
    onSelectAircraft = viewModel::selectAircraft,
    onOpenSettings = viewModel::openSettings,
    onAddAircraft = { navController.navigate(Screen.AddAircraft.route) },
    sectionContent = { section, aircraftId ->
      if (section == ShellSection.SETTINGS) {
        SettingsSection(
          rootNavController = navController,
          isStressTestSupported = isStressTestSupported
        )
      } else {
        ShellSectionBody(
          section = section,
          aircraftId = aircraftId,
          navController = navController,
          onNavigateToSection = viewModel::selectSection,
        )
      }
    },
    emptyFleetContent = {
      FleetEmptyState(
        onAddAircraft = { navController.navigate(Screen.AddAircraft.route) },
      )
    },
    sectionFab = { section, aircraftId ->
      ShellSectionFab(
        section = section,
        aircraftId = aircraftId,
        navController = navController,
      )
    },
  )
}

/** Nested route for the Settings list itself, hosted inside the content pane in sidebar mode. */
private const val SETTINGS_ROOT_ROUTE = "settings_root"

/**
 * The Settings section body. In sidebar mode it hosts a nested NavHost so the list and its detail
 * pages render in the content pane (the sidebar stays put); on compact tiers it renders the list
 * directly and detail pages open as full-screen routes off [rootNavController] (via
 * [settingsDetailRoutes] on the root graph).
 */
@Composable
private fun SettingsSection(
  rootNavController: NavController,
  isStressTestSupported: Boolean,
) {
  if (LocalLayoutTier.current.hasFullSidebar) {
    val settingsNav: NavHostController = rememberNavController()
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
        )
      }
      settingsDetailRoutes(settingsNav, isStressTestSupported)
    }
  } else {
    SettingsContent(navController = rootNavController)
  }
}
