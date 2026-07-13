package dev.fanfly.wingslog.feature.sync.data.blob.di

import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDeleteDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDownloadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobUploadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.ForegroundWebBlobScheduler
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.storage.FirebaseStorage
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
      blobs = get<LocalBlobStore>(),
      storage = get<FirebaseStorage>(),
      auth = get<FirebaseAuth>(),
      fs = get<BlobFilesystem>(),
    )
  }

  single {
    BlobDownloadDriver(
      blobs = get<LocalBlobStore>(),
      storage = get<FirebaseStorage>(),
      httpClient = get<HttpClient>(),
    )
  }

  single {
    BlobDeleteDriver(
      blobs = get<LocalBlobStore>(),
      storage = get<FirebaseStorage>(),
      db = get<WingsLogDatabase>(),
      writeLock = get<DatabaseWriteLock>(),
    )
  }

  single<UploadScheduler> {
    ForegroundWebBlobScheduler(
      uploadDriver = get<BlobUploadDriver>(),
      downloadDriver = get<BlobDownloadDriver>(),
      deleteDriver = get<BlobDeleteDriver>(),
    )
  }
}
