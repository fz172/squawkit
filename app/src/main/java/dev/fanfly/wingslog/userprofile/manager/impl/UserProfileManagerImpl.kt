package dev.fanfly.wingslog.userprofile.manager.impl

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
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

  override suspend fun loadLicenseInfo(): Result<LicenseInfo> {
    return try {
      val docRef = getProfileDocumentRef() // <-- UPDATED
        ?: return Result.failure(Exception("User not logged in."))

      val document = docRef.get().await()

      // Get the blob from the document
      val blob = document.getBlob(LICENSE_INFO_BLOB)

      val licenseInfo = if (blob != null) {
        // If blob exists, parse it
        try {
          LicenseInfo.parseFrom(blob.toBytes())
        } catch (e: InvalidProtocolBufferException) {
          Log.e(TAG, "Failed to parse LicenseInfo proto", e)
          // Data is corrupt, return default
          newUserLicenseProfile()
        }
      } else {
        // No blob found (e.g., new user), return default
        Log.d(TAG, "No licenseInfoBlob found, returning default instance.")
        newUserLicenseProfile()
      }

      Log.d(TAG, "Profile loaded successfully.")
      Result.success(licenseInfo)

    } catch (e: Exception) {
      Log.e(TAG, "Error loading profile", e)
      Result.failure(e)
    }
  }

  override suspend fun updateLicenseInfo(licenseInfo: LicenseInfo): Result<Boolean> {
    return try {
      val docRef = getProfileDocumentRef() // <-- UPDATED
        ?: return Result.failure(Exception("User not logged in."))

      // Create a map to set the blob field
      val data = mapOf(LICENSE_INFO_BLOB to Blob.fromBytes(licenseInfo.toByteArray()))

      // Use SetOptions.merge() to only update this field
      docRef.set(data, SetOptions.merge()).await()

      Log.d(TAG, "Profile updated successfully.")
      Result.success(true)

    } catch (e: Exception) {
      Log.e(TAG, "Error updating profile", e)
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
    private const val TAG = "UserProfileManagerImpl"
    private const val USERS_COLLECTION = "users"
    private const val PROFILE_COLLECTION = "profile"
    private const val LICENSE_INFO_DOCUMENT = "license_info"
    private const val LICENSE_INFO_BLOB = "license_info_blob"
  }
}