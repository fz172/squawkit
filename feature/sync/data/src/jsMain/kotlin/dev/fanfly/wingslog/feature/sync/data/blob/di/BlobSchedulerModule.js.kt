package dev.fanfly.wingslog.feature.sync.data.blob.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Blob transfer is deferred on web. No UploadScheduler binding means SyncEngine does not scan or
 * enqueue attachment work while entity sync remains available.
 */
actual val blobSchedulerModule: Module = module {}
