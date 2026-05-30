package dev.fanfly.wingslog.feature.attachment.datamanager

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
