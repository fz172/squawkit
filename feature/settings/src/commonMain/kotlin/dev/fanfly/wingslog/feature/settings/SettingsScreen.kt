package dev.fanfly.wingslog.feature.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
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
import dev.fanfly.wingslog.feature.login.upgrade.AccountUpgradeViewModel
import dev.fanfly.wingslog.feature.settings.data.NotificationsRowState
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.data.UserStatus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.settings
import wingslog.feature.export.sharedassets.generated.resources.feature_name_export_logs
import wingslog.feature.settings.generated.resources.account_upgrade_link_cta
import wingslog.feature.settings.generated.resources.account_upgrade_link_subtitle
import wingslog.feature.settings.generated.resources.app_version
import wingslog.feature.settings.generated.resources.developer_options
import wingslog.feature.settings.generated.resources.settings_ad_privacy
import wingslog.feature.settings.generated.resources.settings_ad_privacy_subtitle
import wingslog.feature.settings.generated.resources.settings_delete_account
import wingslog.feature.settings.generated.resources.settings_delete_account_subtitle
import wingslog.feature.settings.generated.resources.settings_developer_options_subtitle
import wingslog.feature.settings.generated.resources.settings_export_subtitle
import wingslog.feature.settings.generated.resources.settings_logout_subtitle
import wingslog.feature.settings.generated.resources.settings_notifications
import wingslog.feature.settings.generated.resources.settings_notifications_subtitle_blocked
import wingslog.feature.settings.generated.resources.settings_notifications_subtitle_default
import wingslog.feature.settings.generated.resources.settings_notifications_subtitle_off
import wingslog.feature.settings.generated.resources.settings_subscription
import wingslog.feature.settings.generated.resources.settings_subscription_subtitle
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
  val snackbarHostState = remember { SnackbarHostState() }

  // With a sidebar, detail pages embed via the nested controller; otherwise they open full-screen
  // off the root controller.
  val hasSidebar = LocalLayoutTier.current.hasFullSidebar
  val detailNav = if (hasSidebar) sectionNavController else navController

  // The account row is chosen from isAnonymous, and linking never fires authStateChanged, so this
  // ViewModel would otherwise keep serving a stale snapshot. Re-read on entry for an upgrade that
  // finished while Settings was off-screen, and on each completion for one that finishes while it
  // is open — the flow is hosted by the shell, so both happen.
  LaunchedEffect(Unit) { settingsViewModel.refreshAccountState() }
  LaunchedEffect(accountUpgradeViewModel) {
    accountUpgradeViewModel.completions.collect { settingsViewModel.refreshAccountState() }
  }

  LaunchedEffect(user) {
    if (user.userStatus == UserStatus.LOGGED_OUT) {
      navController.navigate(Screen.Login.route) {
        popUpTo(Screen.AdaptiveShell.route) { inclusive = true }
        launchSingleTop = true
      }
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

        val generalRows = buildList<@Composable () -> Unit> {
          add {
            AppearanceSettingRow(
              mode = appearanceMode,
              onModeChange = settingsViewModel::setAppearance,
            )
          }
          add {
            SettingsRow(
              icon = Icons.Default.WorkspacePremium,
              title = stringResource(SettingsRes.string.settings_subscription),
              subtitle = stringResource(SettingsRes.string.settings_subscription_subtitle),
              onClick = { detailNav.navigate(Screen.Subscription.route) },
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
              icon = Icons.Default.Notifications,
              title = stringResource(SettingsRes.string.settings_notifications),
              subtitle = stringResource(
                when (user.notificationsRowState) {
                  NotificationsRowState.BLOCKED -> SettingsRes.string.settings_notifications_subtitle_blocked
                  NotificationsRowState.OFF -> SettingsRes.string.settings_notifications_subtitle_off
                  NotificationsRowState.DEFAULT -> SettingsRes.string.settings_notifications_subtitle_default
                }
              ),
              onClick = { detailNav.navigate(Screen.Notifications.route) },
            )
          }
        }
        val supportRows = buildList<@Composable () -> Unit> {
          add {
            FirebaseLoggingSettingRow(
              enabled = firebaseLoggingEnabled,
              onEnabledChange = settingsViewModel::setFirebaseLoggingEnabled,
            )
          }
          // Only when there's actually a CMP form to re-present right now — not just wherever this
          // build ships ads — so tapping the row never silently does nothing (#384).
          if (user.isAdPrivacyOptionsAvailable) {
            add {
              SettingsRow(
                icon = Icons.Default.PrivacyTip,
                title = stringResource(SettingsRes.string.settings_ad_privacy),
                subtitle = stringResource(SettingsRes.string.settings_ad_privacy_subtitle),
                onClick = settingsViewModel::presentAdPrivacyOptions,
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
        val dataManagementRows = buildList<@Composable () -> Unit> {
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
              icon = Icons.Default.FileDownload,
              title = stringResource(ExportRes.string.feature_name_export_logs),
              subtitle = stringResource(SettingsRes.string.settings_export_subtitle),
              onClick = { detailNav.navigate(Screen.ExportLogs.route) },
            )
          }
        }
        val accountRows = buildList<@Composable () -> Unit> {
          // Guest shows "Link to an account" (runs the upgrade); real accounts show "Log out".
          //
          // The branch is load-bearing, not cosmetic: a guest has no cloud copy, so logOut()'s wipe
          // would destroy every thing, log, task, squawk, and attachment unrecoverably — and
          // "Sign out of your account on this device" says the opposite of what that does. Guests
          // are offered the way *in* instead, which is also the only thing that makes their data
          // recoverable. Keep it that way: a guest sign-out needs an explicit erase warning ahead of
          // it, never this row (#413).
          if (user.isAnonymous) {
            add {
              SettingsRow(
                icon = Icons.AutoMirrored.Filled.Login,
                title = stringResource(SettingsRes.string.account_upgrade_link_cta),
                subtitle =
                  stringResource(SettingsRes.string.account_upgrade_link_subtitle),
                onClick = { accountUpgradeViewModel.choose() },
              )
            }
          } else {
            add {
              SettingsRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = stringResource(SettingsRes.string.sign_out),
                subtitle = stringResource(SettingsRes.string.settings_logout_subtitle),
                onClick = { settingsViewModel.logOut() },
              )
            }
            // Below Log out, and only for a permanent account. Required by App Store Review
            // Guideline 5.1.1(v) — which applies to any app offering account creation, not just
            // Apple sign-in (#418). A guest has no account to delete: their exit is the upgrade row
            // above, and logOut()'s wipe is already off-limits to them (#413).
            add {
              SettingsRow(
                icon = Icons.Default.DeleteForever,
                title = stringResource(SettingsRes.string.settings_delete_account),
                subtitle = stringResource(SettingsRes.string.settings_delete_account_subtitle),
                settingsLevel = SettingsLevel.DANGER,
                onClick = { settingsViewModel.askToDeleteAccount() },
              )
            }
          }
        }
        SettingsRowGroup(generalRows)
        SettingsRowGroup(dataManagementRows)
        SettingsRowGroup(supportRows)
        SettingsRowGroup(accountRows)

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

    // Guideline 5.1.1(v) wants deletion reachable, not easy to do by accident — so the row opens
    // this rather than acting, and the confirm button stays inert until the pilot has typed their
    // email address (or a fixed phrase, when the account has no address they would recognise).
    DeleteAccountDialog(
      state = user.deletion,
      challenge = user.deletionChallenge,
      typed = user.deletionInput,
      onTypedChange = settingsViewModel::setDeleteAccountInput,
      onConfirm = settingsViewModel::confirmDeleteAccount,
      onDismiss = settingsViewModel::cancelDeleteAccount,
    )

    SnackbarHost(
      snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter)
    )
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
