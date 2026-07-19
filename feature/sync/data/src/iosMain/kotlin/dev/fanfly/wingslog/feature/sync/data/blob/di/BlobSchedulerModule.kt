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
import dev.fanfly.wingslog.feature.sync.data.blob.HttpsAttachmentBroker
import dev.fanfly.wingslog.feature.sync.data.blob.UrlSessionUploadScheduler
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.storage.FirebaseStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module

actual val blobSchedulerModule = module {
  single { HttpClient(Darwin) }

  // iOS App Check token access is a fast-follow (P8.4 #245): until it lands, brokered DOWNLOADS of a
  // foreign-hosted blob fail-and-retry. Brokered UPLOADS work — the getBlobUploadSession callable
  // attaches App Check via the native SDK. Own-tree blobs are unaffected.
  single<AppCheckTokenProvider> { AppCheckTokenProvider.Unavailable }

  single<AttachmentBroker> {
    HttpsAttachmentBroker(
      auth = get<FirebaseAuth>(),
      httpClient = get<HttpClient>(),
      appCheck = get<AppCheckTokenProvider>(),
      functionsBaseUrl = HttpsAttachmentBroker.functionsBaseUrl(),
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

  single<UrlSessionUploadScheduler> {
    UrlSessionUploadScheduler(
      blobs = get<LocalBlobStore>(),
      fs = get<BlobFilesystem>(),
      auth = get<FirebaseAuth>(),
      storage = get<FirebaseStorage>(),
      db = get<WingsLogDatabase>(),
      httpClient = get<HttpClient>(),
      downloadDriver = get<BlobDownloadDriver>(),
      deleteDriver = get<BlobDeleteDriver>(),
      writeLock = get<DatabaseWriteLock>(),
    )
  }

  single<UploadScheduler> { get<UrlSessionUploadScheduler>() }
}
