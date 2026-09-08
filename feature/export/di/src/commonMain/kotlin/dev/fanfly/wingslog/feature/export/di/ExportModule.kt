package dev.fanfly.wingslog.feature.export.di

import dev.fanfly.wingslog.feature.export.datamanager.di.exportDataManagerModule
import dev.fanfly.wingslog.feature.export.datamanager.di.exportPlatformModule
import dev.fanfly.wingslog.feature.export.update.viewmodel.exportUiModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the export feature (logbook export) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val exportModule: Module = module {
  includes(
    exportDataManagerModule,
    exportPlatformModule,
    exportUiModule,
  )
}
