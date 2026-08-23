package dev.fanfly.wingslog.feature.notifications.engine.di

import dev.fanfly.wingslog.core.storage.ForeignWriteListener
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.engine.NoOpUrgencyScanScheduler
import dev.fanfly.wingslog.feature.notifications.engine.UrgencyScanScheduler
import dev.fanfly.wingslog.feature.notifications.engine.WebForeignWriteDetector
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformNotificationEngineModule: Module = module {
  single<UrgencyScanScheduler> { NoOpUrgencyScanScheduler() }

  // N1 on web (design §8). Bound here and nowhere else: Android and iOS get N1 by push, and running
  // both paths would double-notify. `feature:sync:data` resolves this as ForeignWriteListener via
  // getOrNull, so the absence of this binding on the other two hosts is the no-op path, not an error.
  //
  // Not createdAtStart: it resolves SharingManager and FleetManager, which reach Firebase, and
  // building those during startup NPEs on iOS. The sync engine resolves it lazily on first use.
  single<ForeignWriteListener> {
    WebForeignWriteDetector(
      sharingManager = get<SharingManager>(),
      fleetManager = get<FleetManager>(),
      prefsManager = get<NotificationPrefsManager>(),
      permission = get<NotificationPermission>(),
      notifier = get<LocalNotifier>(),
    )
  }
}
