package dev.fanfly.wingslog.feature.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.common.compose.getAppVersion
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.data.UserStatus
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCard
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCardData
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.settings
import wingslog.feature.settings.generated.resources.Res as SettingsRes
import wingslog.feature.settings.generated.resources.app_version
import wingslog.feature.settings.generated.resources.feature_lab
import wingslog.feature.settings.generated.resources.sign_out
import wingslog.feature.sync.sharedassets.generated.resources.Res as SyncRes
import wingslog.feature.sync.sharedassets.generated.resources.feature_name_backup_and_sync
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes
import wingslog.feature.technician.sharedassets.generated.resources.setting_item_manage_technicians


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  navController: NavController,
  settingsViewModel: SettingsViewModel = koinViewModel(),
) {

  val user by settingsViewModel.user.collectAsStateWithLifecycle()

  // This LaunchedEffect will run when 'user' state changes
  LaunchedEffect(user) {
    if (user.userStatus == UserStatus.LOGGED_OUT) {
      // If the user becomes logged out, go to log in and clear everything up to main
      navController.navigate(Screen.Login.route) {
        popUpTo(Screen.Dashboard.route) {
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
        title = stringResource(Res.string.settings),
        onBackClick = { navController.popBackStack() })
    }) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .padding(Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.columnGap)
    ) {
      UserProfileCard(
        data = UserProfileCardData(
          displayName = user.displayName,
          photoUri = user.photoUri,
          licenceInfo = user.licenseInfo,
        ),
        onOpenEditProfile = { navController.navigate(Screen.EditProfile.route) }
      )
      if (user.featureFlags.technicianEnabled) {
        SettingsRow(
          icon = Icons.Default.Engineering,
          title = stringResource(TechnicianRes.string.setting_item_manage_technicians),
          onClick = { navController.navigate(Screen.ManageTechnicians.route) },
          settingsLevel = SettingsLevel.DEFAULT
        )
      }

      SettingsRow(
        icon = Icons.Default.CloudSync,
        title = stringResource(SyncRes.string.feature_name_backup_and_sync),
        onClick = { navController.navigate(Screen.SyncSettings.route) },
        settingsLevel = SettingsLevel.DEFAULT
      )

      SettingsRow(
        icon = Icons.Default.Tune,
        title = stringResource(SettingsRes.string.feature_lab),
        onClick = { navController.navigate(Screen.FeatureLab.route) },
        settingsLevel = SettingsLevel.DEFAULT
      )

      SettingsRow(
        icon = Icons.AutoMirrored.Filled.Logout,
        title = stringResource(SettingsRes.string.sign_out),
        onClick = { settingsViewModel.logOut() },
        settingsLevel = SettingsLevel.DANGER
      )

      Spacer(modifier = Modifier.weight(1f))

      Text(
        text = stringResource(
          SettingsRes.string.app_version,
          getAppVersion()
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )
    }
  }
}
