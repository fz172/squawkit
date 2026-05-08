package dev.fanfly.wingslog.feature.sync.data.blob.di

import dev.fanfly.wingslog.feature.attachment.datamanager.UploadScheduler
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDeleteDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobDownloadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.BlobUploadDriver
import dev.fanfly.wingslog.feature.sync.data.blob.WorkManagerUploadScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val blobSchedulerModule = module {
  single { HttpClient(OkHttp) }

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

  single<UploadScheduler> {
    WorkManagerUploadScheduler(context = androidContext(), syncPreferences = get())
  }
}
