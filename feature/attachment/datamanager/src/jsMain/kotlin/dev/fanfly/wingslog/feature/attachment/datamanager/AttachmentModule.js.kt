package dev.fanfly.wingslog.feature.attachment.datamanager

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Attachments are outside the current web scope. Keep the dependency JS-capable without
 * registering browser filesystem, picker, or opener implementations.
 */
actual val platformAttachmentModule: Module = module {}
