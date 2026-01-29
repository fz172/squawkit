package dev.fanfly.wingslog

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fanfly.wingslog.aircraft.edit.EditAircraftScreen
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

    composable("add_aircraft") {
      EditAircraftScreen(navController = navController)
    }

    composable(
      route = "aircraft_overview/{aircraftId}",
      arguments = listOf(androidx.navigation.navArgument("aircraftId") {
        type = androidx.navigation.NavType.StringType
      })
    ) {
      AircraftOverviewScreen(navController = navController)
    }
  }
}