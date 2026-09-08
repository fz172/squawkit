package dev.fanfly.wingslog.core.di

import dev.fanfly.wingslog.core.analytics.di.analyticsPreferenceModule
import dev.fanfly.wingslog.core.analytics.di.analyticsPreferenceStoreModule
import dev.fanfly.wingslog.core.analytics.di.platformAnalyticsModule
import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.core.auth.di.commonAuthModule
import dev.fanfly.wingslog.core.firebase.functions.functionsModule
import dev.fanfly.wingslog.core.lifecycle.di.lifecycleModule
import dev.fanfly.wingslog.core.lifecycle.di.platformLifecycleModule
import dev.fanfly.wingslog.core.storage.di.platformStorageModule
import dev.fanfly.wingslog.core.storage.di.storageModule
import dev.fanfly.wingslog.core.template.di.templateModule
import dev.fanfly.wingslog.core.ui.theme.di.appearanceModule
import dev.fanfly.wingslog.core.ui.theme.di.appearanceStoreModule
import dev.fanfly.wingslog.feature.ads.di.adsModule
import dev.fanfly.wingslog.feature.attachment.di.attachmentModule
import dev.fanfly.wingslog.feature.comments.datamanager.commentsModule
import dev.fanfly.wingslog.feature.developeroptions.datamanager.di.developerOptionsModule
import dev.fanfly.wingslog.feature.export.di.exportModule
import dev.fanfly.wingslog.feature.fleet.di.fleetModule
import dev.fanfly.wingslog.feature.login.di.loginModule
import dev.fanfly.wingslog.feature.logs.di.logsModule
import dev.fanfly.wingslog.feature.notifications.di.notificationsModule
import dev.fanfly.wingslog.feature.search.datamanager.di.searchModule
import dev.fanfly.wingslog.feature.settings.di.settingsModule
import dev.fanfly.wingslog.feature.sharing.di.sharingModule
import dev.fanfly.wingslog.feature.shell.di.shellModule
import dev.fanfly.wingslog.feature.squawk.di.squawkModule
import dev.fanfly.wingslog.feature.subscription.di.subscriptionModule
import dev.fanfly.wingslog.feature.sync.di.syncModule
import dev.fanfly.wingslog.feature.tasks.di.tasksModule
import dev.fanfly.wingslog.feature.technician.di.technicianModule
import dev.fanfly.wingslog.feature.thing.di.thingModule
import org.koin.core.module.Module

/**
 * Every Koin module shared by *all* host apps: auth, storage, sync, analytics, and one entry per
 * feature. Both `composeApp` (Android/iOS, via `initKoin`) and `webApp` (`main.kt`) build their
 * Koin graph from this list plus their own host-only bootstrap (`AppCapability` construction,
 * `stressTestKoinModules()`, host-only singles like the web SQLite worker).
 *
 * A feature with more than one Koin module bundles them in its `feature/<name>/di` uber module
 * (see `notificationsModule`), so this list and `core/di`'s dependencies stay one-per-feature.
 * A feature with a single Koin module (search, comments, …) lists that module directly.
 *
 * Kept as a single source of truth after this list drifted between the two hosts once already —
 * a module added to one but not the other surfaces as a runtime `NoDefinitionFoundException`
 * (Koin resolves lazily), not a compile error.
 */
val commonAppModules: List<Module> = listOf(
  platformAnalyticsModule,
  analyticsPreferenceStoreModule,
  analyticsPreferenceModule,
  // Ahead of authModule: on Android this supplies the CurrentActivityProvider that AuthManagerImpl
  // takes. Koin resolves lazily so the order is not required, but the list is read by people and
  // the dependency direction should be visible. Empty on iOS and web.
  platformLifecycleModule,
  // The one Cloud Functions client. Ahead of every callable client that injects it.
  functionsModule,
  commonAuthModule,
  authModule,
  storageModule,
  templateModule,
  platformStorageModule,
  appearanceModule,
  appearanceStoreModule,
  syncModule,
  attachmentModule,
  exportModule,
  developerOptionsModule,
  subscriptionModule,
  // Ahead of adsModule: the ad session counter depends on the foreground observer, not the reverse.
  lifecycleModule,
  adsModule,
  technicianModule,
  logsModule,
  thingModule,
  tasksModule,
  squawkModule,
  searchModule,
  commentsModule,
  fleetModule,
  sharingModule,
  loginModule,
  // Notifications (P1 done; P2 in progress — docs/notifications/notifications_design.md §14.1).
  // :engine's UrgencyScanner (P2.1-P2.3) has no scheduled caller yet — only the Developer Options
  // "scan now" trigger — the platform schedulers (WorkManager/BGTaskScheduler, P2.6/P2.7) still
  // don't exist.
  notificationsModule,
  settingsModule,
  shellModule,
)
