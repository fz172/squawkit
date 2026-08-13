package dev.fanfly.wingslog.core.auth.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.auth.AccountDeleter
import dev.gitlive.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException

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
class FirebaseAccountDeleter(
  private val functions: FirebaseFunctions,
) : AccountDeleter {

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
    private val logger = Logger.withTag("AccountDeleter")
  }
}
