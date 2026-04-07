package dev.fanfly.wingslog.feature.maintenance.datamanager.impl

import dev.fanfly.wingslog.feature.maintenance.datamanager.MaintenanceLogManager
import org.koin.dsl.module

val maintenanceDataManagerModule = module {
  single<MaintenanceLogManager> { MaintenanceLogManagerImpl(get(), get()) }
}
