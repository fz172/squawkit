package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.PostWriteHook
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.SqlDelightLocalBlobStore
import dev.fanfly.wingslog.feature.attachment.datamanager.impl.LocalFirstAttachmentManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common Koin bindings for the local-first attachment stack. [BlobFilesystem] is provided per
 * platform in [platformAttachmentModule] alongside the [FileByteReader] actual.
 */
val attachmentModule = module {
  single<LocalBlobStore> {
    SqlDelightLocalBlobStore(
      db = get(),
      fs = get(),
      ioContext = Dispatchers.Default,
      writeLock = get<DatabaseWriteLock>(),
    )
  }
  single<AttachmentManager> {
    LocalFirstAttachmentManagerImpl(
      blobs = get(),
      auth = get(),
      fileByteReader = get(),
      uploadScheduler = getOrNull(),
    )
  }
  single {
    QuotaChecker(
      db = get(),
      ioContext = Dispatchers.Default,
    )
  }
  single<PostWriteHook> {
    BlobIndexReconciler(
      blobs = get(),
      coroutineScope = CoroutineScope(SupervisorJob()),
      uploadScheduler = getOrNull(),
    )
  }
}

expect val platformAttachmentModule: Module
