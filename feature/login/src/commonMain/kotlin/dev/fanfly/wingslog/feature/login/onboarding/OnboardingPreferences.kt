package dev.fanfly.wingslog.feature.login.onboarding

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.auth.FirebaseAuth

class OnboardingPreferences(
  private val db: WingsLogDatabase,
  private val auth: FirebaseAuth,
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
    db.schemaQueries.upsertConfig(uid, KEY_HAS_SEEN_WELCOME, true.toString())
  }

  companion object {
    private const val KEY_HAS_SEEN_WELCOME = "onboarding_has_seen_welcome"
  }
}
