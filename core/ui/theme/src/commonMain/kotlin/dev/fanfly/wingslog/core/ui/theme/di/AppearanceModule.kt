package dev.fanfly.wingslog.core.ui.theme.di

import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.core.ui.theme.AppearanceStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** Common appearance wiring; the platform [appearanceStoreModule] supplies the [AppearanceStore]. */
val appearanceModule = module {
  single { AppearanceController(get<AppearanceStore>()) }
}

/**
 * Platform-provided device-local [AppearanceStore]. The Android actual receives the application
 * [android.content.Context] via Koin's `androidContext()`, mirroring `platformStorageModule`.
 */
expect val appearanceStoreModule: Module
