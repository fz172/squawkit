package dev.fanfly.wingslog.core.storage.di

import dev.fanfly.wingslog.core.storage.DriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformStorageModule: Module = module {
  // The Worker is provided by the host app (webApp), which owns the bundled worker file.
  single { DriverFactory(get()) }
}
