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
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseHealth
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.common.compose.AdaptiveFormDialogFrame
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.shell.AdaptiveAppShell
import dev.fanfly.wingslog.core.ui.shell.ShellSection
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.aircraft.dashboard.ShellSectionBody
import dev.fanfly.wingslog.feature.export.update.ExportHistoryRoute
import dev.fanfly.wingslog.feature.export.update.ExportSelectionRoute
import dev.fanfly.wingslog.feature.fleet.viewing.DashboardScreen
import dev.fanfly.wingslog.feature.fleet.viewing.viewmodel.AdaptiveShellViewModel
import dev.fanfly.wingslog.feature.login.AuthFlow
import dev.fanfly.wingslog.feature.logs.update.aircraft.EditAircraftScreen
import dev.fanfly.wingslog.feature.logs.update.logs.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.settings.SettingsContent
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
private const val GRAPH_SHELL = "graph_shell"

@Composable
fun AppEntry() {
  val health: DatabaseHealth = koinInject()
  val checker: DatabaseIntegrityChecker = koinInject()
  val authManager: AuthManager = koinInject()
  val firebaseAuth: FirebaseAuth = koinInject()
  val dogfoodExts: DogfoodFeatureExtensions = koinInject()
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
        authGraph(navController)
        shellGraph(navController)
        formDialogs(navController)
        secondaryRoutes(navController, dogfoodExts)
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
        onSelectSection = viewModel::selectSection,
        onSelectAircraft = viewModel::selectAircraft,
        onEnterAircraft = viewModel::enterAircraft,
        onExitToFleet = viewModel::exitToFleet,
        // Settings is a native section in the shell now (M6) — no standalone-route hop.
        onOpenSettings = viewModel::openSettings,
        onAddAircraft = { navController.navigate(Screen.AddAircraft.route) },
        sectionContent = { section, aircraftId ->
          if (section == ShellSection.SETTINGS) {
            SettingsContent(navController = navController)
          } else {
            ShellSectionBody(
              section = section,
              aircraftId = aircraftId,
              navController = navController,
              onNavigateToSection = viewModel::selectSection,
            )
          }
        },
        fleetLanding = { onAircraftClick ->
          DashboardScreen(
            onOpenSettings = viewModel::openSettings,
            onAddAircraft = { navController.navigate(Screen.AddAircraft.route) },
            onAircraftClick = onAircraftClick,
          )
        },
        onEditAircraft = {
          state.selectedAircraftId?.let { navController.navigate(Screen.EditAircraft.createRoute(it)) }
        },
      )
    }
  }
}

private fun NavGraphBuilder.formDialogs(navController: NavController) {
  dialog(
    route = Screen.AddAircraft.route,
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      EditAircraftScreen(navController = navController)
    }
  }
  dialog(
    route = Screen.EditAircraft.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
      nullable = true
    }),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      EditAircraftScreen(navController = navController)
    }
  }
  dialog(
    route = Screen.AddMaintenanceTask.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
    }),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      AddTaskRoute(navController = navController)
    }
  }
  dialog(
    route = Screen.EditMaintenanceTask.route,
    arguments = listOf(
      navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
      navArgument(Screen.CARD_ID) { type = NavType.StringType },
    ),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      EditTaskRoute(navController = navController)
    }
  }
  dialog(
    route = Screen.AddMaintenanceLog.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
    }),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      MaintenanceLogFormScreen(navController = navController)
    }
  }
  dialog(
    route = Screen.EditMaintenanceLog.route,
    arguments = listOf(
      navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
      navArgument(Screen.LOG_ID) { type = NavType.StringType },
    ),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      MaintenanceLogFormScreen(navController = navController)
    }
  }
  dialog(
    route = Screen.AddSquawk.route,
    arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
      type = NavType.StringType
    }),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      AddSquawkRoute(navController = navController)
    }
  }
  dialog(
    route = Screen.EditSquawk.route,
    arguments = listOf(
      navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
      navArgument(Screen.SQUAWK_ID) { type = NavType.StringType },
    ),
    dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    AdaptiveFormDialogFrame {
      EditSquawkRoute(navController = navController)
    }
  }
}

private fun NavGraphBuilder.secondaryRoutes(
  navController: NavController,
  dogfoodExts: DogfoodFeatureExtensions
) {
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
