package dev.fanfly.wingslog.feature.login.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Persists the email a passwordless sign-in link was issued for, so leg 2 can complete after an app
 * restart or (on web) a fresh page load. The link may be opened on a different device, in which case
 * nothing is stashed here and the UI prompts for the address instead.
 *
 * Stored in the existing `sync_config` table under a constant pseudo-uid, because the user has no
 * real uid yet when the link is sent. See docs/account/email_link_signin_design.html.
 */
class EmailLinkStore(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  suspend fun pendingEmail(): String? =
    db.schemaQueries.selectConfig(SCOPE, KEY_PENDING_EMAIL)
      .awaitAsOneOrNull()
      ?.takeIf { it.isNotBlank() }

  suspend fun savePendingEmail(email: String) {
    writeLock.withLock {
      db.schemaQueries.upsertConfig(SCOPE, KEY_PENDING_EMAIL, email)
    }
  }

  /** Clears the stash after a successful sign-in (or when the user picks a different email). */
  suspend fun clear() {
    writeLock.withLock {
      db.schemaQueries.upsertConfig(SCOPE, KEY_PENDING_EMAIL, "")
    }
  }

  private companion object {
    // Not a real Firebase uid — a fixed scope so the stash survives before the user is signed in.
    private const val SCOPE = "__email_link_pending__"
    private const val KEY_PENDING_EMAIL = "pending_signin_email"
  }
}
