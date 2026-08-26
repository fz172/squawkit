package dev.fanfly.wingslog.feature.notifications.datamanager

import dev.fanfly.wingslog.core.auth.AuthManager
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Signing out, with this device's push registration released first (design §7.1, issue #550).
 *
 * **The order is the property.** Deleting `users/{uid}/push_devices/{installId}` is a Firestore
 * write that rules gate on `request.auth.uid == userId`, so once the session is gone it can only be
 * permission-denied and the token survives — leaving this device holding a live address under an
 * account that has left it. Nothing prunes that: `pruneDeadTokens` only fires on a token FCM reports
 * as gone, and this one is not gone.
 *
 * **It lives here, in one place, because it was already wrong in two.** `SettingsViewModel.logOut()`
 * had this sequence; the corruption-recovery dialog in `AppEntry` called `authManager.logOut()`
 * directly and so never cleared anything, and every later sign-out path would have inherited the
 * same omission. A shared call is what makes "sign out" mean the same thing everywhere.
 *
 * Not a general session teardown: local wipes stay with their callers, because they differ. Settings
 * drops this account's cached records; corruption recovery has already wiped everything by the time
 * it gets here.
 */
class SignOutCoordinator(
  private val authManager: AuthManager,
  /**
   * Null on any platform with no push transport — iOS until P5, web by design (§8). Nullable rather
   * than a no-op binding so "this platform registers no tokens" stays visible.
   */
  private val pushTokenRegistrar: PushTokenRegistrar? = null,
) {

  /**
   * Clears the registration, then signs out. Returns once the session is gone.
   *
   * **Bounded, never awaited indefinitely.** A Firestore write Task resolves only when the backend
   * acknowledges the mutation, so offline it does not fail — it simply never settles. Unbounded,
   * that makes signing out a dead action in airplane mode: no sign-out, no wipe, no error to show.
   * Hanging the only exit from an account is the worse trade.
   *
   * A clear that fails outright is treated the same way, for the same reason. Expiring or failing
   * leaves the token document behind, and that residue is not closable from this side — a
   * queued delete can never land once the session is gone. The receive-side `recipientUid` check
   * (issue P4.13) is what covers it, and it covers the paths no sign-out can reach at all: a process
   * killed mid-delete, or a shared tablet nobody ever signs out of.
   */
  suspend fun signOut() {
    // runCatching as well as the timeout, because they cover different failures and the timeout
    // covers only one of them: `withTimeoutOrNull` returns null when the clear hangs, but a clear
    // that *throws* propagates straight through it. A permission-denied delete — the doc already
    // gone, a rules edge — would then abort the sign-out itself, which is the same class of bug as
    // #550 and worse: it strands someone in an account they asked to leave.
    runCatching {
      withTimeoutOrNull(PUSH_TOKEN_CLEAR_TIMEOUT_MS) { pushTokenRegistrar?.clearThisDevice() }
    }
    authManager.logOut()
  }

  private companion object {
    /**
     * Long enough for a healthy round-trip, short enough that a pilot on a ramp with no signal does
     * not read the action as broken. The cost of expiring is a stale token doc, not lost data.
     */
    const val PUSH_TOKEN_CLEAR_TIMEOUT_MS = 3_000L
  }
}
