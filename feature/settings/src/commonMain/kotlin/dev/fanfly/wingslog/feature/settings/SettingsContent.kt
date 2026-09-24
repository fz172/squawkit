package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.upgrade.AccountUpgradeViewModel
import dev.fanfly.wingslog.feature.settings.account.DeleteAccountDialog
import dev.fanfly.wingslog.feature.settings.profile.ProfileCard
import dev.fanfly.wingslog.feature.settings.section.AccountSection
import dev.fanfly.wingslog.feature.settings.section.DataSection
import dev.fanfly.wingslog.feature.settings.section.PreferencesSection
import dev.fanfly.wingslog.feature.settings.section.SupportSection
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.settings

/**
 * The settings body — the profile card (who you are, what plan you are on), then Preferences,
 * Data, Support and Account groups.
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

  // The profile card resolves its destination asynchronously (the self record may need seeding).
  LaunchedEffect(settingsViewModel) {
    settingsViewModel.profileRequests.collect { target ->
      when (target) {
        is ProfileTarget.Self ->
          detailNav.navigate(Screen.EditTechnician.createRoute(target.technicianId))

        ProfileTarget.Roster -> detailNav.navigate(Screen.ManageTechnicians.route)
      }
    }
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
      // The whole page scrolls as one, so every row stays reachable on short screens. On compact
      // tiers the shell runs Settings edge-to-edge under the transparent system navigation bar, so
      // the scroll content re-adds that bottom inset (after verticalScroll so it scrolls with the
      // content) to keep the last row above the gesture bar.
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
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
      ) {
        // In sidebar mode the shell drops its "Settings" top bar (the section owns its chrome), so
        // the page renders its own title/subtitle. Compact tiers still get the title from the shell.
        if (hasSidebar) {
          SettingsHeader()
        }

        ProfileCard(
          user = user,
          onOpenProfile = settingsViewModel::openProfile,
          onOpenSubscription = { detailNav.navigate(Screen.Subscription.route) },
        )

        PreferencesSection(
          user = user,
          appearanceMode = appearanceMode,
          onAppearanceChange = settingsViewModel::setAppearance,
          onOpenNotifications = { detailNav.navigate(Screen.Notifications.route) },
          onOpenSync = { detailNav.navigate(Screen.SyncSettings.route) },
        )

        DataSection(
          onOpenTechnicians = { detailNav.navigate(Screen.ManageTechnicians.route) },
          onOpenExport = { detailNav.navigate(Screen.ExportLogs.route) },
        )

        SupportSection(
          user = user,
          firebaseLoggingEnabled = firebaseLoggingEnabled,
          onFirebaseLoggingChange = settingsViewModel::setFirebaseLoggingEnabled,
          onPresentAdPrivacyOptions = settingsViewModel::presentAdPrivacyOptions,
          onOpenAbout = { detailNav.navigate(Screen.About.route) },
          onOpenDeveloperOptions = { detailNav.navigate(Screen.DeveloperOptions.route) },
        )

        AccountSection(
          user = user,
          onLinkAccount = { accountUpgradeViewModel.choose() },
          onLogOut = { settingsViewModel.logOut() },
          onDeleteAccount = { settingsViewModel.askToDeleteAccount() },
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
