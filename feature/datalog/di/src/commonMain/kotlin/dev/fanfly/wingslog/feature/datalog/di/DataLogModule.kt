package dev.fanfly.wingslog.feature.datalog.di

import dev.fanfly.wingslog.feature.datalog.datamanager.dataLogDataManagerModule
import dev.fanfly.wingslog.feature.datalog.update.viewmodel.dataLogUpdateModule
import dev.fanfly.wingslog.feature.datalog.viewing.di.dataLogViewingModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the data log visualizer feature into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val dataLogModule: Module = module {
  includes(
    dataLogDataManagerModule,
    dataLogViewingModule,
    dataLogUpdateModule,
  )
}
