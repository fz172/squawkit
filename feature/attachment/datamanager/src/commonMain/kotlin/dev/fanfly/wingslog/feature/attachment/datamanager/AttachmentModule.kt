package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.feature.attachment.datamanager.impl.LocalFirstAttachmentManagerImpl
import dev.fanfly.wingslog.feature.attachment.datamanager.impl.SqlDelightLocalBlobStore
import kotlinx.coroutines.Dispatchers
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
      ioContext = Dispatchers.IO,
    )
  }
  single<AttachmentManager> {
    LocalFirstAttachmentManagerImpl(
      blobs = get(),
      auth = get(),
      fileByteReader = get(),
    )
  }
  single {
    QuotaChecker(
      db = get(),
      ioContext = Dispatchers.IO,
    )
  }
}

expect val platformAttachmentModule: Module
