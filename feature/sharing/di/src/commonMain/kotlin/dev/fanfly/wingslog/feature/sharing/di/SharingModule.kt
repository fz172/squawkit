package dev.fanfly.wingslog.feature.sharing.di

import dev.fanfly.wingslog.feature.sharing.datamanager.sharingDataManagerModule
import dev.fanfly.wingslog.feature.sharing.update.di.sharingUiModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the sharing feature (multi-user thing sharing) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val sharingModule: Module = module {
  includes(
    sharingDataManagerModule,
    sharingUiModule,
  )
}
