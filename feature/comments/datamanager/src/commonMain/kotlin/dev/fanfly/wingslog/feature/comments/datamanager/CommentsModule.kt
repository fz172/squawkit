package dev.fanfly.wingslog.feature.comments.datamanager

import dev.fanfly.wingslog.core.storage.CurrentUidProvider
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.comments.datamanager.impl.CommentManagerImpl
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.dsl.module

val commentsModule = module {
  single<CommentManager> {
    CommentManagerImpl(
      get<ThingScopeResolver>(),
      get<CurrentUidProvider>(),
      get<TechnicianManager>(),
      get<FirebaseAuth>(),
      get<EntityStoreFactory>(),
    )
  }
}
