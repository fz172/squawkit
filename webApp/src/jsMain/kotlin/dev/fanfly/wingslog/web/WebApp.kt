package dev.fanfly.wingslog.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavType
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.adaptive.AdaptiveAppShell
import dev.fanfly.wingslog.core.ui.adaptive.ShellSection
import dev.fanfly.wingslog.core.ui.adaptive.compose.AdaptiveFormDialogFrame
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.core.ui.theme.resolveDarkTheme
import dev.fanfly.wingslog.feature.aircraft.dashboard.ShellSectionBody
import dev.fanfly.wingslog.feature.fleet.viewing.FleetEmptyState
import dev.fanfly.wingslog.feature.fleet.viewing.viewmodel.AdaptiveShellViewModel
import dev.fanfly.wingslog.feature.login.AuthFlow
import dev.fanfly.wingslog.feature.logs.update.aircraft.EditAircraftScreen
import dev.fanfly.wingslog.feature.logs.update.logs.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.settings.SettingsContent
import dev.fanfly.wingslog.feature.settings.featurelab.FeatureLabScreen
import dev.fanfly.wingslog.feature.squawk.update.ui.AddSquawkRoute
import dev.fanfly.wingslog.feature.squawk.update.ui.EditSquawkRoute
import dev.fanfly.wingslog.feature.stresstest.config.StressTestFeatureLabExtra
import dev.fanfly.wingslog.feature.stresstest.config.registerStressTestRoutes
import dev.fanfly.wingslog.feature.sync.settings.SyncSettingsScreen
import dev.fanfly.wingslog.feature.tasks.update.ui.AddTaskRoute
import dev.fanfly.wingslog.feature.tasks.update.ui.EditTaskRoute
import dev.fanfly.wingslog.feature.technician.manage.compose.EditTechnicianScreen
import dev.fanfly.wingslog.feature.technician.manage.compose.TechnicianListScreen
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
fun WebApp() {
  val appearanceController: AppearanceController = koinInject()
  val appearanceMode by appearanceController.mode.collectAsState()
  WingslogTheme(darkTheme = appearanceMode.resolveDarkTheme()) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      val navController = rememberNavController()
      val firebaseAuth: FirebaseAuth = koinInject()
      var browserNavigationBound by remember { mutableStateOf(false) }

      LaunchedEffect(Unit) {
        firebaseAuth.authStateChanged.collect { user ->
          if (user == null) {
            navController.navigate(Screen.Login.route) {
              popUpTo(0) { inclusive = true }
            }
          }
        }
      }

      LaunchedEffect(browserNavigationBound) {
        if (browserNavigationBound) {
          navController.bindToBrowserNavigation()
        }
      }

      NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
      ) {
        composable(Screen.Login.route) {
          AuthFlow(
            onComplete = {
              navController.navigate(Screen.AdaptiveShell.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
              }
              browserNavigationBound = true
            },
          )
        }
        composable(Screen.AdaptiveShell.route) {
          val viewModel = koinViewModel<AdaptiveShellViewModel>()
          val state by viewModel.uiState.collectAsState()
          AdaptiveAppShell(
            state = state,
            onSelectSection = viewModel::selectSection,
            onSelectAircraft = viewModel::selectAircraft,
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
            emptyFleetContent = {
              FleetEmptyState(
                onAddAircraft = { navController.navigate(Screen.AddAircraft.route) },
              )
            },
          )
        }
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
            AddTaskRoute(
              navController = navController,
            )
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
            EditTaskRoute(
              navController = navController,
            )
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
            MaintenanceLogFormScreen(
              navController = navController,
            )
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
            MaintenanceLogFormScreen(
              navController = navController,
            )
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
            AddSquawkRoute(
              navController = navController,
            )
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
            EditSquawkRoute(
              navController = navController,
            )
          }
        }
        composable(Screen.SyncSettings.route) {
          SyncSettingsScreen(navController = navController)
        }
        composable(Screen.FeatureLab.route) {
          FeatureLabScreen(
            navController = navController,
            dogfoodContent = { StressTestFeatureLabExtra(navController) },
          )
        }
        registerStressTestRoutes(this, navController)
        composable(Screen.ManageTechnicians.route) {
          val viewModel = koinViewModel<TechnicianListViewModel>()
          TechnicianListScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToEdit = { id ->
              navController.navigate(Screen.EditTechnician.createRoute(id))
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
  }
}
