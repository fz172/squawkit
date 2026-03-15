package dev.fanfly.wingslog

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.fanfly.wingslog.aircraft.edit.EditAircraftConstants.ARGUMENT_AIRCRAFT_ID
import dev.fanfly.wingslog.aircraft.edit.EditAircraftScreen
import dev.fanfly.wingslog.aircraft.maintenance.form.MaintenanceLogFormScreen
import dev.fanfly.wingslog.aircraft.maintenance.log.MaintenanceLogListScreen
import dev.fanfly.wingslog.aircraft.overview.AircraftOverviewScreen
import dev.fanfly.wingslog.fleet.dashboard.DashboardScreen
import dev.fanfly.wingslog.login.LoginScreen
import dev.fanfly.wingslog.settings.SettingsScreen
import dev.fanfly.wingslog.userprofile.EditProfileScreen

@Composable
fun AppEntry() {
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
        navController = navController, onAddAircraft = { navController.navigate("add_aircraft") })
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
