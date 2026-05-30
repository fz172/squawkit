package dev.fanfly.wingslog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseHealth
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.core.ui.shell.AdaptiveAppShell
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.fleet.viewing.viewmodel.AdaptiveShellViewModel
import dev.fanfly.wingslog.feature.aircraft.dashboard.AircraftOverviewScreen
import dev.fanfly.wingslog.feature.export.update.ExportHistoryRoute
import dev.fanfly.wingslog.feature.export.update.ExportSelectionRoute
import dev.fanfly.wingslog.feature.fleet.viewing.DashboardScreen
import dev.fanfly.wingslog.feature.login.AuthFlow
import dev.fanfly.wingslog.feature.logs.update.aircraft.EditAircraftScreen
import dev.fanfly.wingslog.feature.logs.update.logs.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.settings.SettingsScreen
import dev.fanfly.wingslog.feature.settings.featurelab.FeatureLabScreen
import dev.fanfly.wingslog.feature.squawk.update.ui.AddSquawkRoute
import dev.fanfly.wingslog.feature.squawk.update.ui.EditSquawkRoute
import dev.fanfly.wingslog.feature.sync.settings.SyncSettingsScreen
import dev.fanfly.wingslog.feature.tasks.update.ui.AddTaskRoute
import dev.fanfly.wingslog.feature.tasks.update.ui.EditTaskRoute
import dev.fanfly.wingslog.feature.technician.manage.compose.EditTechnicianScreen
import dev.fanfly.wingslog.feature.technician.manage.compose.TechnicianListScreen
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val GRAPH_AUTH = "graph_auth"
private const val GRAPH_FLEET = "graph_fleet"
private const val GRAPH_AIRCRAFT = "graph_aircraft"
private const val GRAPH_SETTINGS = "graph_settings"
private const val GRAPH_SHELL = "graph_shell"

@Composable
fun AppEntry() {
  val health: DatabaseHealth = koinInject()
  val checker: DatabaseIntegrityChecker = koinInject()
  val authManager: AuthManager = koinInject()
  val firebaseAuth: FirebaseAuth = koinInject()
  val dogfoodExts: DogfoodFeatureExtensions = koinInject()
  val featureLabManager: FeatureLabManager = koinInject()
  val flags by featureLabManager.observe().collectAsState(FeatureFlags())
  val scope = rememberCoroutineScope()

  if (health.isCorrupted) {
    WingslogTheme {
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

  WingslogTheme {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      val navController = rememberNavController()

      LaunchedEffect(Unit) {
        firebaseAuth.authStateChanged.collect { user ->
          if (user == null) {
            navController.navigate(Screen.Login.route) {
              popUpTo(0) { inclusive = true }
            }
          }
        }
      }

      NavHost(
        navController,
        startDestination = GRAPH_AUTH
      ) {
        authGraph(navController, adaptiveShellEnabled = { flags.adaptiveShellEnabled })
        fleetGraph(navController)
        aircraftGraph(navController)
        settingsGraph(navController, dogfoodExts)
        shellGraph(navController)
      }
    }
  }
}

private fun NavGraphBuilder.authGraph(
  navController: NavController,
  adaptiveShellEnabled: () -> Boolean,
) {
  navigation(
    startDestination = Screen.Login.route,
    route = GRAPH_AUTH
  ) {
    composable(Screen.Login.route) {
      // The whole sign-in + onboarding flow now lives in feature/login as AuthFlow.
      AuthFlow(
        onComplete = {
          // Behind the adaptiveShellEnabled flag, land authenticated users in the new adaptive
          // shell instead of the legacy fleet stack. Default path is unchanged.
          val destination = if (adaptiveShellEnabled()) GRAPH_SHELL else GRAPH_FLEET
          navController.navigate(destination) {
            popUpTo(GRAPH_AUTH) { inclusive = true }
          }
        },
      )
    }
  }
}

private fun NavGraphBuilder.shellGraph(navController: NavController) {
  navigation(
    startDestination = Screen.AdaptiveShell.route,
    route = GRAPH_SHELL
  ) {
    composable(Screen.AdaptiveShell.route) {
      val viewModel = koinViewModel<AdaptiveShellViewModel>()
      val state by viewModel.uiState.collectAsState()
      AdaptiveAppShell(
        state = state,
        onSelectAircraft = viewModel::selectAircraft,
        onOpenSettings = { navController.navigate(GRAPH_SETTINGS) },
      )
    }
  }
}

private fun NavGraphBuilder.fleetGraph(navController: NavController) {
  navigation(
    startDestination = Screen.Dashboard.route,
    route = GRAPH_FLEET
  ) {
    composable(Screen.Dashboard.route) {
      DashboardScreen(
        onOpenSettings = { navController.navigate(GRAPH_SETTINGS) },
        onAddAircraft = { navController.navigate(Screen.AddAircraft.route) },
        onAircraftClick = { aircraftId ->
          navController.navigate(
            Screen.MaintenanceOverview.createRoute(
              aircraftId
            )
          )
        },
      )
    }
    composable(route = Screen.AddAircraft.route) {
      EditAircraftScreen(navController = navController)
    }
    composable(
      route = Screen.EditAircraft.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
        nullable = true
      }),
    ) {
      EditAircraftScreen(navController = navController)
    }
  }
}

