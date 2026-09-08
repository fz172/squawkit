package dev.fanfly.wingslog.feature.squawk.di

import dev.fanfly.wingslog.feature.squawk.datamanager.squawkDataManagerModule
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.squawkUpdateModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the squawk feature (squawks) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val squawkModule: Module = module {
  includes(
    squawkDataManagerModule,
    squawkUpdateModule,
  )
}
