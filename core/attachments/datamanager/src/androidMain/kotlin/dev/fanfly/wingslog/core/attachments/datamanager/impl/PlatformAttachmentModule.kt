package dev.fanfly.wingslog.core.attachments.datamanager.impl

import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentOpener
import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentOpenerAndroid
import dev.fanfly.wingslog.core.attachments.datamanager.FileByteReader
import dev.fanfly.wingslog.core.attachments.datamanager.FileByteReaderImpl
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { FileByteReaderImpl(androidContext()) }
  single<AttachmentOpener> { AttachmentOpenerAndroid(androidContext()) }
}
