package dev.fanfly.wingslog.feature.sync.data.blob.di

import dev.fanfly.wingslog.feature.attachment.datamanager.UploadScheduler
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDeleteDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDownloadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobUploadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.UrlSessionUploadScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module

actual val blobSchedulerModule = module {
  single { HttpClient(Darwin) }

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
    )
  }

  single<UrlSessionUploadScheduler> {
    UrlSessionUploadScheduler(
      blobs = get(),
      fs = get(),
      auth = get(),
      storage = get(),
      db = get(),
      syncPreferences = get(),
      httpClient = get(),
      downloadDriver = get(),
      deleteDriver = get(),
    )
  }

  single<UploadScheduler> { get<UrlSessionUploadScheduler>() }
}
