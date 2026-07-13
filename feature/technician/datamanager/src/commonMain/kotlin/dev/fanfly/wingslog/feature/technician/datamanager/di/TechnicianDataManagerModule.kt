package dev.fanfly.wingslog.feature.technician.datamanager.di

import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.datamanager.impl.TechnicianManagerImpl
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.dsl.module

val technicianDataManagerModule = module {
  single<TechnicianManager> {
    TechnicianManagerImpl(
      get<FirebaseAuth>(),
      get<CloudSyncSetting>(),
      get<EntityStoreFactory>(),
      get<WingsLogDatabase>(),
      get<DatabaseWriteLock>(),
    )
  }
}
