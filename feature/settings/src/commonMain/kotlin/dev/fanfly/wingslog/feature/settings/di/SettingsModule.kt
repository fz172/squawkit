package dev.fanfly.wingslog.feature.settings.di

import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceController
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AccountDeleter
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.ui.theme.AppearanceController
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenRegistrar
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.settings.data.SettingsViewModel
import dev.fanfly.wingslog.feature.settings.developeroptions.DeveloperOptionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
  viewModel {
    SettingsViewModel(
      get<AuthManager>(),
      get<AccountDeleter>(),
      get<AttachmentManager>(),
      get<DatabaseIntegrityChecker>(),
      get<DeveloperOptionsManager>(),
      get<AppearanceController>(),
      get<AnalyticsPreferenceController>(),
      get<AppCapability>(),
      get<AdConsentManager>(),
      get<NotificationPermission>(),
      get<NotificationPrefsManager>(),
      // getOrNull, not get: only Android binds a registrar today (iOS is P5, web is P6 and never
      // will). A hard get() here would turn "this platform has no push transport" into a startup
      // crash on the Settings screen.
      getOrNull<PushTokenRegistrar>(),
    )
  }
  viewModel { DeveloperOptionsViewModel(get<DeveloperOptionsManager>(), get<AdConsentManager>()) }
}
