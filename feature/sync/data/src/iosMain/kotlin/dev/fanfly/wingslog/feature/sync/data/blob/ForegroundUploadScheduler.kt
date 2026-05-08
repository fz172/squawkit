package dev.fanfly.wingslog.feature.sync.data.blob

import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.feature.attachment.datamanager.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * iOS [UploadScheduler] that runs blob jobs as foreground coroutines. Each job launches
 * immediately in a shared [CoroutineScope] backed by [Dispatchers.Default]. The scope is
 * cancelled (and recreated) on [cancelAll], which models sign-out correctly.
 *
 * This is the R2 v1 implementation. M7 upgrades this to a URLSession background scheduler.
 */
class ForegroundUploadScheduler(
  private val uploadDriver: BlobUploadDriver,
  private val downloadDriver: BlobDownloadDriver,
  private val deleteDriver: BlobDeleteDriver,
) : UploadScheduler {

  private var scope = newScope()

  override fun scheduleUpload(blobId: BlobId) {
    scope.launch { uploadDriver.runOnce(blobId) }
  }

  override fun scheduleDownload(blobId: BlobId) {
    scope.launch { downloadDriver.runOnce(blobId) }
  }

  override fun scheduleDelete(blobId: BlobId) {
    scope.launch { deleteDriver.runOnce(blobId) }
  }

  override fun cancelAll() {
    scope.cancel()
    scope = newScope()
  }

  private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
