package dev.fanfly.wingslog.feature.developeroptions.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.settings.DeveloperSettings
import dev.fanfly.wingslog.core.model.settings.Subscription
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

internal fun DeveloperSettings.toDeveloperFlags() = DeveloperFlags(
  forceSubscriptionStatus = force_subscription_status.toSubscriptionStatusOrNull(),
)

internal fun DeveloperFlags.toProto() = DeveloperSettings(
  force_subscription_status = forceSubscriptionStatus.toForceProto(),
)

private fun DeveloperSettings.ForceSubscriptionStatus.toSubscriptionStatusOrNull(): Subscription.Status? =
  when (this) {
    DeveloperSettings.ForceSubscriptionStatus.FORCE_SUBSCRIPTION_STATUS_FREE -> Subscription.Status.STATUS_FREE
    DeveloperSettings.ForceSubscriptionStatus.FORCE_SUBSCRIPTION_STATUS_PRO -> Subscription.Status.STATUS_PRO
    DeveloperSettings.ForceSubscriptionStatus.FORCE_SUBSCRIPTION_STATUS_UNSET -> null
  }

private fun Subscription.Status?.toForceProto(): DeveloperSettings.ForceSubscriptionStatus =
  when (this) {
    Subscription.Status.STATUS_FREE -> DeveloperSettings.ForceSubscriptionStatus.FORCE_SUBSCRIPTION_STATUS_FREE
    Subscription.Status.STATUS_PRO -> DeveloperSettings.ForceSubscriptionStatus.FORCE_SUBSCRIPTION_STATUS_PRO
    null -> DeveloperSettings.ForceSubscriptionStatus.FORCE_SUBSCRIPTION_STATUS_UNSET
  }
