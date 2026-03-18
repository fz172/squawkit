package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.settings.R
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCard
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCardData

@Composable
fun SettingsScreen(
  navController: NavController,
  settingsViewModel: SettingsViewModel = koinViewModel(),
  onAddAircraft: () -> Unit
) {

  val user by settingsViewModel.user.collectAsStateWithLifecycle()

  // This LaunchedEffect will run when 'user' state changes
  LaunchedEffect(user) {
    if (!user.isLoading && user.firebaseUser == null) {
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
        data = UserProfileCardData(
          displayName = user.firebaseUser?.displayName,
          photoUri = user.firebaseUser?.photoUrl,
          licenceInfo = user.licenseInfo,
        ),
        onOpenEditProfile = { navController.navigate("edit_profile") }
      )
      SettingsRow(
        icon = Icons.Default.Add,
        title = stringResource(R.string.add_aircraft),
        onClick = { onAddAircraft() }
      )
      SettingsRow(
        icon = Icons.AutoMirrored.Filled.Logout,
        title = stringResource(R.string.sign_out),
        onClick = { settingsViewModel.logOut() },
        settingsLevel = SettingsLevel.DANGER
      )
    }
  }
}