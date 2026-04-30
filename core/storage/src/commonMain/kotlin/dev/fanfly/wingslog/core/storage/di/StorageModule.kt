package dev.fanfly.wingslog.core.storage.di

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceOverview
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityCodecRegistry
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.WireCodec
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common storage Koin module. Provides the [WingsLogDatabase], the [EntityCodecRegistry] with
 * every [CollectionKind] codec pre-registered, and the [EntityStoreFactory] that managers consume.
 *
 * The platform [dev.fanfly.wingslog.core.storage.DriverFactory] is provided by
 * [platformStorageModule] in androidMain / iosMain.
 */
@OptIn(ExperimentalTime::class) val storageModule: Module = module {
  single<WingsLogDatabase> { createWingsLogDatabase(get<dev.fanfly.wingslog.core.storage.DriverFactory>().createDriver()) }

  single<EntityCodecRegistry> {
    EntityCodecRegistry().apply {
      register(
        CollectionKind.Aircraft,
        WireCodec(Aircraft.ADAPTER)
      )
      register(
        CollectionKind.MaintenanceTask,
        WireCodec(MaintenanceTask.ADAPTER)
      )
      register(
        CollectionKind.MaintenanceLog,
        WireCodec(MaintenanceLog.ADAPTER)
      )
      register(
        CollectionKind.MaintenanceOverview,
        WireCodec(MaintenanceOverview.ADAPTER)
      )
      register(
        CollectionKind.Technician,
        WireCodec(Technician.ADAPTER)
      )
      register(
        CollectionKind.LicenseInfo,
        WireCodec(LicenseInfo.ADAPTER)
      )
      verifyCoverage()
    }
  }

  single<EntityStoreFactory> {
    EntityStoreFactory(
      db = get(),
      codecs = get(),
      ioContext = Dispatchers.Default,
    )
  }
}
