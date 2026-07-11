package dev.fanfly.wingslog.feature.sharing.datamanager

import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.sharing.datamanager.impl.AircraftScopeResolverImpl
import dev.fanfly.wingslog.feature.sharing.datamanager.impl.SharingManagerImpl
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.dsl.module

val sharingModule = module {
  single<SharingManager> {
    SharingManagerImpl(
      auth = get<FirebaseAuth>(),
      firestore = get<FirebaseFirestore>(),
      storeFactory = get<EntityStoreFactory>(),
      db = get(),
      writeLock = get(),
    )
  }
  // Own-vs-shared scope resolution for the per-aircraft managers (logs/tasks/squawk). Lives here
  // because it needs auth + the refs store; consumers depend only on the core:storage interface.
  single<AircraftScopeResolver> {
    AircraftScopeResolverImpl(
      auth = get<FirebaseAuth>(),
      storeFactory = get<EntityStoreFactory>(),
    )
  }
}
