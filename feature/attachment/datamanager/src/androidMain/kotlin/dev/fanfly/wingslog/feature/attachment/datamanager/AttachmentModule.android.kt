package dev.fanfly.wingslog.feature.attachment.datamanager

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { FileByteReaderImpl(androidContext()) }
  single<BlobFilesystem> { FileBlobFilesystem(androidContext().filesDir) }
}
