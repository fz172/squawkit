package dev.fanfly.wingslog.feature.sync.data.blob.di

import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.fanfly.wingslog.feature.sync.data.blob.AndroidAppCheckTokenProvider
import dev.fanfly.wingslog.feature.sync.data.blob.AppCheckTokenProvider
import dev.fanfly.wingslog.feature.sync.data.blob.AttachmentBroker
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDeleteDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDownloadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobUploadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.HttpsAttachmentBroker
import dev.fanfly.wingslog.feature.sync.data.blob.WorkManagerUploadScheduler
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.storage.FirebaseStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val blobSchedulerModule = module {
  single { HttpClient(OkHttp) }

  single<AppCheckTokenProvider> { AndroidAppCheckTokenProvider() }

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

  single<UploadScheduler> {
    WorkManagerUploadScheduler(
      context = androidContext(),
      syncPreferences = get<SyncPreferences>()
    )
  }
}
