package dev.fanfly.wingslog.feature.settings.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.storage.LocalAccountMigrator
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.featurelab.FeatureLabViewModel
import dev.fanfly.wingslog.feature.settings.upgrade.AccountUpgradeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
  viewModel {
    SettingsViewModel(
      get(),
      get(),
      get(),
      get<DatabaseIntegrityChecker>(),
      get(),
    )
  }
  viewModel { FeatureLabViewModel(get()) }
  viewModel {
    AccountUpgradeViewModel(
      authManager = get<AuthManager>(),
      migrator = get<LocalAccountMigrator>(),
    )
  }
}
