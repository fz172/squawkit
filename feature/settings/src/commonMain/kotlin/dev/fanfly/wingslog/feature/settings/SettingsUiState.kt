package dev.fanfly.wingslog.feature.settings

import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags

data class SettingsUiState(
  val userStatus: UserStatus = UserStatus.UNKNOWN,
  val featureFlags: DeveloperFlags = DeveloperFlags(),
  val notificationsRowState: NotificationsRowState = NotificationsRowState.DEFAULT,
  // Guest (anonymous) accounts keep all data on-device only; logging out erases it permanently.
  val isAnonymous: Boolean = false,
  val isDeveloperOptionsSupported: Boolean = false,
  /** The profile card: the self-technician's name (falling back through the account), its email and photo. */
  val displayName: String? = null,
  val email: String? = null,
  val photoUrl: String? = null,
  /** Null until the entitlement has been read — the row says nothing rather than guessing Basic. */
  val plan: PlanRow? = null,
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
