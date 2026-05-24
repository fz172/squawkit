package dev.fanfly.wingslog.feature.settings


import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.appinfo.getAppVersion
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.data.UserStatus
import dev.fanfly.wingslog.feature.settings.upgrade.AccountUpgradeViewModel
import dev.fanfly.wingslog.feature.settings.upgrade.UpgradeUiState
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose.UserProfileCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.settings
import wingslog.feature.settings.generated.resources.anon_logout_warning_body
import wingslog.feature.settings.generated.resources.anon_logout_warning_confirm
import wingslog.feature.settings.generated.resources.anon_logout_warning_title
import wingslog.feature.settings.generated.resources.account_upgrade_error
import wingslog.feature.settings.generated.resources.account_upgrade_info_body
import wingslog.feature.settings.generated.resources.account_upgrade_info_confirm
import wingslog.feature.settings.generated.resources.account_upgrade_info_title
import wingslog.feature.settings.generated.resources.account_upgrade_login_cta
import wingslog.feature.settings.generated.resources.account_upgrade_merge_body
import wingslog.feature.settings.generated.resources.account_upgrade_merge_confirm
import wingslog.feature.settings.generated.resources.account_upgrade_merge_title
import wingslog.feature.settings.generated.resources.account_upgrade_success
import wingslog.feature.settings.generated.resources.account_upgrade_working
import wingslog.feature.settings.generated.resources.app_version
import wingslog.feature.settings.generated.resources.feature_lab
import wingslog.feature.settings.generated.resources.sign_out
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs
import wingslog.feature.sync.sharedassets.generated.resources.feature_name_backup_and_sync
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians
import wingslog.feature.settings.generated.resources.Res as SettingsRes
import wingslog.feature.export.sharedassets.generated.resources.Res as ExportRes
import wingslog.feature.sync.sharedassets.generated.resources.Res as SyncRes
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  navController: NavController,
  settingsViewModel: SettingsViewModel = koinViewModel(),
  accountUpgradeViewModel: AccountUpgradeViewModel = koinViewModel(),
) {

  val user by settingsViewModel.user.collectAsStateWithLifecycle()
  val upgradeState by accountUpgradeViewModel.state.collectAsStateWithLifecycle()
  var showAnonLogoutWarning by remember { mutableStateOf(false) }
  var showLoginInfo by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  val upgradeSuccessMessage = stringResource(SettingsRes.string.account_upgrade_success)
  val upgradeErrorMessage = stringResource(SettingsRes.string.account_upgrade_error)

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

  // Terminal upgrade states surface as a snackbar, then reset to Idle.
  LaunchedEffect(upgradeState) {
    when (upgradeState) {
      is UpgradeUiState.Success -> {
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

  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = stringResource(Res.string.settings),
        onBackClick = { navController.popBackStack() })
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .padding(Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.columnGap)
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
        icon = Icons.Default.FileDownload,
        title = stringResource(ExportRes.string.feature_name_export_logs),
        onClick = { navController.navigate(Screen.ExportLogs.route) },
        settingsLevel = SettingsLevel.DEFAULT
      )

      SettingsRow(
        icon = Icons.Default.Tune,
        title = stringResource(SettingsRes.string.feature_lab),
        onClick = { navController.navigate(Screen.FeatureLab.route) },
        settingsLevel = SettingsLevel.DEFAULT
      )

      SettingsRow(
        icon = if (guestCanUpgrade) Icons.AutoMirrored.Filled.Login
        else Icons.AutoMirrored.Filled.Logout,
        title = stringResource(
          if (guestCanUpgrade) SettingsRes.string.account_upgrade_login_cta
          else SettingsRes.string.sign_out
        ),
        // Guest + flag on: "Log in" opens the info dialog, then the upgrade. Real accounts log out.
        // Guest without the flag still gets the destructive erase warning.
        onClick = {
          when {
            guestCanUpgrade -> showLoginInfo = true
            user.isAnonymous -> showAnonLogoutWarning = true
            else -> settingsViewModel.logOut()
          }
        },
        settingsLevel = if (guestCanUpgrade) SettingsLevel.DEFAULT else SettingsLevel.DANGER,
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

  if (showAnonLogoutWarning) {
    AlertDialog(
      onDismissRequest = { showAnonLogoutWarning = false },
      title = { Text(stringResource(SettingsRes.string.anon_logout_warning_title)) },
      text = { Text(stringResource(SettingsRes.string.anon_logout_warning_body)) },
      confirmButton = {
        TextButton(
          onClick = {
            showAnonLogoutWarning = false
            settingsViewModel.logOut()
          }
        ) {
          Text(
            text = stringResource(SettingsRes.string.anon_logout_warning_confirm),
            color = MaterialTheme.colorScheme.error,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showAnonLogoutWarning = false }) {
          Text(stringResource(Res.string.cancel))
        }
      },
    )
  }

  // Informational dialog shown before the upgrade so the guest knows what logging in does.
  if (showLoginInfo) {
    AlertDialog(
      onDismissRequest = { showLoginInfo = false },
      title = { Text(stringResource(SettingsRes.string.account_upgrade_info_title)) },
      text = { Text(stringResource(SettingsRes.string.account_upgrade_info_body)) },
      confirmButton = {
        TextButton(
          onClick = {
            showLoginInfo = false
            accountUpgradeViewModel.startUpgrade()
          }
        ) {
          Text(stringResource(SettingsRes.string.account_upgrade_info_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { showLoginInfo = false }) {
          Text(stringResource(Res.string.cancel))
        }
      },
    )
  }

  when (upgradeState) {
    is UpgradeUiState.ConfirmMerge -> AlertDialog(
      onDismissRequest = { accountUpgradeViewModel.cancelMerge() },
      title = { Text(stringResource(SettingsRes.string.account_upgrade_merge_title)) },
      text = { Text(stringResource(SettingsRes.string.account_upgrade_merge_body)) },
      confirmButton = {
        TextButton(onClick = { accountUpgradeViewModel.confirmMerge() }) {
          Text(stringResource(SettingsRes.string.account_upgrade_merge_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { accountUpgradeViewModel.cancelMerge() }) {
          Text(stringResource(Res.string.cancel))
        }
      },
    )

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
