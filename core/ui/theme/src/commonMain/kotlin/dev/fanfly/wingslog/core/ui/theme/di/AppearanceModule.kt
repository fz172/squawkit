package dev.fanfly.wingslog.core.ui.theme.di

import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.AppearanceStore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform-provided device-local [AppearanceStore]. The Android actual receives the application
 * [android.content.Context] via Koin's `androidContext()`, mirroring `platformStorageModule`.
 */
internal expect val platformAppearanceStoreModule: Module

/** The one appearance entry `commonAppModules` lists: the controller + the platform store. */
val appearanceModule: Module = module {
  includes(platformAppearanceStoreModule)
  single { AppearanceController(get<AppearanceStore>()) }
}
