package dev.fanfly.wingslog.feature.settings.section

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.grouped.GroupedSection
import dev.fanfly.wingslog.feature.settings.SettingsUiState
import dev.fanfly.wingslog.feature.settings.row.SettingsLevel
import dev.fanfly.wingslog.feature.settings.row.SettingsRow
import dev.fanfly.wingslog.feature.settings.row.SettingsRowGroup
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.account_upgrade_link_cta
import wingslog.feature.settings.generated.resources.account_upgrade_link_subtitle
import wingslog.feature.settings.generated.resources.settings_delete_account
import wingslog.feature.settings.generated.resources.settings_delete_account_subtitle
import wingslog.feature.settings.generated.resources.settings_logout_subtitle
import wingslog.feature.settings.generated.resources.settings_section_account
import wingslog.feature.settings.generated.resources.sign_out
import wingslog.feature.settings.generated.resources.Res as SettingsRes

/** Link-to-account for a guest; Log out and Delete account for a permanent account. */
@Composable
internal fun AccountSection(
  user: SettingsUiState,
  onLinkAccount: () -> Unit,
  onLogOut: () -> Unit,
  onDeleteAccount: () -> Unit,
) {
  GroupedSection(stringResource(SettingsRes.string.settings_section_account)) {
    SettingsRowGroup(
      buildList {
        // Guest shows "Link to an account" (runs the upgrade); real accounts show "Log out".
        //
        // The branch is load-bearing, not cosmetic: a guest has no cloud copy, so logOut()'s
        // wipe would destroy every thing, log, task, squawk, and attachment unrecoverably —
        // and "Sign out of your account on this device" says the opposite of what that does.
        // Guests are offered the way *in* instead, which is also the only thing that makes
        // their data recoverable. Keep it that way: a guest sign-out needs an explicit erase
        // warning ahead of it, never this row (#413).
        if (user.isAnonymous) {
          add {
            SettingsRow(
              icon = Icons.AutoMirrored.Filled.Login,
              title = stringResource(SettingsRes.string.account_upgrade_link_cta),
              subtitle = stringResource(SettingsRes.string.account_upgrade_link_subtitle),
              onClick = onLinkAccount,
            )
          }
        } else {
          add {
            SettingsRow(
              icon = Icons.AutoMirrored.Filled.Logout,
              title = stringResource(SettingsRes.string.sign_out),
              subtitle = stringResource(SettingsRes.string.settings_logout_subtitle),
              onClick = onLogOut,
            )
          }
          // Below Log out, and only for a permanent account. Required by App Store Review
          // Guideline 5.1.1(v) — which applies to any app offering account creation, not
          // just Apple sign-in (#418). A guest has no account to delete: their exit is the
          // upgrade row above, and logOut()'s wipe is already off-limits to them (#413).
          add {
            SettingsRow(
              icon = Icons.Default.DeleteForever,
              title = stringResource(SettingsRes.string.settings_delete_account),
              subtitle = stringResource(SettingsRes.string.settings_delete_account_subtitle),
              settingsLevel = SettingsLevel.DANGER,
              onClick = onDeleteAccount,
            )
          }
        }
      }
    )
  }
}
