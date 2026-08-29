package dev.fanfly.wingslog.feature.fleet.picker.data.di

import dev.fanfly.wingslog.feature.fleet.picker.data.AndroidSelectedThingStore
import dev.fanfly.wingslog.feature.fleet.picker.data.SelectedThingStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val selectedThingStoreModule: Module = module {
  single<SelectedThingStore> { AndroidSelectedThingStore(androidContext()) }
}
