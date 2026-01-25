package dev.fanfly.wingslog

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fanfly.wingslog.fleet.DashboardScreen
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
        onOpenSettings = { navController.navigate("settings") })
    }
    composable("settings") {
      SettingsScreen(navController = navController)
    }

    composable("edit_profile") {
      EditProfileScreen(navController = navController)
    }
  }
}