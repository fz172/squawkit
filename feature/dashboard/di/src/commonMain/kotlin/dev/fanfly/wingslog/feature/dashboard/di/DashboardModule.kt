package dev.fanfly.wingslog.feature.dashboard.di

import dev.fanfly.wingslog.feature.dashboard.host.di.dashboardHostModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the dashboard feature into the one entry `commonAppModules` lists.
 * The per-feature tabs register their own ViewModels through their feature's di module.
 */
val dashboardModule: Module = module {
  includes(
    dashboardHostModule,
  )
}
