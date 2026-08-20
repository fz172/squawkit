package dev.fanfly.wingslog.feature.notifications.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.sync.data.SyncCursorStore
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.seconds

/**
 * [NotificationPrefsManager], resolving [PrefsState] per the hydration rule in design §4.3.
 *
 * **`DeveloperOptionsManagerImpl` is not the template here — copying it was the mistake this class
 * exists to avoid.** That one reads `store.observe(...)` directly and falls back to defaults on
 * `null`, which is correct there because `CollectionKind.DeveloperOptions` is not in
 * `SyncEngine.TOP_LEVEL_KINDS` and so `null` can only ever mean "never set." `NotificationSettings`
 * *is* in that list (§4.2), so `null` here is ambiguous between "never set" and "not hydrated yet" —
 * and reading (or worse, writing) through that ambiguity is exactly what [PrefsState.Unresolved]'s
 * own doc explains the cost of. The correct precedent is `TechnicianManagerImpl.awaitHydratedSelfId`,
 * which solves the identical problem for `UserInfo` — the other synced per-user singleton.
 */
class NotificationPrefsManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val cloudSyncSetting: CloudSyncSetting,
  private val cursorStore: SyncCursorStore,
  private val syncEngine: SyncEngine,
  storeFactory: EntityStoreFactory,
) : NotificationPrefsManager {

  private val store: EntityStore<NotificationSettings> =
    storeFactory.create(CollectionKind.NotificationSettings)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observe(): Flow<PrefsState> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        // Nothing to hydrate, and the settings screen must still be usable while signed out.
        flowOf(PrefsState.Resolved(NotificationSettings()))
      } else {
        val uid = user.uid
        val scope = EntityScope.userRoot(uid)
        // Re-run the resolution on every local write to the doc AND every sync hydration tick —
        // the latter is what notices "a scope just finished hydrating" for a doc that stayed
        // genuinely absent, which produces no entity write to react to on its own.
        combine(store.observe(DOC_ID, scope), syncEngine.hydrationState) { entity, _ -> entity }
          // Explicit type argument: without it, inference locks R to PrefsState.Resolved from the
          // second (more specific) emit() call below rather than widening to the sealed supertype.
          .transformLatest<StorageEntity<NotificationSettings>?, PrefsState> { entity ->
            val state = resolve(uid, scope, entity)
            emit(state)
            if (state is PrefsState.Unresolved) {
              // Racing a fresh timeout per tick, not once overall: collectLatest/transformLatest
              // cancels this delay the instant a new tick arrives (a row lands, or hydration
              // state moves), so a genuine resolution always wins the race with the timeout.
              delay(PREFS_HYDRATION_TIMEOUT)
              emit(PrefsState.Resolved(NotificationSettings()))
            }
          }
      }
    }
      .catch { e ->
        logger.w(e) { "Error observing notification settings" }
        emit(PrefsState.Resolved(NotificationSettings()))
      }

  override suspend fun update(mutate: (NotificationSettings) -> NotificationSettings): Result<Unit> =
    runCatching {
      val user = firebaseAuth.currentUser
        ?: error("Cannot update notification settings when no user is signed in")
      val uid = user.uid
      val scope = EntityScope.userRoot(uid)
      val entity = store.observe(DOC_ID, scope).first()
      val state = resolve(uid, scope, entity)
      val resolved = state as? PrefsState.Resolved
        ?: error("Cannot update notification settings while unresolved")
      store.put(DOC_ID, mutate(resolved.settings), scope)
    }
      .onFailure { logger.w(it) { "Error updating notification settings" } }
      .map { }

  /** The resolution rule (design §4.3), given the row this device currently holds (or does not). */
  private suspend fun resolve(
    uid: String,
    scope: EntityScope,
    entity: StorageEntity<NotificationSettings>?,
  ): PrefsState {
    if (entity != null) return PrefsState.Resolved(entity.value)
    // Nothing will ever hydrate here — waiting would hang forever. Same first-line guard
    // awaitHydratedSelfId uses.
    if (!cloudSyncSetting.isCloudSyncEnabled()) return PrefsState.Resolved(NotificationSettings())
    val cursor = cursorStore.get(uid, CollectionKind.NotificationSettings, scope)
    // hydrated == true with still no row means hydration finished and there genuinely is no doc —
    // the user has never set preferences. Anything else means hydration has not reached this scope
    // yet, or has not started.
    return if (cursor?.hydrated == true) PrefsState.Resolved(NotificationSettings())
    else PrefsState.Unresolved
  }

  companion object {
    private val logger = Logger.withTag("NotificationPrefsManagerImpl")
    private const val DOC_ID = "main"

    /** Backstop for hydration failing outright (SyncCursorStore backs off `failed_attempts`, so
     * "never hydrates" is a real state to survive, not an impossibility) — matches
     * TechnicianManagerImpl.SELF_ID_HYDRATION_TIMEOUT. */
    private val PREFS_HYDRATION_TIMEOUT = 5.seconds
  }
}
