package dev.fanfly.wingslog.feature.notifications.datamanager.di

import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.impl.NotificationPrefsManagerImpl
import dev.fanfly.wingslog.feature.sync.data.SyncCursorStore
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.Module
import org.koin.dsl.module

val notificationPrefsModule: Module = module {
  single<NotificationPrefsManager> {
    NotificationPrefsManagerImpl(
      firebaseAuth = get<FirebaseAuth>(),
      cloudSyncSetting = get<CloudSyncSetting>(),
      cursorStore = get<SyncCursorStore>(),
      syncEngine = get<SyncEngine>(),
      storeFactory = get<EntityStoreFactory>(),
    )
  }
}
