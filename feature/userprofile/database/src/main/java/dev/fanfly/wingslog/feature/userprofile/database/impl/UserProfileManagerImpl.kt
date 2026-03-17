package dev.fanfly.wingslog.feature.userprofile.database.impl

import com.google.common.flogger.FluentLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.protobuf.InvalidProtocolBufferException
import dev.fanfly.wingslog.core.database.common.getUserDocumentRef
import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.newUserLicenseProfile
import dev.fanfly.wingslog.feature.userprofile.database.UserProfileManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject


class UserProfileManagerImpl @Inject constructor(
  private val firebaseAuth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
) : UserProfileManager {

  override fun observeLicenseInfo(): Flow<LicenseInfo?> = callbackFlow {
    val docRef = getProfileDocumentRef()
    if (docRef == null) {
      trySend(null)
      close(Exception("User not logged in."))
      return@callbackFlow
    }

    val listener = docRef.addSnapshotListener { snapshot, e ->
      if (e != null) {
        logger.atWarning().withCause(e).log("Listen failed.")
        close(e)
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
      trySend(licenseInfo)
    }

    awaitClose { listener.remove() }
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

  private fun getProfileDocumentRef(): DocumentReference? =
    firestore.getUserDocumentRef(firebaseAuth)?.collection(PROFILE_COLLECTION)
      ?.document(LICENSE_INFO_DOCUMENT)


  companion object {
    private val logger: FluentLogger = FluentLogger.forEnclosingClass()

    private const val PROFILE_COLLECTION = "profile"
    private const val LICENSE_INFO_DOCUMENT = "license_info"
    private const val LICENSE_INFO_BLOB = "license_info_blob"
  }
}