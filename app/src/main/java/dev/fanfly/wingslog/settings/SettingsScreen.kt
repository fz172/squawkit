package dev.fanfly.wingslog.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.common.WingsLogTopAppBar
import dev.fanfly.wingslog.dev.fanfly.wingslog.settings.SettingsLevel
import dev.fanfly.wingslog.dev.fanfly.wingslog.settings.SettingsRow
import dev.fanfly.wingslog.dev.fanfly.wingslog.settings.data.SettingsViewModel

@Composable
fun SettingsScreen(
  navController: NavController,
  settingsViewModel: SettingsViewModel = hiltViewModel(),
) {

  val user by settingsViewModel.user.collectAsStateWithLifecycle()

  // This LaunchedEffect will run when 'user' state changes
  LaunchedEffect(user) {
    if (user == null) {
      // If user becomes null (logged out), go to login and clear all other pages
      navController.navigate("login") {
        popUpTo(navController.graph.startDestinationId) {
          inclusive = true
        }
        // Ensure only one copy of the login screen
        launchSingleTop = true
      }
    }
  }

  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = stringResource(R.string.settings),
        onBackClick = { navController.popBackStack() })
    }) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      UserProfileCard(
        currentUser = user,
        onOpenEditProfile = { navController.navigate("edit_profile") })
      SettingsRow(
        icon = Icons.AutoMirrored.Filled.Logout,
        title = stringResource(R.string.sign_out),
        onClick = { settingsViewModel.logOut() },
        settingsLevel = SettingsLevel.DANGER
      )
    }
  }
}