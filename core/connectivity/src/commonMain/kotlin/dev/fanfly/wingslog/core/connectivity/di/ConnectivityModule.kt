package dev.fanfly.wingslog.core.connectivity.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** Binds the platform's `ConnectivityMonitor`. */
internal expect val platformConnectivityModule: Module

/** The one connectivity entry `commonAppModules` lists. */
val connectivityModule: Module = module {
  includes(platformConnectivityModule)
}
