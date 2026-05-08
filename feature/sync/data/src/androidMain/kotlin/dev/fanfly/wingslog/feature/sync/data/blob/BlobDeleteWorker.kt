package dev.fanfly.wingslog.feature.sync.data.blob

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.fanfly.wingslog.core.storage.blob.BlobId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BlobDeleteWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

  private val driver: BlobDeleteDriver by inject()

  override suspend fun doWork(): Result {
    val id = inputData.getString(KEY_BLOB_ID) ?: return Result.failure()
    val success = driver.runOnce(BlobId(id))
    return if (success) Result.success() else Result.retry()
  }

  companion object {
    const val KEY_BLOB_ID = "blob_id"
  }
}
