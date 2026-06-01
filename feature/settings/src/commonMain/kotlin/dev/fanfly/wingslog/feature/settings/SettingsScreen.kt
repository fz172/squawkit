package dev.fanfly.wingslog.feature.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.appinfo.getAppVersion
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.data.UserStatus
import dev.fanfly.wingslog.feature.settings.upgrade.AccountUpgradeViewModel
import dev.fanfly.wingslog.feature.settings.upgrade.UpgradeUiState
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs
import wingslog.feature.settings.generated.resources.account_upgrade_error
import wingslog.feature.settings.generated.resources.account_upgrade_login_cta
import wingslog.feature.settings.generated.resources.account_upgrade_login_subtitle
import wingslog.feature.settings.generated.resources.account_upgrade_success
import wingslog.feature.settings.generated.resources.account_upgrade_working
import wingslog.feature.settings.generated.resources.app_version
import wingslog.feature.settings.generated.resources.feature_lab
import wingslog.feature.settings.generated.resources.settings_export_subtitle
import wingslog.feature.settings.generated.resources.settings_feature_lab_subtitle
import wingslog.feature.settings.generated.resources.settings_logout_subtitle
import wingslog.feature.settings.generated.resources.settings_sync_subtitle
import wingslog.feature.settings.generated.resources.settings_technicians_subtitle
import wingslog.feature.settings.generated.resources.sign_out
import wingslog.feature.sync.sharedassets.generated.resources.feature_name_backup_and_sync
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians
import wingslog.feature.export.sharedassets.generated.resources.Res as ExportRes
import wingslog.feature.settings.generated.resources.Res as SettingsRes
import wingslog.feature.sync.sharedassets.generated.resources.Res as SyncRes
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes


/**
 * The settings body — profile card, technician profiles, sync/cloud backup, Export entry point,
 * Feature Lab, account action, and app version — with no chrome of its own. The adaptive shell
 * renders it inside its Settings section. Secondary flows still navigate via [navController] to
 * their existing routes.
 */
@Composable
fun SettingsContent(
  navController: NavController,
  modifier: Modifier = Modifier,
  settingsViewModel: SettingsViewModel = koinViewModel(),
  accountUpgradeViewModel: AccountUpgradeViewModel = koinViewModel(),
  onExportLogs: (() -> Unit)? = { navController.navigate(Screen.ExportLogs.route) },
) {
  val user by settingsViewModel.user.collectAsStateWithLifecycle()
  val appearanceMode by settingsViewModel.appearanceMode.collectAsStateWithLifecycle()
  val upgradeState by accountUpgradeViewModel.state.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  val upgradeSuccessMessage = stringResource(SettingsRes.string.account_upgrade_success)
  val upgradeErrorMessage = stringResource(SettingsRes.string.account_upgrade_error)

  LaunchedEffect(user) {
    if (user.userStatus == UserStatus.LOGGED_OUT) {
      navController.navigate(Screen.Login.route) {
        popUpTo(Screen.AdaptiveShell.route) { inclusive = true }
        launchSingleTop = true
      }
    }
  }

  // Terminal upgrade states surface as a snackbar, then reset to Idle.
  LaunchedEffect(upgradeState) {
    when (upgradeState) {
      is UpgradeUiState.Success -> {
        // Linking didn't fire authStateChanged; pull the new photo / non-anonymous state in now.
        settingsViewModel.refreshAccountState()
        snackbarHostState.showSnackbar(upgradeSuccessMessage)
        accountUpgradeViewModel.dismiss()
      }

      is UpgradeUiState.Error -> {
        snackbarHostState.showSnackbar(upgradeErrorMessage)
        accountUpgradeViewModel.dismiss()
      }

      else -> Unit
    }
  }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .constrainedContentWidth(ContentWidth.Reading)
        .fillMaxSize()
        .padding(Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.columnGap),
    ) {
      UserProfileCard(
        self = user.selfTechnician,
        photoUri = user.photoUri,
      )

      // For a guest with the upgrade flag on, "Log in" connects their on-device records to a real
      // account (the upgrade flow). It replaces the destructive guest logout entirely.
      val guestCanUpgrade = user.isAnonymous && user.featureFlags.accountUpgradeEnabled

      if (user.featureFlags.technicianEnabled) {
        SettingsRow(
          icon = Icons.Default.Engineering,
          title = stringResource(TechnicianRes.string.manage_technicians),
          subtitle = stringResource(SettingsRes.string.settings_technicians_subtitle),
          onClick = { navController.navigate(Screen.ManageTechnicians.route) },
          settingsLevel = SettingsLevel.DEFAULT,
        )
      }

      SettingsRow(
        icon = Icons.Default.CloudSync,
        title = stringResource(SyncRes.string.feature_name_backup_and_sync),
        subtitle = stringResource(SettingsRes.string.settings_sync_subtitle),
        onClick = { navController.navigate(Screen.SyncSettings.route) },
        settingsLevel = SettingsLevel.DEFAULT,
      )

      if (onExportLogs != null) {
        SettingsRow(
          icon = Icons.Default.FileDownload,
          title = stringResource(ExportRes.string.feature_name_export_logs),
          subtitle = stringResource(SettingsRes.string.settings_export_subtitle),
          onClick = onExportLogs,
          settingsLevel = SettingsLevel.DEFAULT,
        )
      }

      SettingsRow(
        icon = Icons.Default.Tune,
        title = stringResource(SettingsRes.string.feature_lab),
        subtitle = stringResource(SettingsRes.string.settings_feature_lab_subtitle),
        onClick = { navController.navigate(Screen.FeatureLab.route) },
        settingsLevel = SettingsLevel.DEFAULT,
      )

      AppearanceSettingRow(
        mode = appearanceMode,
        onModeChange = settingsViewModel::setAppearance,
      )

      // Guest + flag on shows "Log in" (runs the upgrade); real accounts show "Log out". An
      // anonymous user without the upgrade flag has no sign-out action — logging out would
      // erase their on-device data, so we don't offer it.
      if (guestCanUpgrade || !user.isAnonymous) {
        SettingsRow(
          icon = if (guestCanUpgrade) Icons.AutoMirrored.Filled.Login
          else Icons.AutoMirrored.Filled.Logout,
          title = stringResource(
            if (guestCanUpgrade) SettingsRes.string.account_upgrade_login_cta
            else SettingsRes.string.sign_out
          ),
          subtitle = if (guestCanUpgrade) {
            stringResource(SettingsRes.string.account_upgrade_login_subtitle)
          } else {
            stringResource(SettingsRes.string.settings_logout_subtitle)
          },
          onClick = {
            if (guestCanUpgrade) accountUpgradeViewModel.startUpgrade()
            else settingsViewModel.logOut()
          },
          settingsLevel = if (guestCanUpgrade) SettingsLevel.DEFAULT else SettingsLevel.DANGER,
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      Text(
        text = stringResource(SettingsRes.string.app_version, getAppVersion()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }

    SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
  }

  when (upgradeState) {
    is UpgradeUiState.Working -> AlertDialog(
      // Non-dismissable: provider sign-in / sync re-keying is in flight.
      onDismissRequest = {},
      confirmButton = {},
      text = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(Spacing.xLarge))
          Text(stringResource(SettingsRes.string.account_upgrade_working))
        }
      },
    )

    else -> Unit
  }
}
