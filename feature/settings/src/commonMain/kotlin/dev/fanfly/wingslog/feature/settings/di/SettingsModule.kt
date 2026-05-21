package dev.fanfly.wingslog.feature.settings.di

import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.featurelab.FeatureLabBackendProbe
import dev.fanfly.wingslog.feature.settings.featurelab.FeatureLabViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
  single { FeatureLabBackendProbe() }
  viewModel {
    SettingsViewModel(
      get(),
      get(),
      get(),
      get<DatabaseIntegrityChecker>(),
      get(),
    )
  }
  viewModel { FeatureLabViewModel(get(), get()) }
}
