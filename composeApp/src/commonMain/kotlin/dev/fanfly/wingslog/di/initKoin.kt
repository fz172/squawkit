package dev.fanfly.wingslog.di

import dev.fanfly.wingslog.feature.attachment.datamanager.attachmentModule
import dev.fanfly.wingslog.feature.attachment.datamanager.platformAttachmentModule
import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.core.auth.di.commonAuthModule
import dev.fanfly.wingslog.core.storage.di.platformStorageModule
import dev.fanfly.wingslog.core.storage.di.storageModule
import dev.fanfly.wingslog.feature.aircraft.dashboard.di.aircraftDashboardModule
import dev.fanfly.wingslog.feature.export.datamanager.di.exportDataManagerModule
import dev.fanfly.wingslog.feature.export.update.viewmodel.exportUiModule
import dev.fanfly.wingslog.feature.fleet.datamanager.di.fleetDataManagerModule
import dev.fanfly.wingslog.feature.fleet.viewing.di.fleetViewingModule
import dev.fanfly.wingslog.feature.logs.datamanager.impl.maintenanceDataManagerModule
import dev.fanfly.wingslog.feature.logs.update.di.maintenanceUpdateModule
import dev.fanfly.wingslog.feature.logs.viewing.di.maintenanceViewingModule
import dev.fanfly.wingslog.feature.settings.di.settingsModule
import dev.fanfly.wingslog.feature.sync.data.blob.di.blobSchedulerModule
import dev.fanfly.wingslog.feature.sync.data.di.syncModule
import dev.fanfly.wingslog.feature.sync.settings.di.syncSettingsModule
import dev.fanfly.wingslog.feature.squawk.datamanager.squawkModule
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.squawkUiModule
import dev.fanfly.wingslog.feature.tasks.datamanager.tasksModule
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.tasksUiModule
import dev.fanfly.wingslog.feature.technician.datamanager.di.technicianDataManagerModule
import dev.fanfly.wingslog.feature.technician.manage.di.technicianManageModule
import dev.fanfly.wingslog.feature.featurelab.datamanager.di.featureLabModule
import dev.fanfly.wingslog.DogfoodFeatureExtensions
import dev.fanfly.wingslog.NoOpDogfoodExtensions
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(
  dogfoodExtensions: DogfoodFeatureExtensions = NoOpDogfoodExtensions,
  appDeclaration: KoinAppDeclaration = {},
) = startKoin {
  appDeclaration()
  val allModules = dogfoodExtensions.koinModules() + listOf(
    module { single<DogfoodFeatureExtensions> { dogfoodExtensions } },
    commonAuthModule,
    storageModule,
    platformStorageModule,
    syncModule,
    blobSchedulerModule,
    authModule,
    attachmentModule,
    platformAttachmentModule,
    exportDataManagerModule,
    exportUiModule,
    featureLabModule,
    technicianDataManagerModule,
    technicianManageModule,
    maintenanceDataManagerModule,
    maintenanceViewingModule,
    maintenanceUpdateModule,
    aircraftDashboardModule,
    tasksModule,
    tasksUiModule,
    squawkModule,
    squawkUiModule,
    fleetDataManagerModule,
    appModule,
    settingsModule,
    syncSettingsModule,
    fleetViewingModule,
  )
  modules(allModules)
}
