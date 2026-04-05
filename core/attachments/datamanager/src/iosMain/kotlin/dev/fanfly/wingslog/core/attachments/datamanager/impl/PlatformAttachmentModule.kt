package dev.fanfly.wingslog.core.attachments.datamanager.impl

import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentOpener
import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentOpenerIos
import dev.fanfly.wingslog.core.attachments.datamanager.FileByteReader
import dev.fanfly.wingslog.core.attachments.datamanager.FileByteReaderImpl
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { FileByteReaderImpl() }
  single<AttachmentOpener> { AttachmentOpenerIos() }
}
