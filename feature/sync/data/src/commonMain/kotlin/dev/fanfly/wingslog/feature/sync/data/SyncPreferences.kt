package dev.fanfly.wingslog.feature.sync.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext

/**
 * R1 sync user preferences. One flag, surfaced via the dedicated sync settings page:
 *
 * - [cloudSyncEnabled] — master toggle. When false, [SyncEngine.start] stays idle even if a
 *   non-anonymous user is signed in.
 *
 * Persisted in SQLDelight via the `sync_config` table, scoped by user ID.
 */
data class SyncPrefs(
  val cloudSyncEnabled: Boolean = true,
  val allowUploadOnCellular: Boolean = false,
)

class SyncPreferences(
  private val db: WingsLogDatabase,
  private val auth: FirebaseAuth,
  private val ioContext: CoroutineContext = syncIoContext,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioContext),
) : CloudSyncSetting {

  override fun isCloudSyncEnabled(): Boolean = state.value.cloudSyncEnabled

  @OptIn(ExperimentalCoroutinesApi::class)
  val state: StateFlow<SyncPrefs> = auth.authStateChanged
    .flatMapLatest { user ->
      val uid = user?.uid ?: return@flatMapLatest flowOf(SyncPrefs())
      combine(
        booleanConfig(uid, KEY_CLOUD_SYNC_ENABLED, defaultValue = true),
        booleanConfig(uid, KEY_ALLOW_UPLOAD_ON_CELLULAR, defaultValue = false),
      ) { cloudSync, cellular ->
        SyncPrefs(
          cloudSyncEnabled = cloudSync,
          allowUploadOnCellular = cellular,
        )
      }
    }
    .stateIn(
      scope = scope,
      started = SharingStarted.Eagerly,
      initialValue = SyncPrefs()
    )

  suspend fun setCloudSyncEnabled(enabled: Boolean) {
    val uid = auth.currentUser?.uid ?: return
    writeLock.withLock {
      db.schemaQueries.upsertConfig(
        uid,
        KEY_CLOUD_SYNC_ENABLED,
        enabled.toString()
      )
    }
  }

  suspend fun setAllowUploadOnCellular(allowed: Boolean) {
    val uid = auth.currentUser?.uid ?: return
    writeLock.withLock {
      db.schemaQueries.upsertConfig(
        uid,
        KEY_ALLOW_UPLOAD_ON_CELLULAR,
        allowed.toString()
      )
    }
  }

  private fun booleanConfig(
    uid: String,
    key: String,
    defaultValue: Boolean,
  ): Flow<Boolean> = db.schemaQueries.selectConfig(uid, key)
    .asFlow()
    .mapToOneOrNull(ioContext)
    .map { value -> value?.toBoolean() ?: defaultValue }

  companion object {
    private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    private const val KEY_ALLOW_UPLOAD_ON_CELLULAR = "allow_upload_on_cellular"
  }
}