private fun NavGraphBuilder.aircraftGraph(navController: NavController) {
  navigation(
    startDestination = Screen.MaintenanceOverview.route,
    route = GRAPH_AIRCRAFT
  ) {
    composable(
      route = Screen.MaintenanceOverview.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
      }),
    ) {
      AircraftOverviewScreen(navController = navController)
    }
    composable(
      route = Screen.AddMaintenanceTask.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
      }),
    ) {
      AddTaskRoute(navController = navController)
    }
    composable(
      route = Screen.EditMaintenanceTask.route,
      arguments = listOf(
        navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
        navArgument(Screen.CARD_ID) { type = NavType.StringType },
      ),
    ) {
      EditTaskRoute(navController = navController)
    }
    composable(
      route = Screen.AddMaintenanceLog.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
      }),
    ) {
      MaintenanceLogFormScreen(navController = navController)
    }
    composable(
      route = Screen.EditMaintenanceLog.route,
      arguments = listOf(
        navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
        navArgument(Screen.LOG_ID) { type = NavType.StringType },
      ),
    ) {
      MaintenanceLogFormScreen(navController = navController)
    }
    composable(
      route = Screen.AddSquawk.route,
      arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
        type = NavType.StringType
      }),
    ) {
      AddSquawkRoute(navController = navController)
    }
    composable(
      route = Screen.EditSquawk.route,
      arguments = listOf(
        navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
        navArgument(Screen.SQUAWK_ID) { type = NavType.StringType },
      ),
    ) {
      EditSquawkRoute(navController = navController)
    }
  }
}

private fun NavGraphBuilder.settingsGraph(
  navController: NavController,
  dogfoodExts: DogfoodFeatureExtensions
) {
  navigation(
    startDestination = Screen.Settings.route,
    route = GRAPH_SETTINGS
  ) {
    composable(Screen.Settings.route) {
      SettingsScreen(navController = navController)
    }
    composable(Screen.SyncSettings.route) {
      SyncSettingsScreen(navController = navController)
    }
    composable(Screen.ExportLogs.route) {
      ExportSelectionRoute(
        navController = navController,
        onNavigateToHistory = { navController.navigate(Screen.ExportHistory.route) },
      )
    }
    composable(Screen.ExportHistory.route) {
      ExportHistoryRoute(navController = navController)
    }
    composable(Screen.FeatureLab.route) {
      FeatureLabScreen(
        navController = navController,
        dogfoodContent = { dogfoodExts.FeatureLabExtra(navController) },
      )
    }
    dogfoodExts.registerRoutes(this, navController)
    composable(Screen.ManageTechnicians.route) {
      val viewModel = koinViewModel<TechnicianListViewModel>()
      TechnicianListScreen(
        viewModel = viewModel,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToEdit = { id ->
          navController.navigate(
            Screen.EditTechnician.createRoute(
              id
            )
          )
        },
      )
    }
    composable(
      route = Screen.EditTechnician.route,
      arguments = listOf(navArgument(Screen.TECHNICIAN_ID) {
        type = NavType.StringType
        nullable = true
      }),
    ) {
      EditTechnicianScreen(
        viewModel = koinViewModel(),
        onNavigateBack = { navController.popBackStack() },
      )
    }
  }
}
