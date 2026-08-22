package dev.fanfly.wingslog.feature.notifications.engine.di

import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanner
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyWatermarkStore
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.Module
import org.koin.dsl.module

val notificationEngineModule: Module = module {
  single { UrgencyWatermarkStore(db = get<WingsLogDatabase>()) }
  single {
    UrgencyScanner(
      auth = get<FirebaseAuth>(),
      prefsManager = get<NotificationPrefsManager>(),
      permission = get<NotificationPermission>(),
      fleetManager = get<FleetManager>(),
      scopeResolver = get<AircraftScopeResolver>(),
      taskDueManager = get<TaskDueManager>(),
      logManager = get<MaintenanceLogManager>(),
      entityStoreFactory = get<EntityStoreFactory>(),
      watermarkStore = get<UrgencyWatermarkStore>(),
      notifier = get<LocalNotifier>(),
    )
  }
}
