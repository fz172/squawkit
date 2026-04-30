package dev.fanfly.wingslog.core.storage.di

import dev.fanfly.wingslog.core.storage.DriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformStorageModule: Module = module {
  single { DriverFactory(androidContext()) }
}
