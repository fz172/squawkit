package dev.fanfly.wingslog.onboarding

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class OnboardingPreferences(
  private val db: WingsLogDatabase,
  private val auth: FirebaseAuth,
  private val ioContext: CoroutineContext = Dispatchers.IO,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

  @OptIn(ExperimentalCoroutinesApi::class)
  val hasSeenWelcome: StateFlow<Boolean> = auth.authStateChanged
    .flatMapLatest { user ->
      val uid = user?.uid ?: return@flatMapLatest flowOf(false)
      db.schemaQueries.selectConfig(uid, KEY_HAS_SEEN_WELCOME).asFlow()
        .mapToOneOrNull(ioContext)
        .map { it?.toBoolean() ?: false }
    }
    .stateIn(scope, SharingStarted.Eagerly, false)

  suspend fun checkHasSeenWelcome(): Boolean {
    val uid = auth.currentUser?.uid ?: return false
    return db.schemaQueries.selectConfig(uid, KEY_HAS_SEEN_WELCOME)
      .executeAsOneOrNull()?.toBoolean() ?: false
  }

  fun setHasSeenWelcome() {
    val uid = auth.currentUser?.uid ?: return
    db.schemaQueries.upsertConfig(uid, KEY_HAS_SEEN_WELCOME, true.toString())
  }

  companion object {
    private const val KEY_HAS_SEEN_WELCOME = "onboarding_has_seen_welcome"
  }
}
