package dev.fanfly.wingslog.feature.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Star
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
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.data.UserStatus
import dev.fanfly.wingslog.feature.settings.upgrade.AccountUpgradeViewModel
import dev.fanfly.wingslog.feature.settings.upgrade.UpgradeUiState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.settings
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs
import wingslog.feature.settings.generated.resources.account_upgrade_error
import wingslog.feature.settings.generated.resources.account_upgrade_login_cta
import wingslog.feature.settings.generated.resources.account_upgrade_login_subtitle
import wingslog.feature.settings.generated.resources.account_upgrade_success
import wingslog.feature.settings.generated.resources.account_upgrade_working
import wingslog.feature.settings.generated.resources.app_version
import wingslog.feature.settings.generated.resources.developer_options
import wingslog.feature.settings.generated.resources.settings_export_subtitle
import wingslog.feature.settings.generated.resources.settings_developer_options_subtitle
import wingslog.feature.settings.generated.resources.settings_subscription
import wingslog.feature.settings.generated.resources.settings_subscription_subtitle
import wingslog.feature.settings.generated.resources.settings_logout_subtitle
import wingslog.feature.settings.generated.resources.settings_subtitle
import wingslog.feature.settings.generated.resources.settings_sync_subtitle
import wingslog.feature.settings.generated.resources.settings_technicians_subtitle
import wingslog.feature.settings.generated.resources.sign_out
import wingslog.feature.sync.sharedassets.generated.resources.feature_name_backup_and_sync
import wingslog.feature.technician.sharedassets.generated.resources.manage_technicians
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.export.sharedassets.generated.resources.Res as ExportRes
import wingslog.feature.settings.generated.resources.Res as SettingsRes
import wingslog.feature.sync.sharedassets.generated.resources.Res as SyncRes
import wingslog.feature.technician.sharedassets.generated.resources.Res as TechnicianRes


/**
 * The settings body — profile card, technician profiles, sync/cloud backup, Export entry point,
 * Developer Options, account action, and app version.
 *
 * Detail pages embed in the content pane next to the sidebar when one is present: in that case the
 * caller passes a [sectionNavController] scoped to a nested NavHost, and the rows navigate it so the
 * detail screen renders in place (the sidebar stays). On compact tiers (no sidebar) the rows fall
 * back to [navController] and the detail pages open full-screen, as before. Login/logout always uses
 * [navController] (the root graph owns the Login route).
 */
