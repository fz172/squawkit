package dev.fanfly.wingslog.feature.sharing.datamanager

import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sharing.datamanager.impl.ThingScopeResolverImpl
import dev.fanfly.wingslog.feature.sharing.datamanager.impl.SharingManagerImpl
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import org.koin.dsl.module

val sharingModule = module {
  single<SharingManager> {
    SharingManagerImpl(
      auth = get<FirebaseAuth>(),
      firestore = get<FirebaseFirestore>(),
      storeFactory = get<EntityStoreFactory>(),
      db = get<WingsLogDatabase>(),
      writeLock = get<DatabaseWriteLock>(),
      technicianManager = get<TechnicianManager>(),
      functions = get<FirebaseFunctions>(),
    )
  }
  // Own-vs-shared scope resolution for the per-thing managers (logs/tasks/squawk). Lives here
  // because it needs auth + the refs store; consumers depend only on the core:storage interface.
  single<ThingScopeResolver> {
    ThingScopeResolverImpl(
      auth = get<FirebaseAuth>(),
      storeFactory = get<EntityStoreFactory>(),
    )
  }
}
