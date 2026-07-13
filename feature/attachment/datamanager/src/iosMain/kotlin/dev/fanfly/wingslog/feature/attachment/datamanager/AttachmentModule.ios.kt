package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import org.koin.core.module.Module
import org.koin.dsl.module
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager

actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { FileByteReaderImpl() }
  single<BlobFilesystem> { NsBlobFilesystem() }
  single<AttachmentOpener> {
    AttachmentOpenerIos(
      blobs = get<LocalBlobStore>(),
      attachmentManager = get<AttachmentManager>(),
      fs = get<BlobFilesystem>(),
    )
  }
}
