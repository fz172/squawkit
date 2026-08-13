package dev.fanfly.wingslog.feature.settings.data

import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags

data class SettingsUiState(
  val userStatus: UserStatus = UserStatus.UNKNOWN,
  val featureFlags: DeveloperFlags = DeveloperFlags(),
  // Guest (anonymous) accounts keep all data on-device only; logging out erases it permanently.
  val isAnonymous: Boolean = false,
  val isDeveloperOptionsSupported: Boolean = false,
  val isSubscriptionSupported: Boolean = false,
  val deletion: AccountDeletion = AccountDeletion.Idle,
)

/**
 * Where the "Delete Account" flow has got to (#418).
 *
 * In the ViewModel rather than in a composable `remember`, so the confirmation cannot be dismissed
 * out from under an in-flight delete by a recomposition or a configuration change.
 */
enum class AccountDeletion {
  Idle,

  /** The confirmation is on screen. Nothing has happened yet. */
  Confirming,

  /** The callable is running. Not cancellable — the server is already partway through. */
  Working,

  /**
   * The delete did not happen and the account is intact. Local data is deliberately left alone: a
   * wipe here would destroy the only copy of records the account still holds.
   */
  Failed,
}

enum class UserStatus {
  UNKNOWN,
  LOADING,
  LOGGED_OUT,
}
