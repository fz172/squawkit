package dev.fanfly.wingslog.feature.sync.settings.di

import dev.fanfly.wingslog.feature.sync.settings.SyncSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val syncSettingsModule = module {
  viewModel {
    SyncSettingsViewModel(
      auth = get(),
      syncPreferences = get(),
      syncEngine = get(),
    )
  }
}
