package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { FileByteReaderImpl(androidContext()) }
  single<ImageCompressor> { ImageCompressorImpl() }
  single<BlobFilesystem> { FileBlobFilesystem(androidContext().filesDir) }
  single<AttachmentOpener> {
    AttachmentOpenerAndroid(
      context = androidContext(),
      blobs = get<LocalBlobStore>(),
      attachmentManager = get<AttachmentManager>(),
    )
  }
}
