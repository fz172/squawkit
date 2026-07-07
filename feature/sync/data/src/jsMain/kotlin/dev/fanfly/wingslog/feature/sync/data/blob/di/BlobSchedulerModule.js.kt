package dev.fanfly.wingslog.feature.sync.data.blob.di

import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDeleteDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDownloadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobUploadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.ForegroundWebBlobScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Web blob transfer wiring. The foreground scheduler runs uploads/downloads/deletes for the
 * lifetime of the open tab; on reload `SyncEngine.schedulePendingBlobs` re-queues anything still
 * pending. REMOTE_ONLY rows are downloaded lazily on open (see
 * [ForegroundWebBlobScheduler.prefetchRemoteOnly]).
 */
actual val blobSchedulerModule: Module = module {
  single { HttpClient(Js) }

  single {
    BlobUploadDriver(
      blobs = get(),
      storage = get(),
      auth = get(),
      fs = get(),
    )
  }

  single {
    BlobDownloadDriver(
      blobs = get(),
      storage = get(),
      httpClient = get(),
    )
  }

  single {
    BlobDeleteDriver(
      blobs = get(),
      storage = get(),
      db = get(),
      writeLock = get<DatabaseWriteLock>(),
    )
  }

  single<UploadScheduler> {
    ForegroundWebBlobScheduler(
      uploadDriver = get(),
      downloadDriver = get(),
      deleteDriver = get(),
    )
  }
}
