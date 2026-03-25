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
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.aircraft.edit.EditAircraftConstants.ARGUMENT_AIRCRAFT_ID
import dev.fanfly.wingslog.feature.aircraft.edit.EditAircraftScreen
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.MaintenanceLogFormScreen
import dev.fanfly.wingslog.feature.aircraft.maintenance.log.MaintenanceLogListScreen
import dev.fanfly.wingslog.feature.aircraft.overview.AircraftOverviewScreen
import dev.fanfly.wingslog.feature.fleet.dashboard.DashboardScreen
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

      NavHost(navController, startDestination = "login") {
        composable("login") {
          LoginScreen(
            onLoginSuccess = {
              navController.navigate("main") {
                popUpTo("login") { inclusive = true }
              }
            })
        }
        composable("main") {
          DashboardScreen(
            onOpenSettings = { navController.navigate("settings") },
            onClickFab = { navController.navigate("add_aircraft") },
            onAircraftClick = { aircraftId -> navController.navigate("aircraft_overview/$aircraftId") })
        }
        composable("settings") {
          SettingsScreen(
            navController = navController,
            onAddAircraft = { navController.navigate("add_aircraft") })
        }

        composable("edit_profile") {
          EditProfileScreen(navController = navController)
        }

        composable(
          route = "add_aircraft",
        ) {
          EditAircraftScreen(navController = navController)
        }

        composable(
          route = "edit_aircraft/{${ARGUMENT_AIRCRAFT_ID}}",
          arguments = listOf(navArgument(ARGUMENT_AIRCRAFT_ID) {
            type = NavType.StringType
            nullable = true
          })
        ) {
          EditAircraftScreen(navController = navController)
        }

        composable(
          route = "aircraft_overview/{aircraftId}", arguments = listOf(navArgument("aircraftId") {
            type = NavType.StringType
          })
        ) {
          AircraftOverviewScreen(navController = navController)
        }

        // Maintenance Log routes
        composable(
          route = "maintenance_logs/{aircraftId}",
          arguments = listOf(navArgument("aircraftId") { type = NavType.StringType })
        ) {
          MaintenanceLogListScreen(navController = navController)
        }

        composable(
          route = "maintenance_log_create/{aircraftId}",
          arguments = listOf(navArgument("aircraftId") { type = NavType.StringType })
        ) {
          MaintenanceLogFormScreen(navController = navController)
        }

        composable(
          route = "maintenance_log_edit/{aircraftId}/{logId}",
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
