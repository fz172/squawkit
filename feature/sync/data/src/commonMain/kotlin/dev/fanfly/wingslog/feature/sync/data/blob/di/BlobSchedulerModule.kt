package dev.fanfly.wingslog.feature.sync.data.blob.di

import org.koin.core.module.Module

/**
 * Platform-specific Koin module that provides [dev.fanfly.wingslog.core.storage.blob.UploadScheduler].
 * Android: [WorkManagerUploadScheduler]; iOS: [ForegroundUploadScheduler].
 */
expect val blobSchedulerModule: Module
