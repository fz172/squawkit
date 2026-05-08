package dev.fanfly.wingslog.feature.sync.data.blob

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.feature.attachment.datamanager.UploadScheduler
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences

/**
 * Android [UploadScheduler] backed by WorkManager. Each blob gets its own uniquely-keyed
 * `OneTimeWorkRequest` with `KEEP` policy, so duplicate schedules are no-ops. WorkManager
 * handles retry, process death, and reboot.
 *
 * Upload constraints respect `SyncPreferences.allowUploadOnCellular`: when false (default) only
 * UNMETERED (Wi-Fi) networks are used; when true any CONNECTED network is allowed.
 */
class WorkManagerUploadScheduler(
  private val context: Context,
  private val syncPreferences: SyncPreferences,
) : UploadScheduler {

  private val wm get() = WorkManager.getInstance(context)

  private fun uploadNetworkType(): NetworkType =
    if (syncPreferences.state.value.allowUploadOnCellular) NetworkType.CONNECTED
    else NetworkType.UNMETERED

  override fun scheduleUpload(blobId: BlobId) {
    val request = OneTimeWorkRequestBuilder<BlobUploadWorker>()
      .setInputData(workDataOf(BlobUploadWorker.KEY_BLOB_ID to blobId.value))
      .setConstraints(Constraints(requiredNetworkType = uploadNetworkType()))
      .addTag(TAG_BLOB)
      .build()
    wm.enqueueUniqueWork("upload:${blobId.value}", ExistingWorkPolicy.KEEP, request)
  }

  override fun scheduleDownload(blobId: BlobId) {
    val request = OneTimeWorkRequestBuilder<BlobDownloadWorker>()
      .setInputData(workDataOf(BlobDownloadWorker.KEY_BLOB_ID to blobId.value))
      .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
      .addTag(TAG_BLOB)
      .build()
    wm.enqueueUniqueWork("download:${blobId.value}", ExistingWorkPolicy.KEEP, request)
  }

  override fun scheduleDelete(blobId: BlobId) {
    val request = OneTimeWorkRequestBuilder<BlobDeleteWorker>()
      .setInputData(workDataOf(BlobDeleteWorker.KEY_BLOB_ID to blobId.value))
      .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
      .addTag(TAG_BLOB)
      .build()
    wm.enqueueUniqueWork("delete:${blobId.value}", ExistingWorkPolicy.KEEP, request)
  }

  override fun cancelAll() {
    wm.cancelAllWorkByTag(TAG_BLOB)
  }

  companion object {
    const val TAG_BLOB = "wingslog_blob"
  }
}
