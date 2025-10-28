package dev.fanfly.wingslog.userprofile.manager.impl

import com.google.common.flogger.FluentLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.protobuf.InvalidProtocolBufferException
import dev.fanfly.wingslog.userprofile.data.LicenseInfo
import dev.fanfly.wingslog.userprofile.data.newUserLicenseProfile
import dev.fanfly.wingslog.userprofile.manager.UserProfileManager
import kotlinx.coroutines.tasks.await
import javax.inject.Inject


class UserProfileManagerImpl @Inject constructor(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : UserProfileManager {

  override fun observeLicenseInfo(licenseInfoListener: (LicenseInfo) -> Unit): ListenerRegistration? {
    val docRef = getProfileDocumentRef()
    if (docRef == null) {
      return null
    }
    val listener = docRef.addSnapshotListener { snapshot, e ->
      if (e != null) {
        logger.atWarning().withCause(e).log("Listen failed.")
        return@addSnapshotListener
      }
      val licenseInfo = if (snapshot != null && snapshot.exists()) {
        // Get the blob from the snapshot
        val blob = snapshot.getBlob(LICENSE_INFO_BLOB)
        if (blob != null) {
          // If blob exists, parse it
          try {
            LicenseInfo.parseFrom(blob.toBytes())
          } catch (e: InvalidProtocolBufferException) {
            logger.atWarning().withCause(e).log("Failed to parse LicenseInfo proto")
            // Data is corrupt, return default
            newUserLicenseProfile()
          }
        } else {
          // No blob found (e.g., new user), return default
          logger.atFine().log("No licenseInfoBlob found, returning default instance.")
          newUserLicenseProfile()
        }
      } else {
        // Document itself doesn't exist (e.g., new user)
        logger.atFine().log("Profile document does not exist, returning default.")
        newUserLicenseProfile()
      }
      licenseInfoListener.invoke(licenseInfo)
    }

    return listener
  }

  override suspend fun updateLicenseInfo(licenseInfo: LicenseInfo): Result<Boolean> {
    return try {
      val docRef = getProfileDocumentRef() // <-- UPDATED
        ?: return Result.failure(Exception("User not logged in."))

      // Create a map to set the blob field
      val data = mapOf(LICENSE_INFO_BLOB to Blob.fromBytes(licenseInfo.toByteArray()))

      // Use SetOptions.merge() to only update this field
      docRef.set(data, SetOptions.merge()).await()

      logger.atFine().log("Profile updated successfully.")
      Result.success(true)

    } catch (e: Exception) {
      logger.atWarning().withCause(e).log("Error updating profile")

      Result.failure(e)
    }
  }


  private fun getUserDocumentRef(): DocumentReference? {
    val userId = firebaseAuth.currentUser?.uid ?: return null
    return firestore.collection(USERS_COLLECTION).document(userId)
  }

  private fun getProfileDocumentRef(): DocumentReference? =
    getUserDocumentRef()?.collection(PROFILE_COLLECTION)?.document(LICENSE_INFO_DOCUMENT)


  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()

    private const val USERS_COLLECTION = "users"
    private const val PROFILE_COLLECTION = "profile"
    private const val LICENSE_INFO_DOCUMENT = "license_info"
    private const val LICENSE_INFO_BLOB = "license_info_blob"
  }
}