package dev.fanfly.wingslog.feature.login.onboarding

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.auth.FirebaseAuth

class OnboardingPreferences(
  private val db: WingsLogDatabase,
  private val auth: FirebaseAuth,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  // suspend + awaitAsOneOrNull so it works on the async web (sql.js) driver, not just mobile.
  suspend fun checkHasSeenWelcome(): Boolean {
    val uid = auth.currentUser?.uid ?: return false
    return db.schemaQueries.selectConfig(uid, KEY_HAS_SEEN_WELCOME)
      .awaitAsOneOrNull()
      ?.toBoolean() ?: false
  }

  suspend fun setHasSeenWelcome() {
    val uid = auth.currentUser?.uid ?: return
    writeLock.withLock {
      db.schemaQueries.upsertConfig(uid, KEY_HAS_SEEN_WELCOME, true.toString())
    }
  }

  /**
   * Whether this account has ever seen `NotificationPrimerScreen`, on any device. Unlike the
   * original permission-only gate (`observe().value == UNDETERMINED`), this flag makes the primer a
   * true one-time notice — it still shows once for an account whose permission is already `DENIED`
   * (from a prior OS decision, or from testing before this flag existed), just with the
   * "open settings" copy instead of the request prompt.
   */
  suspend fun checkHasSeenNotificationPrimer(): Boolean {
    val uid = auth.currentUser?.uid ?: return false
    return db.schemaQueries.selectConfig(uid, KEY_HAS_SEEN_NOTIFICATION_PRIMER)
      .awaitAsOneOrNull()
      ?.toBoolean() ?: false
  }

  suspend fun setHasSeenNotificationPrimer() {
    val uid = auth.currentUser?.uid ?: return
    writeLock.withLock {
      db.schemaQueries.upsertConfig(uid, KEY_HAS_SEEN_NOTIFICATION_PRIMER, true.toString())
    }
  }

  companion object {
    private const val KEY_HAS_SEEN_WELCOME = "onboarding_has_seen_welcome"
    private const val KEY_HAS_SEEN_NOTIFICATION_PRIMER = "onboarding_has_seen_notification_primer"
  }
}
