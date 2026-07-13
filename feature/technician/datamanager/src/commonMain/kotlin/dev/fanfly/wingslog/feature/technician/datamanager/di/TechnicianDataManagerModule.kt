package dev.fanfly.wingslog.feature.technician.datamanager.di

import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.datamanager.impl.TechnicianManagerImpl
import org.koin.dsl.module
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.core.storage.CloudSyncSetting

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
