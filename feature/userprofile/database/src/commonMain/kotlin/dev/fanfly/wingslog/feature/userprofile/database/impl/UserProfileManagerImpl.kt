package dev.fanfly.wingslog.feature.userprofile.database.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.userprofile.database.UserProfileManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class UserProfileManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  storeFactory: EntityStoreFactory,
) : UserProfileManager {

  private val store: EntityStore<LicenseInfo> = storeFactory.create(CollectionKind.LicenseInfo)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observeLicenseInfo(): Flow<LicenseInfo?> =
    firebaseAuth.authStateChanged.flatMapLatest { user ->
      if (user == null) {
        logger.d { "User logged out, stopping license info observation" }
        flowOf(null)
      } else {
        store.observe(LICENSE_INFO_ID, EntityScope.userRoot(user.uid))
          .map<_, LicenseInfo?> { it?.value ?: newUserLicenseProfile() }
          .catch { e ->
            logger.w(e) { "Error observing license info" }
            emit(null)
          }
      }
    }

  override suspend fun updateLicenseInfo(licenseInfo: LicenseInfo): Result<Boolean> = runCatching {
    val uid = firebaseAuth.currentUser?.uid
      ?: error("Cannot update license info when no user is signed in")
    store.put(LICENSE_INFO_ID, licenseInfo, EntityScope.userRoot(uid))
    true
  }.onFailure { logger.w(it) { "Error updating license info" } }

  companion object {
    private val logger = Logger.withTag("UserProfileManagerImpl")
    // The license-info doc is the only LicenseInfo per user; a fixed id keeps the row well-defined.
    private const val LICENSE_INFO_ID = "main"
  }
}
