package dev.fanfly.wingslog.core.di

import dev.fanfly.wingslog.core.analytics.di.analyticsPreferenceModule
import dev.fanfly.wingslog.core.analytics.di.analyticsPreferenceStoreModule
import dev.fanfly.wingslog.core.analytics.di.platformAnalyticsModule
import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.core.auth.di.commonAuthModule
import dev.fanfly.wingslog.core.storage.di.platformStorageModule
import dev.fanfly.wingslog.core.storage.di.storageModule
import dev.fanfly.wingslog.core.ui.theme.di.appearanceModule
import dev.fanfly.wingslog.core.ui.theme.di.appearanceStoreModule
import dev.fanfly.wingslog.feature.aircraft.dashboard.di.aircraftDashboardModule
import dev.fanfly.wingslog.feature.aircraft.update.di.aircraftUpdateModule
import dev.fanfly.wingslog.feature.attachment.datamanager.attachmentModule
import dev.fanfly.wingslog.feature.attachment.datamanager.platformAttachmentModule
import dev.fanfly.wingslog.feature.export.datamanager.di.exportDataManagerModule
import dev.fanfly.wingslog.feature.export.datamanager.di.exportPlatformModule
import dev.fanfly.wingslog.feature.export.update.viewmodel.exportUiModule
import dev.fanfly.wingslog.feature.featurelab.datamanager.di.featureLabModule
import dev.fanfly.wingslog.feature.subscription.datamanager.di.subscriptionModule
import dev.fanfly.wingslog.feature.fleet.datamanager.di.fleetDataManagerModule
import dev.fanfly.wingslog.feature.fleet.picker.data.di.selectedAircraftStoreModule
import dev.fanfly.wingslog.feature.login.di.loginModule
import dev.fanfly.wingslog.feature.logs.datamanager.impl.maintenanceDataManagerModule
import dev.fanfly.wingslog.feature.logs.update.di.maintenanceUpdateModule
import dev.fanfly.wingslog.feature.logs.viewing.di.maintenanceViewingModule
import dev.fanfly.wingslog.feature.settings.di.settingsModule
import dev.fanfly.wingslog.feature.sharing.datamanager.sharingModule
import dev.fanfly.wingslog.feature.sharing.update.di.sharingUiModule
import dev.fanfly.wingslog.feature.shell.di.shellModule
import dev.fanfly.wingslog.feature.squawk.datamanager.squawkModule
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.squawkUiModule
import dev.fanfly.wingslog.feature.sync.data.blob.di.blobSchedulerModule
import dev.fanfly.wingslog.feature.sync.data.di.syncModule
import dev.fanfly.wingslog.feature.sync.logging.di.syncLoggingModule
import dev.fanfly.wingslog.feature.sync.settings.di.syncSettingsModule
import dev.fanfly.wingslog.feature.tasks.datamanager.tasksModule
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.tasksUiModule
import dev.fanfly.wingslog.feature.technician.datamanager.di.technicianDataManagerModule
import dev.fanfly.wingslog.feature.technician.manage.di.technicianManageModule
import org.koin.core.module.Module

/**
 * Every Koin module shared by *all* host apps: auth, storage, sync, analytics, and every
 * feature's data/viewing/update layer. Both `composeApp` (Android/iOS, via `initKoin`) and
 * `webApp` (`main.kt`) build their Koin graph from this list plus their own host-only bootstrap
 * (`AppCapability` construction, `stressTestKoinModules()`, host-only singles like the web
 * SQLite worker).
 *
 * Kept as a single source of truth after this list drifted between the two hosts once already —
 * a module added to one but not the other surfaces as a runtime `NoDefinitionFoundException`
 * (Koin resolves lazily), not a compile error.
 */
val commonAppModules: List<Module> = listOf(
  platformAnalyticsModule,
  analyticsPreferenceStoreModule,
  analyticsPreferenceModule,
  commonAuthModule,
  authModule,
  storageModule,
  platformStorageModule,
  appearanceModule,
  appearanceStoreModule,
  selectedAircraftStoreModule,
  syncModule,
  syncLoggingModule,
  blobSchedulerModule,
  attachmentModule,
  platformAttachmentModule,
  exportDataManagerModule,
  exportPlatformModule,
  exportUiModule,
  featureLabModule,
  subscriptionModule,
  technicianDataManagerModule,
  technicianManageModule,
  maintenanceDataManagerModule,
  maintenanceViewingModule,
  maintenanceUpdateModule,
  aircraftDashboardModule,
  aircraftUpdateModule,
  tasksModule,
  tasksUiModule,
  squawkModule,
  squawkUiModule,
  fleetDataManagerModule,
  sharingModule,
  sharingUiModule,
  loginModule,
  settingsModule,
  syncSettingsModule,
  shellModule,
)
