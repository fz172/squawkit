package dev.fanfly.wingslog.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavType
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.aircraft.dashboard.AircraftOverviewScreen
import dev.fanfly.wingslog.feature.fleet.viewing.DashboardScreen
import dev.fanfly.wingslog.feature.login.AuthFlow
import dev.fanfly.wingslog.feature.logs.update.aircraft.EditAircraftScreen
import dev.fanfly.wingslog.feature.logs.update.logs.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.settings.SettingsScreen
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
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalBrowserHistoryApi::class)
@Composable
fun WebApp() {
  WingslogTheme {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      val navController = rememberNavController()
      var browserNavigationBound by remember { mutableStateOf(false) }

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
              navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
              }
              browserNavigationBound = true
            },
          )
        }
        composable(Screen.Dashboard.route) {
          DashboardScreen(
            onOpenSettings = { navController.navigate(Screen.Settings.route) },
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
        composable(
          route = Screen.MaintenanceOverview.route,
          arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
            type = NavType.StringType
          }),
        ) {
          AircraftOverviewScreen(
            navController = navController,
          )
        }
        composable(
          route = Screen.AddMaintenanceTask.route,
          arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
            type = NavType.StringType
          }),
        ) {
          AddTaskRoute(
            navController = navController,
          )
        }
        composable(
          route = Screen.EditMaintenanceTask.route,
          arguments = listOf(
            navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
            navArgument(Screen.CARD_ID) { type = NavType.StringType },
          ),
        ) {
          EditTaskRoute(
            navController = navController,
          )
        }
        composable(
          route = Screen.AddMaintenanceLog.route,
          arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
            type = NavType.StringType
          }),
        ) {
          MaintenanceLogFormScreen(
            navController = navController,
          )
        }
        composable(
          route = Screen.EditMaintenanceLog.route,
          arguments = listOf(
            navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
            navArgument(Screen.LOG_ID) { type = NavType.StringType },
          ),
        ) {
          MaintenanceLogFormScreen(
            navController = navController,
          )
        }
        composable(
          route = Screen.AddSquawk.route,
          arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
            type = NavType.StringType
          }),
        ) {
          AddSquawkRoute(
            navController = navController,
          )
        }
        composable(
          route = Screen.EditSquawk.route,
          arguments = listOf(
            navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
            navArgument(Screen.SQUAWK_ID) { type = NavType.StringType },
          ),
        ) {
          EditSquawkRoute(
            navController = navController,
          )
        }
        composable(Screen.Settings.route) {
          SettingsScreen(navController = navController, onExportLogs = null)
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
