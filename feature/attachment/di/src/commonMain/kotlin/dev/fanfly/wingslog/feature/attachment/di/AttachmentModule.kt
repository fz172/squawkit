package dev.fanfly.wingslog.feature.attachment.di

import dev.fanfly.wingslog.feature.attachment.datamanager.attachmentDataManagerModule
import dev.fanfly.wingslog.feature.attachment.datamanager.platformAttachmentModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the attachment feature (attachments) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val attachmentModule: Module = module {
  includes(
    attachmentDataManagerModule,
    // BlobFilesystem / FileByteReader / ImageCompressor per platform.
    platformAttachmentModule,
  )
}
