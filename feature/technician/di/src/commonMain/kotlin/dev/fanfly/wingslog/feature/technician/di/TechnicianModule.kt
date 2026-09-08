package dev.fanfly.wingslog.feature.technician.di

import dev.fanfly.wingslog.feature.technician.datamanager.di.technicianDataManagerModule
import dev.fanfly.wingslog.feature.technician.manage.di.technicianManageModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the technician feature (technicians) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val technicianModule: Module = module {
  includes(
    technicianDataManagerModule,
    technicianManageModule,
  )
}
