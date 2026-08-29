package dev.fanfly.wingslog.feature.fleet.picker.data.di

import dev.fanfly.wingslog.feature.fleet.picker.data.JsSelectedThingStore
import dev.fanfly.wingslog.feature.fleet.picker.data.SelectedThingStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val selectedThingStoreModule: Module = module {
  single<SelectedThingStore> { JsSelectedThingStore() }
}
