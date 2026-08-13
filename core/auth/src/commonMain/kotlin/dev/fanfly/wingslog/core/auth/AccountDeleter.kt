package dev.fanfly.wingslog.core.auth

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import kotlinx.coroutines.CancellationException

/**
 * Permanently deletes the signed-in account and everything belonging to it (#418).
 *
 * Required by App Store Review Guideline 5.1.1(v). An interface so the destructive call can be
 * faked in tests — nothing here is recoverable, so "it was only a mock" matters.
 */
interface AccountDeleter {
  /**
   * Deletes the account server-side. Returns false if it did not happen, in which case the caller
   * must leave the local data alone: wiping the device after a failed delete would destroy the only
   * remaining copy of records the account still holds.
   */
  suspend fun deleteAccount(): Boolean
}

/**
 * Calls the `deleteMyAccount` callable.
 *
 * Sends no payload — the server derives the account from the caller's auth token, which is what
 * makes a "delete everything" endpoint safe to expose. There is nothing here a client could lie
 * about, and no id it could substitute for someone else's.
 *
 * The Auth user is deleted by the function, using the Admin SDK. That is why this needs no
 * re-authentication: `FirebaseUser.delete()` would demand a fresh credential
 * (`requires-recent-login`) and put a second provider sheet in front of a pilot who has already
 * confirmed once.
 */
class FirebaseAccountDeleter : AccountDeleter {

  private val functions = Firebase.functions(REGION)

  override suspend fun deleteAccount(): Boolean = try {
    functions.httpsCallable("deleteMyAccount")
      .invoke()
    logger.i { "Account deleted" }
    true
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    // Not swallowed silently the way a best-effort call would be: the caller shows an error and,
    // crucially, does NOT wipe the device.
    logger.e(e) { "Account deletion failed" }
    false
  }

  private companion object {
    private const val REGION = "us-central1"
    private val logger = Logger.withTag("AccountDeleter")
  }
}
