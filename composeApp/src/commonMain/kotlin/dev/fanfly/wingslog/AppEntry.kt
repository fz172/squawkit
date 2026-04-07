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
import dev.fanfly.wingslog.feature.inspection.update.ui.AddInspectionRoute
import dev.fanfly.wingslog.feature.inspection.update.ui.EditInspectionRoute
import dev.fanfly.wingslog.feature.maintenance.update.edit.EditAircraftScreen
import dev.fanfly.wingslog.feature.maintenance.update.form.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.maintenance.viewing.log.MaintenanceLogListScreen
import dev.fanfly.wingslog.feature.maintenance.viewing.overview.AircraftOverviewScreen
import dev.fanfly.wingslog.feature.settings.SettingsScreen
import dev.fanfly.wingslog.feature.userprofile.EditProfileScreen
import dev.fanfly.wingslog.login.LoginScreen

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
            onAircraftClick = { aircraftId -> navController.navigate(Screen.MaintenanceOverview.createRoute(aircraftId)) })
        }
        composable(Screen.Settings.route) {
          SettingsScreen(
            navController = navController,
            onAddAircraft = { navController.navigate(Screen.AddAircraft.route) })
        }

        composable(Screen.EditProfile.route) {
          EditProfileScreen(navController = navController)
        }

        composable(
          route = Screen.AddAircraft.route,
        ) {
          EditAircraftScreen(navController = navController)
        }

        composable(
          route = Screen.EditAircraft.route,
          arguments = listOf(navArgument("aircraft_id") {
            type = NavType.StringType
            nullable = true
          })
        ) {
          EditAircraftScreen(navController = navController)
        }

        composable(
          route = Screen.MaintenanceOverview.route,
          arguments = listOf(navArgument("aircraftId") {
            type = NavType.StringType
          })
        ) {
          AircraftOverviewScreen(navController = navController)
        }

        composable(
          route = Screen.AddInspection.route,
          arguments = listOf(navArgument("aircraftId") { type = NavType.StringType })
        ) {
          AddInspectionRoute(navController = navController)
        }

        composable(
          route = Screen.EditInspection.route,
          arguments = listOf(
            navArgument("aircraftId") { type = NavType.StringType },
            navArgument("cardId") { type = NavType.StringType }
          )
        ) {
          EditInspectionRoute(navController = navController)
        }

        // Maintenance Log routes
        composable(
          route = Screen.MaintenanceLogs.route,
          arguments = listOf(navArgument("aircraftId") { type = NavType.StringType })
        ) {
          MaintenanceLogListScreen(navController = navController)
        }

        composable(
          route = Screen.AddMaintenanceLog.route,
          arguments = listOf(navArgument("aircraftId") { type = NavType.StringType })
        ) {
          MaintenanceLogFormScreen(navController = navController)
        }

        composable(
          route = Screen.EditMaintenanceLog.route,
          arguments = listOf(
            navArgument("aircraftId") { type = NavType.StringType },
            navArgument("logId") { type = NavType.StringType }
          )
        ) {
          MaintenanceLogFormScreen(navController = navController)
        }
      }
    }
  }
}
