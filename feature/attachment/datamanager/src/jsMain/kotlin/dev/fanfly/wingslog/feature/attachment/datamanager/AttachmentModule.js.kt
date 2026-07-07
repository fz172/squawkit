package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { WebFileByteReader() }
  single<BlobFilesystem> { OpfsBlobFilesystem() }
  single<AttachmentOpener> {
    AttachmentOpenerWeb(
      get<LocalBlobStore>(),
      get<AttachmentManager>(),
      get<BlobFilesystem>(),
    )
  }
}
