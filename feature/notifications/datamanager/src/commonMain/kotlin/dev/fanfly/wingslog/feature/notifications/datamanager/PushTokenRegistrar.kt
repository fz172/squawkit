package dev.fanfly.wingslog.feature.notifications.datamanager

import dev.fanfly.wingslog.feature.notifications.model.PushTokenSink

/**
 * Owns `users/{uid}/push_devices/{installationId}` (design §7.1) — plain fields, not proto bytes,
 * because the server must read them directly. Same rationale as the sharing ACL exception.
 *
 * Extends [PushTokenSink] rather than merely implementing it elsewhere: the registrar IS the sink a
 * platform's token-refresh callback calls, so the two contracts are the same object viewed from
 * `:datamanager`'s side versus `:viewing`'s.
 *
 * Upserts itself on sign-in and on every token refresh — self-driving on `FirebaseAuth`, the
 * `SyncPreferences` shape, so nothing external has to remember to call it. [setEnabled] and
 * [clearThisDevice] are the two triggers that need an explicit caller.
 */
interface PushTokenRegistrar : PushTokenSink {

  /**
   * The per-device silence switch (design §4, Q2) — the one preference deliberately NOT synced.
   * No settings UI in V1 (design §16, E2); this exists so Developer Options can flip it ahead of
   * one.
   */
  suspend fun setEnabled(enabled: Boolean)

  /**
   * Deletes this device's token doc. A stale token on a shared device leaks another account's
   * squawk titles into the tray (design §7.1) — this is what closes that.
   *
   * **Must be called before the caller signs out.** Firestore rules require
   * `request.auth.uid == userId` to delete this doc, so calling it after `AuthManager.logOut()` is
   * a guaranteed permission-denied. See `SettingsViewModel.logOut()`'s call site and its comment on
   * why the ordering is the opposite of the local-data wipes that follow it.
   */
  suspend fun clearThisDevice()
}
