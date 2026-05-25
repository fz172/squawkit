package dev.fanfly.wingslog.core.storage.di

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceOverview
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.model.settings.FeatureLabSettings
import dev.fanfly.wingslog.core.model.userinfo.UserInfo
import app.cash.sqldelight.db.SqlDriver
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.DatabaseHealth
import dev.fanfly.wingslog.core.storage.DatabaseIntegrityChecker
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.DriverFactory
import dev.fanfly.wingslog.core.storage.EntityCodecRegistry
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.LocalAccountMigrator
import dev.fanfly.wingslog.core.storage.LocalAccountMigratorImpl
import dev.fanfly.wingslog.core.storage.TombstoneGc
import dev.fanfly.wingslog.core.storage.WireCodec
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.core.storage.storageIoContext
import kotlin.time.ExperimentalTime
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common storage Koin module. Provides the [WingsLogDatabase], the [EntityCodecRegistry] with
 * every [CollectionKind] codec pre-registered, and the [EntityStoreFactory] that managers consume.
 *
 * The platform [DriverFactory] is provided by
 * [platformStorageModule] in androidMain / iosMain.
 */
@OptIn(ExperimentalTime::class) val storageModule: Module = module {
  single<SqlDriver> { get<DriverFactory>().createDriver() }

  single<WingsLogDatabase> { createWingsLogDatabase(get<SqlDriver>()) }

  // Serializes all DB write-units so a write's change-notification is never captured by a
  // concurrently-open transaction (see DatabaseWriteLock). Shared by every writer below.
  single<DatabaseWriteLock> { DatabaseWriteLock() }

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
        CollectionKind.UserInfo,
        WireCodec(UserInfo.ADAPTER)
      )
      register(
        CollectionKind.FeatureLab,
        WireCodec(FeatureLabSettings.ADAPTER)
      )
      register(
        CollectionKind.Squawk,
        WireCodec(Squawk.ADAPTER)
      )
      verifyCoverage()
    }
  }

  single<EntityStoreFactory> {
    EntityStoreFactory(
      db = get(),
      codecs = get(),
      ioContext = storageIoContext,
      writeLock = get<DatabaseWriteLock>(),
    )
  }

  single<TombstoneGc> { TombstoneGc(db = get(), writeLock = get<DatabaseWriteLock>()) }

  single<DatabaseIntegrityChecker> {
    DatabaseIntegrityChecker(
      db = get<WingsLogDatabase>(),
      driver = get<SqlDriver>(),
      writeLock = get<DatabaseWriteLock>(),
    )
  }

  single<LocalAccountMigrator> {
    LocalAccountMigratorImpl(db = get<WingsLogDatabase>(), writeLock = get<DatabaseWriteLock>())
  }

  single<DatabaseHealth> { DatabaseHealth(isCorrupted = !get<DatabaseIntegrityChecker>().checkIntegrity()) }
}
