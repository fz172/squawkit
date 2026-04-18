package dev.fanfly.wingslog.di

import dev.fanfly.wingslog.core.attachments.datamanager.impl.attachmentModule
import dev.fanfly.wingslog.core.attachments.datamanager.impl.platformAttachmentModule
import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.core.database.di.commonFirebaseModule
import dev.fanfly.wingslog.feature.fleet.datamanager.di.fleetDataManagerModule
import dev.fanfly.wingslog.feature.fleet.viewing.di.fleetViewingModule
import dev.fanfly.wingslog.feature.inspection.datamanager.inspectionModule
import dev.fanfly.wingslog.feature.inspection.update.viewmodel.inspectionUiModule
import dev.fanfly.wingslog.feature.maintenance.datamanager.impl.maintenanceDataManagerModule
import dev.fanfly.wingslog.feature.aircraft.dashboard.di.aircraftDashboardModule
import dev.fanfly.wingslog.feature.maintenance.viewing.di.maintenanceViewingModule
import dev.fanfly.wingslog.feature.maintenance.update.di.maintenanceUpdateModule
import dev.fanfly.wingslog.feature.settings.di.settingsModule
import dev.fanfly.wingslog.feature.technician.datamanager.di.technicianDataManagerModule
import dev.fanfly.wingslog.feature.technician.manage.di.technicianManageModule
import dev.fanfly.wingslog.feature.userprofile.database.impl.userProfileDatabaseModule
import dev.fanfly.wingslog.feature.userprofile.di.userProfileModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
  startKoin {
    appDeclaration()
    modules(
      commonFirebaseModule,
      authModule,
      attachmentModule,
      platformAttachmentModule,
      userProfileDatabaseModule,
      technicianDataManagerModule,
      technicianManageModule,
      maintenanceDataManagerModule,
      maintenanceViewingModule,
      maintenanceUpdateModule,
      aircraftDashboardModule,
      inspectionModule,
      inspectionUiModule,
      fleetDataManagerModule,
      appModule,
      userProfileModule,
      settingsModule,
      fleetViewingModule,
    )
  }
