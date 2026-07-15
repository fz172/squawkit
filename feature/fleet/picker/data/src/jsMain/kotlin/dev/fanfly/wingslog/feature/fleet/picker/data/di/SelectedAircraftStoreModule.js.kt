package dev.fanfly.wingslog.feature.fleet.picker.data.di

import dev.fanfly.wingslog.feature.fleet.picker.data.JsSelectedAircraftStore
import dev.fanfly.wingslog.feature.fleet.picker.data.SelectedAircraftStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val selectedAircraftStoreModule: Module = module {
  single<SelectedAircraftStore> { JsSelectedAircraftStore() }
}
