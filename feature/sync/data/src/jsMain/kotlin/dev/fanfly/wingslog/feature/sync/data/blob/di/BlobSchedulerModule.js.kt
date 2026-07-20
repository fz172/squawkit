package dev.fanfly.wingslog.feature.sync.data.blob.di

import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sync.data.blob.AppCheckTokenProvider
import dev.fanfly.wingslog.feature.sync.data.blob.AttachmentBroker
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDeleteDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDownloadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobUploadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.ForegroundWebBlobScheduler
import dev.fanfly.wingslog.feature.sync.data.blob.HttpsAttachmentBroker
import dev.fanfly.wingslog.feature.sync.data.blob.WebAppCheckTokenProvider
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
 * [UploadScheduler.prefetchRemoteOnly]).
 */
actual val blobSchedulerModule: Module = module {
  single { HttpClient(Js) }

  single<AppCheckTokenProvider> { WebAppCheckTokenProvider() }

  single<AttachmentBroker> {
    HttpsAttachmentBroker(
      auth = get<FirebaseAuth>(),
      httpClient = get<HttpClient>(),
      appCheck = get<AppCheckTokenProvider>(),
    )
  }

  single {
    BlobUploadDriver(
      blobs = get<LocalBlobStore>(),
      storage = get<FirebaseStorage>(),
      auth = get<FirebaseAuth>(),
      fs = get<BlobFilesystem>(),
      broker = get<AttachmentBroker>(),
    )
  }

  single {
    BlobDownloadDriver(
      blobs = get<LocalBlobStore>(),
      storage = get<FirebaseStorage>(),
      httpClient = get<HttpClient>(),
      auth = get<FirebaseAuth>(),
      broker = get<AttachmentBroker>(),
    )
  }

  single {
    BlobDeleteDriver(
      blobs = get<LocalBlobStore>(),
      storage = get<FirebaseStorage>(),
      db = get<WingsLogDatabase>(),
      auth = get<FirebaseAuth>(),
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
