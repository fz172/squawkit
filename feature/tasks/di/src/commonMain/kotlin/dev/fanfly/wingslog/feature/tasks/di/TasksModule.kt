package dev.fanfly.wingslog.feature.tasks.di

import dev.fanfly.wingslog.feature.tasks.datamanager.tasksDataManagerModule
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.tasksUpdateModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the tasks feature (inspection compliance tasks) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val tasksModule: Module = module {
  includes(
    tasksDataManagerModule,
    tasksUpdateModule,
  )
}
