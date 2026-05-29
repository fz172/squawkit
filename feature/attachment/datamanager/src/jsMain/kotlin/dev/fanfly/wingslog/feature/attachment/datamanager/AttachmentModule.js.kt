package dev.fanfly.wingslog.feature.attachment.datamanager

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Attachments are outside the current web scope. Read-only pages can still depend on their
 * view models; these bindings expose empty state and reject any attempted blob interaction.
 */
actual val platformAttachmentModule: Module = module {
  single<FileByteReader> { WebFileByteReader() }
  single<BlobFilesystem> { OpfsBlobFilesystem() }
  single<AttachmentManager> { DisabledWebAttachmentManager() }
  single<AttachmentOpener> { DisabledWebAttachmentOpener() }
}