@Composable
fun SettingsContent(
  navController: NavController,
  modifier: Modifier = Modifier,
  sectionNavController: NavController = navController,
  settingsViewModel: SettingsViewModel = koinViewModel(),
  accountUpgradeViewModel: AccountUpgradeViewModel = koinViewModel(),
) {
  val user by settingsViewModel.user.collectAsStateWithLifecycle()
  val appearanceMode by settingsViewModel.appearanceMode.collectAsStateWithLifecycle()
  val firebaseLoggingEnabled by settingsViewModel.firebaseLoggingEnabled.collectAsStateWithLifecycle()
  val upgradeState by accountUpgradeViewModel.state.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  // With a sidebar, detail pages embed via the nested controller; otherwise they open full-screen
  // off the root controller.
  val hasSidebar = LocalLayoutTier.current.hasFullSidebar
  val detailNav = if (hasSidebar) sectionNavController else navController

  val upgradeSuccessMessage =
    stringResource(SettingsRes.string.account_upgrade_success)
  val upgradeErrorMessage =
    stringResource(SettingsRes.string.account_upgrade_error)

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
        // Pane, not Reading: the settings root is top-level shell content, so it shares the shell's
        // pane cap instead of the narrower reading column (issue #101 — consistent pane width).
        .constrainedContentWidth(ContentWidth.Pane)
        .fillMaxSize()
        .padding(Spacing.screenPadding),
    ) {
      // The whole page scrolls as one, version footer included, so every row stays reachable on
      // short screens. On compact tiers the shell runs Settings edge-to-edge under the transparent
      // system navigation bar, so the scroll content re-adds that bottom inset (after verticalScroll
      // so it scrolls with the content) to keep the last row above the gesture bar.
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .then(
            if (hasSidebar) Modifier
            else Modifier.windowInsetsPadding(
              WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
            )
          ),
        verticalArrangement = Arrangement.spacedBy(Spacing.columnGap),
      ) {
        // In sidebar mode the shell drops its "Settings" top bar (the section owns its chrome), so
        // the page renders its own title/subtitle. Compact tiers still get the title from the shell.
        if (hasSidebar) {
          SettingsHeader()
        }

        // For anonymous guest "Log in" connects their on-device records to a real
        // account (the upgrade flow). It replaces the destructive guest logout entirely.
        val guestCanUpgrade = user.isAnonymous

        // The main navigation entries live in one grouped card with dividers between them; only the
        // rows that apply to this user are added, so the dividers always land correctly.
        val navRows = buildList<@Composable () -> Unit> {
          add {
            SettingsRow(
              icon = Icons.Default.Engineering,
              title = stringResource(TechnicianRes.string.manage_technicians),
              subtitle = stringResource(SettingsRes.string.settings_technicians_subtitle),
              onClick = { detailNav.navigate(Screen.ManageTechnicians.route) },
            )
          }
          add {
            SettingsRow(
              icon = Icons.Default.CloudSync,
              title = stringResource(SyncRes.string.feature_name_backup_and_sync),
              subtitle = stringResource(SettingsRes.string.settings_sync_subtitle),
              onClick = { detailNav.navigate(Screen.SyncSettings.route) },
            )
          }
          add {
            SettingsRow(
              icon = Icons.Default.FileDownload,
              title = stringResource(ExportRes.string.feature_name_export_logs),
              subtitle = stringResource(SettingsRes.string.settings_export_subtitle),
              onClick = { detailNav.navigate(Screen.ExportLogs.route) },
            )
          }
          // Shown only where the subscription capability is on (dev + dogfood today); hidden in the
          // shipping release until GA, so no user sees a paywall entry before it exists.
          if (user.isSubscriptionSupported) {
            add {
              SettingsRow(
                icon = Icons.Default.Star,
                title = stringResource(SettingsRes.string.settings_subscription),
                subtitle = stringResource(SettingsRes.string.settings_subscription_subtitle),
                onClick = { detailNav.navigate(Screen.Subscription.route) },
              )
            }
          }
          // Developer Options is a developer surface: only on debug and dogfood-style builds, never in release.
          if (user.isDeveloperOptionsSupported) {
            add {
              SettingsRow(
                icon = Icons.Default.Tune,
                title = stringResource(SettingsRes.string.developer_options),
                subtitle = stringResource(SettingsRes.string.settings_developer_options_subtitle),
                onClick = { detailNav.navigate(Screen.DeveloperOptions.route) },
              )
            }
          }
        }
        SettingsRowGroup(rows = navRows)

        SettingsRowGroup(
          rows = listOf(
            {
              AppearanceSettingRow(
                mode = appearanceMode,
                onModeChange = settingsViewModel::setAppearance,
              )
            },
            {
              FirebaseLoggingSettingRow(
                enabled = firebaseLoggingEnabled,
                onEnabledChange = settingsViewModel::setFirebaseLoggingEnabled,
              )
            },
          ),
        )

        // Guest + flag on shows "Log in" (runs the upgrade); real accounts show "Log out". An
        // anonymous user without the upgrade flag has no sign-out action — logging out would
        // erase their on-device data, so we omit the card entirely.
        if (guestCanUpgrade) {
          SettingsCard {
            SettingsRow(
              icon = Icons.AutoMirrored.Filled.Login,
              title = stringResource(SettingsRes.string.account_upgrade_login_cta),
              subtitle =
                stringResource(SettingsRes.string.account_upgrade_login_subtitle),
              onClick = { accountUpgradeViewModel.startUpgrade() },
            )
          }
        } else if (!user.isAnonymous) {
          SettingsCard {
            SettingsRow(
              icon = Icons.AutoMirrored.Filled.Logout,
              title = stringResource(SettingsRes.string.sign_out),
              subtitle = stringResource(SettingsRes.string.settings_logout_subtitle),
              onClick = { settingsViewModel.logOut() },
              settingsLevel = SettingsLevel.DANGER,
            )
          }
        }

        Spacer(modifier = Modifier.height(Spacing.columnGap))

        Text(
          text = stringResource(
            SettingsRes.string.app_version,
            getAppVersion()
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.align(Alignment.CenterHorizontally),
        )
      }
    }

    SnackbarHost(
      snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter)
    )
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

/**
 * Page title + subtitle shown only in sidebar mode, where the shell cedes its top bar to the
 * Settings section. On compact tiers the shell's app bar supplies the title instead.
 */
@Composable
private fun SettingsHeader() {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
    Text(
      text = stringResource(CoreRes.string.settings),
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = stringResource(SettingsRes.string.settings_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
