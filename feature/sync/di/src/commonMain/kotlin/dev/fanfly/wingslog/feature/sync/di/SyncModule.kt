package dev.fanfly.wingslog.feature.sync.di

import dev.fanfly.wingslog.feature.sync.data.blob.di.platformBlobSchedulerModule
import dev.fanfly.wingslog.feature.sync.data.di.syncDataModule
import dev.fanfly.wingslog.feature.sync.logging.di.syncLoggingModule
import dev.fanfly.wingslog.feature.sync.settings.di.syncSettingsModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the sync feature (the local-first sync engine) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val syncModule: Module = module {
  includes(
    syncDataModule,
    syncLoggingModule,
    platformBlobSchedulerModule,
    syncSettingsModule,
  )
}
