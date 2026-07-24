package dev.fanfly.wingslog.feature.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.analytics.trackScreenViews
import dev.fanfly.wingslog.core.appinfo.AppCapability
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
import dev.fanfly.wingslog.feature.subscription.viewing.ProUpsellSheet
import dev.fanfly.wingslog.feature.subscription.viewing.UpsellTrigger
import dev.fanfly.wingslog.feature.sync.data.SyncNotice
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.dismiss
import wingslog.core.sharedassets.generated.resources.sync_changes_discarded
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The adaptive-shell destination body shared by every host: wires [AdaptiveShellViewModel]
 * into [AdaptiveAppShell] and logs section changes as page views. Hosts register it on
 * [Screen.AdaptiveShell]'s route.
 */
@Composable
fun AdaptiveShellRoute(
  navController: NavController,
  shellEntry: NavBackStackEntry,
  isStressTestSupported: Boolean,
) {
  val viewModel = koinViewModel<AdaptiveShellViewModel>()
  val state by viewModel.uiState.collectAsState()
  val atAircraftLimit by viewModel.atAircraftLimit.collectAsState()

  // At the owned-aircraft limit, the Add-aircraft entry surfaces the promo instead of navigating
  // (gate as promo, not a hidden action). Never reached from the empty-fleet state — 0 owned is
  // never at limit. Default-open while the subscription capability is off.
  var showAddAircraftUpsell by remember { mutableStateOf(false) }
  val onAddAircraft = {
    if (atAircraftLimit) {
      showAddAircraftUpsell = true
    } else {
      navController.navigate(Screen.AddAircraft.route)
    }
  }

  // Manual invite-code entry (#209). Offered only when aircraft sharing is built into this app —
  // otherwise the affordance is dropped, not shown-and-broken. Reused by both the switcher (populated
  // fleet) and the empty-fleet state (where a technician with no aircraft of their own lands).
  val appCapability = koinInject<AppCapability>()
  val onEnterInviteCode: (() -> Unit)? =
    if (appCapability.isAircraftSharingSupported) {
      { navController.navigate(Screen.EnterInviteCode.route) }
    } else {
      null
    }
  // Page-view feeder 2: the shell's sections (Dashboard/Tasks/Squawks/Logs/Settings) are
  // ViewModel state under one route, so the root observer can't see them — log on change here.
  val analytics = LocalAnalytics.current
  LaunchedEffect(state.section, state.selectedAircraftId) {
    analytics.logScreenView("shell/${state.section.name.lowercase()}")
  }

  // The shell's own back-stack entry: dialog destinations (add/edit squawk, log, etc.) pushed on
  // top of it write a pending success message here (via previousBackStackEntry) before popping, so
  // this is where cross-screen snackbars land once the dialog closes.
  //
  // [shellEntry] is the entry the NavHost is composing this destination for — passed in rather than
  // re-derived with navController.getBackStackEntry(route). That lookup THROWS when the route is
  // momentarily absent from the back stack, which the controller can report mid-pop; the shell then
  // recomposing during the pop that ends a share crashed the app. The passed entry is always the one
  // being composed, and it is the same object dialogs target via previousBackStackEntry.
  val successMessage by shellEntry.savedStateHandle
    .getStateFlow<String?>(CROSS_SCREEN_SUCCESS_MESSAGE, null)
    .collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(successMessage) {
    val message = successMessage ?: return@LaunchedEffect
    shellEntry.savedStateHandle[CROSS_SCREEN_SUCCESS_MESSAGE] = null
    snackbarHostState.showSnackbar(message = message)
  }

  // Work that was destroyed when a share ended (PRD D3 — the one data-loss window). Held open until
  // the user dismisses it: the purge typically runs while they are on another screen or the app is
  // backgrounded, and a message that times out unseen would leave them thinking the edit saved.
  val notice by viewModel.notice.collectAsState()
  val dismissLabel = stringResource(CoreRes.string.dismiss)
  val discardedMessage = stringResource(
    CoreRes.string.sync_changes_discarded,
    (notice as? SyncNotice.ChangesDiscarded)?.aircraftLabel.orEmpty(),
  )
  LaunchedEffect(notice) {
    val discarded =
      notice as? SyncNotice.ChangesDiscarded ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(
      message = discardedMessage,
      actionLabel = dismissLabel,
      withDismissAction = true,
      duration = SnackbarDuration.Indefinite,
    )
    viewModel.dismissNotice()
  }

  AdaptiveAppShell(
    state = state,
    snackbarHostState = snackbarHostState,
    onSelectSection = viewModel::selectSection,
    onSelectAircraft = viewModel::selectAircraft,
    onOpenSettings = viewModel::openSettings,
    onAddAircraft = onAddAircraft,
    onEnterInviteCode = onEnterInviteCode,
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
        onEnterInviteCode = onEnterInviteCode,
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

  if (showAddAircraftUpsell) {
    ProUpsellSheet(
      trigger = UpsellTrigger.ADD_AIRCRAFT,
      onSeePlans = {
        showAddAircraftUpsell = false
        navController.navigate(Screen.Subscription.route)
      },
      onDismiss = { showAddAircraftUpsell = false },
    )
  }
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
