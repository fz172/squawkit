package dev.fanfly.wingslog.feature.fleet.di

import dev.fanfly.wingslog.feature.fleet.datamanager.di.fleetDataManagerModule
import dev.fanfly.wingslog.feature.fleet.picker.data.di.platformSelectedThingStoreModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the fleet feature (the fleet list and the shell's current-thing
 * selection) into the one entry `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val fleetModule: Module = module {
  includes(
    platformSelectedThingStoreModule,
    fleetDataManagerModule,
  )
}
