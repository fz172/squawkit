package dev.fanfly.wingslog.feature.featurelab.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.settings.FeatureLabSettings
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureFlags
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FeatureLabManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
) : FeatureLabManager {

  private val store: EntityStore<FeatureLabSettings> = storeFactory.create(CollectionKind.FeatureLab)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observe(): Flow<FeatureFlags> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        flowOf(FeatureFlags())
      } else {
        store.observe(DOC_ID, EntityScope.userRoot(user.uid))
          .map { it?.value?.toFeatureFlags() ?: FeatureFlags() }
          .catch { e ->
            logger.w(e) { "Error observing feature lab settings" }
            emit(FeatureFlags())
          }
      }
    }

  override suspend fun update(flags: FeatureFlags): Result<Unit> = runCatching {
    val uid = firebaseAuth.currentUser?.uid
      ?: error("Cannot update feature lab settings when no user is signed in")
    store.put(DOC_ID, flags.toProto(), EntityScope.userRoot(uid))
  }.onFailure { logger.w(it) { "Error updating feature lab settings" } }.map { }

  companion object {
    private val logger = Logger.withTag("FeatureLabManagerImpl")
    private const val DOC_ID = "main"
  }
}

private fun FeatureLabSettings.toFeatureFlags() = FeatureFlags(
  technicianEnabled = !technician_disabled,
  attachmentUploadEnabled = !attachment_upload_disabled,
)

private fun FeatureFlags.toProto() = FeatureLabSettings(
  technician_disabled = !technicianEnabled,
  attachment_upload_disabled = !attachmentUploadEnabled,
)
