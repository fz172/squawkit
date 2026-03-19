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
import wingslog.feature.settings.generated.resources.Res as SettingsRes
import wingslog.feature.settings.generated.resources.add_aircraft
import wingslog.feature.settings.generated.resources.sign_out
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.*
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCard
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCardData
import org.koin.compose.viewmodel.koinViewModel

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
        title = cmpStringResource(Res.string.settings),
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
          photoUri = user.firebaseUser?.photoURL?.toString(),
          licenceInfo = user.licenseInfo,
        ),
        onOpenEditProfile = { navController.navigate("edit_profile") }
      )
      SettingsRow(
        icon = Icons.Default.Add,
        title = cmpStringResource(SettingsRes.string.add_aircraft),
        onClick = { onAddAircraft() }
      )
      SettingsRow(
        icon = Icons.AutoMirrored.Filled.Logout,
        title = cmpStringResource(SettingsRes.string.sign_out),
        onClick = { settingsViewModel.logOut() },
        settingsLevel = SettingsLevel.DANGER
      )
    }
  }
}
