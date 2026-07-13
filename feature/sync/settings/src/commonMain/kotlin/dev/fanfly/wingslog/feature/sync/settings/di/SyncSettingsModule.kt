package dev.fanfly.wingslog.feature.sync.settings.di

import dev.fanfly.wingslog.feature.sync.settings.SyncSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.gitlive.firebase.auth.FirebaseAuth

val syncSettingsModule = module {
  viewModel {
    SyncSettingsViewModel(
      auth = get<FirebaseAuth>(),
      syncPreferences = get<SyncPreferences>(),
      syncEngine = get<SyncEngine>(),
    )
  }
}
