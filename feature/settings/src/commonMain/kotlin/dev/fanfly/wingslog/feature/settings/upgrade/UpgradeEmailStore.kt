package dev.fanfly.wingslog.feature.settings.upgrade

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Remembers the address an upgrade link was sent to, so the confirmation step can name it after the
 * app has been closed and reopened by the link.
 *
 * Separate from feature/login's `EmailLinkStore` on purpose, rather than shared: that one stashes
 * under a fixed pseudo-uid because sign-in has no user yet, whereas an upgrade always has one — the
 * guest. Keying by the guest uid means a link is only ever offered to the session that asked for
 * it, so reopening it against a *different* guest cannot silently attach the address to the wrong
 * local data.
 *
 * Web has no guest sessions, so this is mobile-only in practice and needs no localStorage fallback.
 */
class UpgradeEmailStore(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  suspend fun pendingEmail(guestUid: String): String? =
    db.schemaQueries.selectConfig(scopeFor(guestUid), KEY_PENDING_EMAIL)
      .awaitAsOneOrNull()
      ?.takeIf { it.isNotBlank() }

  suspend fun savePendingEmail(guestUid: String, email: String) {
    writeLock.withLock {
      db.schemaQueries.upsertConfig(scopeFor(guestUid), KEY_PENDING_EMAIL, email)
    }
  }

  /** Clears the stash once the upgrade completes, or when the user backs out of it. */
  suspend fun clear(guestUid: String) {
    writeLock.withLock {
      db.schemaQueries.upsertConfig(scopeFor(guestUid), KEY_PENDING_EMAIL, "")
    }
  }

  private companion object {
    private const val KEY_PENDING_EMAIL = "pending_upgrade_email"

    private fun scopeFor(guestUid: String) = "__account_upgrade__:$guestUid"
  }
}
