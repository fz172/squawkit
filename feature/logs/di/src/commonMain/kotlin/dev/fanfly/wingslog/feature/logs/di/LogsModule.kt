package dev.fanfly.wingslog.feature.logs.di

import dev.fanfly.wingslog.feature.logs.datamanager.impl.logsDataManagerModule
import dev.fanfly.wingslog.feature.logs.update.di.logsUpdateModule
import dev.fanfly.wingslog.feature.logs.viewing.di.logsViewingModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the logs feature (maintenance logs) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val logsModule: Module = module {
  includes(
    logsDataManagerModule,
    logsViewingModule,
    logsUpdateModule,
  )
}
