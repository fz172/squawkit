package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { FileByteReaderImpl() }
  single<ImageCompressor> { ImageCompressorImpl() }
  single<BlobFilesystem> { NsBlobFilesystem() }
  single<AttachmentOpener> {
    AttachmentOpenerIos(
      blobs = get<LocalBlobStore>(),
      attachmentManager = get<AttachmentManager>(),
      fs = get<BlobFilesystem>(),
    )
  }
}
