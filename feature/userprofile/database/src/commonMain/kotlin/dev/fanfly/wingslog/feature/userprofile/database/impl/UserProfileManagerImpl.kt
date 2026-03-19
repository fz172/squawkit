package dev.fanfly.wingslog.feature.userprofile.database.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.database.getBlobAsBytes
import dev.fanfly.wingslog.core.database.getGitLiveUserDocumentRef
import dev.fanfly.wingslog.core.database.setEncoded
import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile
import dev.fanfly.wingslog.feature.userprofile.database.UserProfileManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserProfileManagerImpl(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : UserProfileManager {

  override fun observeLicenseInfo(): Flow<LicenseInfo?> {
    val docRef = getProfileDocumentRef()
      ?: return kotlinx.coroutines.flow.flowOf(null)

    return docRef.snapshots.map { snapshot ->
      if (snapshot.exists) {
        val blobBytes = snapshot.getBlobAsBytes(LICENSE_INFO_BLOB)
        if (blobBytes != null) {
          try {
            LicenseInfo.ADAPTER.decode(blobBytes)
          } catch (e: Exception) {
            logger.w(e) { "Failed to parse LicenseInfo proto" }
            newUserLicenseProfile()
          }
        } else {
          logger.d { "No licenseInfoBlob found, returning default instance." }
          newUserLicenseProfile()
        }
      } else {
        logger.d { "Profile document does not exist, returning default." }
        newUserLicenseProfile()
      }
    }
  }

  override suspend fun updateLicenseInfo(licenseInfo: LicenseInfo): Result<Boolean> {
    return try {
      val docRef = getProfileDocumentRef()
        ?: return Result.failure(Exception("User not logged in."))

      val data = mapOf(LICENSE_INFO_BLOB to LicenseInfo.ADAPTER.encode(licenseInfo))

      docRef.setEncoded(data, merge = true)

      logger.d { "Profile updated successfully." }
      Result.success(true)

    } catch (e: Exception) {
      logger.w(e) { "Error updating profile" }
      Result.failure(e)
    }
  }

  private fun getProfileDocumentRef(): DocumentReference? =
    firestore.getGitLiveUserDocumentRef(firebaseAuth)?.collection(PROFILE_COLLECTION)
      ?.document(LICENSE_INFO_DOCUMENT)

  companion object {
    private val logger = Logger.withTag("UserProfileManagerImpl")

    private const val PROFILE_COLLECTION = "profile"
    private const val LICENSE_INFO_DOCUMENT = "license_info"
    private const val LICENSE_INFO_BLOB = "license_info_blob"
  }
}
