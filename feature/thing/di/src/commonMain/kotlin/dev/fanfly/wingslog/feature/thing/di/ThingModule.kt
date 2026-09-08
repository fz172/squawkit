package dev.fanfly.wingslog.feature.thing.di

import dev.fanfly.wingslog.feature.thing.dashboard.di.thingDashboardModule
import dev.fanfly.wingslog.feature.thing.update.di.thingUpdateModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the thing feature (the thing dashboard and thing CRUD) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val thingModule: Module = module {
  includes(
    thingDashboardModule,
    thingUpdateModule,
  )
}
