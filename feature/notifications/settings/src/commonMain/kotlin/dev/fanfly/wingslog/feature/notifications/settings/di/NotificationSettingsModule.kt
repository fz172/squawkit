package dev.fanfly.wingslog.feature.notifications.settings.di

import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.settings.NotificationSettingsViewModel
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val notificationSettingsModule = module {
  viewModel {
    NotificationSettingsViewModel(
      prefsManager = get<NotificationPrefsManager>(),
      permission = get<NotificationPermission>(),
      auth = get<FirebaseAuth>(),
      syncPreferences = get<SyncPreferences>(),
    )
  }
}
