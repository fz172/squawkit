package dev.fanfly.wingslog.feature.notifications.datamanager.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenRegistrar
import dev.fanfly.wingslog.feature.notifications.datamanager.SignOutCoordinator
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

  single {
    SignOutCoordinator(
      authManager = get<AuthManager>(),
      // getOrNull, not get: only Android binds a registrar today (iOS is P5, web is P6 and never
      // will). A hard get() would turn "this platform has no push transport" into a startup crash.
      pushTokenRegistrar = getOrNull<PushTokenRegistrar>(),
    )
  }
}
