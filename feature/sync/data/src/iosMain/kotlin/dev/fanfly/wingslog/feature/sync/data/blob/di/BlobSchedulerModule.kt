package dev.fanfly.wingslog.feature.sync.data.blob.di

import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDeleteDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDownloadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobUploadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.UrlSessionUploadScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem

actual val blobSchedulerModule = module {
  single { HttpClient(Darwin) }

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
