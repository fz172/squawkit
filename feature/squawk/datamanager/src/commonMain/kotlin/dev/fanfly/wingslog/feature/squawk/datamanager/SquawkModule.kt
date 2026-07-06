package dev.fanfly.wingslog.feature.squawk.datamanager

import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.squawk.datamanager.impl.SquawkManagerImpl
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.dsl.module

val squawkModule = module {
  single<SquawkManager> {
    SquawkManagerImpl(
      get<FirebaseAuth>(),
      get<EntityStoreFactory>()
    )
  }
}
