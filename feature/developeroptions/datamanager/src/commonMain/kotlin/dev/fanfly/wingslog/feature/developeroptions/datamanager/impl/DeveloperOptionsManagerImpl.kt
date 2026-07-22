package dev.fanfly.wingslog.feature.developeroptions.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.settings.DeveloperSettings
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DeveloperOptionsManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
) : DeveloperOptionsManager {

  private val store: EntityStore<DeveloperSettings> =
    storeFactory.create(CollectionKind.DeveloperOptions)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observe(): Flow<DeveloperFlags> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        flowOf(DeveloperFlags())
      } else {
        store.observe(DOC_ID, EntityScope.userRoot(user.uid))
          .map { it?.value?.toDeveloperFlags() ?: DeveloperFlags() }
          .catch { e ->
            logger.w(e) { "Error observing feature lab settings" }
            emit(DeveloperFlags())
          }
      }
    }

  override suspend fun update(flags: DeveloperFlags): Result<Unit> = runCatching {
    val uid = firebaseAuth.currentUser?.uid
      ?: error("Cannot update feature lab settings when no user is signed in")
    store.put(DOC_ID, flags.toProto(), EntityScope.userRoot(uid))
  }.onFailure { logger.w(it) { "Error updating feature lab settings" } }
    .map { }

  companion object {
    private val logger = Logger.withTag("DeveloperOptionsManagerImpl")
    private const val DOC_ID = "main"
  }
}

private fun DeveloperSettings.toDeveloperFlags() = DeveloperFlags(
  attachmentUploadEnabled = attachment_upload_enabled,
  exportEmailDeliveryEnabled = export_email_delivery_enabled,
)

private fun DeveloperFlags.toProto() = DeveloperSettings(
  attachment_upload_enabled = attachmentUploadEnabled,
  export_email_delivery_enabled = exportEmailDeliveryEnabled,
)
