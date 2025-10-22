package dev.fanfly.wingslog

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fanfly.wingslog.dashboard.DashboardScreen
import dev.fanfly.wingslog.login.LoginScreen
import dev.fanfly.wingslog.login.data.AuthManager
import dev.fanfly.wingslog.settings.SettingsScreen

@Composable
fun AppEntry(authManager: AuthManager) {
  val navController = rememberNavController()

  NavHost(navController, startDestination = "login") {
    composable("login") {
      LoginScreen(
        authManager = authManager, onLoginSuccess = {
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
  }
}