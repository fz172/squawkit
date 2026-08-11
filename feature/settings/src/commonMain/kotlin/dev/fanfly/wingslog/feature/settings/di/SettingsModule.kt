package dev.fanfly.wingslog.feature.settings.di

import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceController
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.developeroptions.DeveloperOptionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
  viewModel {
    SettingsViewModel(
      get<AuthManager>(),
      get<AttachmentManager>(),
      get<DatabaseIntegrityChecker>(),
      get<DeveloperOptionsManager>(),
      get<AppearanceController>(),
      get<AnalyticsPreferenceController>(),
      get<AppCapability>(),
    )
  }
  viewModel { DeveloperOptionsViewModel(get<DeveloperOptionsManager>()) }
}
