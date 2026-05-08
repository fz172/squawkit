package dev.fanfly.wingslog.feature.attachment.datamanager

import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { FileByteReaderImpl() }
  single<BlobFilesystem> { NsBlobFilesystem() }
  single<AttachmentOpener> {
    AttachmentOpenerIos(
      blobs = get(),
      attachmentManager = get(),
      fs = get(),
    )
  }
}
