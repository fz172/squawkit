package dev.fanfly.wingslog.feature.sharing.datamanager

import dev.fanfly.wingslog.core.storage.EntityStoreFactory
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
    )
  }
}
