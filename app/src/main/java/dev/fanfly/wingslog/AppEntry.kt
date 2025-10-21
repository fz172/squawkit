package dev.fanfly.wingslog

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import dev.fanfly.wingslog.dashboard.DashboardScreen
import dev.fanfly.wingslog.login.LoginScreen

@Composable
fun AppEntry(googleSignInClient: GoogleSignInClient) {
  val navController = rememberNavController()

  NavHost(navController, startDestination = "login") {
    composable("login") {
      LoginScreen(
        googleSignInClient = googleSignInClient,
        onLoginSuccess = {
          navController.navigate("main") {
            popUpTo("login") { inclusive = true }
          }
        }
      )
    }
    composable("main") { DashboardScreen() }
  }
}