package dev.fanfly.wingslog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.fleet.viewing.DashboardScreen
import dev.fanfly.wingslog.feature.tasks.update.ui.AddTaskRoute
import dev.fanfly.wingslog.feature.tasks.update.ui.EditTaskRoute
import dev.fanfly.wingslog.feature.logs.update.aircraft.EditAircraftScreen
import dev.fanfly.wingslog.feature.logs.update.logs.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.aircraft.dashboard.AircraftOverviewScreen
import dev.fanfly.wingslog.feature.settings.SettingsScreen
import dev.fanfly.wingslog.feature.settings.sync.SyncSettingsScreen
import dev.fanfly.wingslog.feature.technician.manage.compose.EditTechnicianScreen
import dev.fanfly.wingslog.feature.technician.manage.compose.TechnicianListScreen
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import dev.fanfly.wingslog.feature.userprofile.EditProfileScreen
import dev.fanfly.wingslog.login.LoginScreen
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppEntry() {
  WingslogTheme {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background
    ) {
      val navController = rememberNavController()

      NavHost(navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
          LoginScreen(
            onLoginSuccess = {
              navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
              }
            })
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
            })
        }
        composable(Screen.Settings.route) {
          SettingsScreen(
            navController = navController,
            onAddAircraft = { navController.navigate(Screen.AddAircraft.route) })
        }

        composable(Screen.EditProfile.route) {
          EditProfileScreen(navController = navController)
        }

        composable(Screen.SyncSettings.route) {
          SyncSettingsScreen(navController = navController)
        }

        composable(
          route = Screen.AddAircraft.route,
        ) {
          EditAircraftScreen(navController = navController)
        }

        composable(
          route = Screen.EditAircraft.route,
          arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
            type = NavType.StringType
            nullable = true
          })
        ) {
          EditAircraftScreen(navController = navController)
        }

        composable(
          route = Screen.MaintenanceOverview.route,
          arguments = listOf(navArgument(Screen.AIRCRAFT_ID) {
            type = NavType.StringType
          })
        ) {
          AircraftOverviewScreen(navController = navController)
        }

        composable(
          route = Screen.AddMaintenanceTask.route,
          arguments = listOf(navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType })
        ) {
          AddTaskRoute(navController = navController)
        }

        composable(
          route = Screen.EditMaintenanceTask.route,
          arguments = listOf(
            navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
            navArgument(Screen.CARD_ID) { type = NavType.StringType }
          )
        ) {
          EditTaskRoute(navController = navController)
        }

        composable(
          route = Screen.AddMaintenanceLog.route,
          arguments = listOf(navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType })
        ) {
          MaintenanceLogFormScreen(navController = navController)
        }

        composable(
          route = Screen.EditMaintenanceLog.route,
          arguments = listOf(
            navArgument(Screen.AIRCRAFT_ID) { type = NavType.StringType },
            navArgument(Screen.LOG_ID) { type = NavType.StringType }
          )
        ) {
          MaintenanceLogFormScreen(navController = navController)
        }

        composable(Screen.ManageTechnicians.route) {
          val viewModel = koinViewModel<TechnicianListViewModel>()
          TechnicianListScreen(
            viewModel = viewModel,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToEdit = { id -> navController.navigate(Screen.EditTechnician.createRoute(id)) }
          )
        }

        composable(
          route = Screen.EditTechnician.route,
          arguments = listOf(navArgument(Screen.TECHNICIAN_ID) {
            type = NavType.StringType
            nullable = true
          })
        ) {
          EditTechnicianScreen(
            viewModel = koinViewModel(),
            onNavigateBack = { navController.popBackStack() }
          )
        }
      }
    }
  }
}
