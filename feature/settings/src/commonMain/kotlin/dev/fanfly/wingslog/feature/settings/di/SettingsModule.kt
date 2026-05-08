package dev.fanfly.wingslog.feature.settings.di

import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
  viewModel {
    SettingsViewModel(
      get(),
      get(),
      get(),
    )
  }
}
