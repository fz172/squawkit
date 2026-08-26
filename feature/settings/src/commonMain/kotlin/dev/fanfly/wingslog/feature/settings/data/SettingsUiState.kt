package dev.fanfly.wingslog.feature.settings.data

import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags

/**
 * What the Notifications row's live subtitle should say (design §9.1). Computed once in the
 * ViewModel from [dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission] and
 * [dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager] rather than
 * exposing either raw stream to the view, so the precedence between "blocked at the OS level" and
 * "the user turned it off in-app" lives in one place. `BLOCKED` wins over `OFF`: fixing an OS-level
 * block is the more actionable thing to surface, and it is true regardless of the in-app toggle.
 */
enum class NotificationsRowState {
  /** Permission undetermined/granted and the master switch is on — the everyday case. */
  DEFAULT,

  /** Permission is fine; the user's own master switch is off. */
  OFF,

  /** OS-level permission is denied or the platform cannot show notifications at all. */
  BLOCKED,
}

data class SettingsUiState(
  val userStatus: UserStatus = UserStatus.UNKNOWN,
  val featureFlags: DeveloperFlags = DeveloperFlags(),
  val notificationsRowState: NotificationsRowState = NotificationsRowState.DEFAULT,
  // Guest (anonymous) accounts keep all data on-device only; logging out erases it permanently.
  val isAnonymous: Boolean = false,
  val isDeveloperOptionsSupported: Boolean = false,
  /**
   * Staged-rollout gate for the Notifications row — see [dev.fanfly.wingslog.core.appinfo.AppCapability.isNotificationsSupported].
   * Not a platform-capability read; comes out once the feature ships.
   */
  val isNotificationsSupported: Boolean = false,
  /**
   * Whether "Ad privacy settings" has a CMP form to re-present right now (#384) — not just whether
   * this build ships ads. False until some ad slot has resolved consent this session (the CMP call
   * is lazy) and stays false outside a region requiring a privacy choice, so the row only appears
   * when tapping it would actually do something.
   */
  val isAdPrivacyOptionsAvailable: Boolean = false,
  val deletion: AccountDeletion = AccountDeletion.Idle,
  /**
   * What the pilot has to type out to get past the confirmation (#418). Resolved when the
   * confirmation opens, so it cannot change under half-typed input.
   */
  val deletionChallenge: DeletionChallenge = DeletionChallenge.Phrase,
  /** The confirmation text typed so far. In the ViewModel so recomposition cannot drop it. */
  val deletionInput: String = "",
)

/**
 * The thing a pilot has to type before "Delete my account" does anything (#418).
 *
 * A single tap on a destructive button is too cheap for an irreversible, un-undoable delete that
 * also cuts off everyone they have shared an aircraft with — so the confirmation asks for something
 * only someone who means it will produce.
 */
sealed interface DeletionChallenge {
  /** The account has an address the pilot would recognise, so that address is what they type. */
  data class Email(val address: String) : DeletionChallenge

  /**
   * No address worth asking for — a provider gave us none, or it is an Apple Hide My Email alias
   * the pilot has never seen. They type a fixed phrase instead; the dialog owns its wording, since
   * it is a localized string.
   */
  data object Phrase : DeletionChallenge
}

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
