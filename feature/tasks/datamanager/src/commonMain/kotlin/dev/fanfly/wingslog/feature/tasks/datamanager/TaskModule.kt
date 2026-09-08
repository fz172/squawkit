package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.tasks.datamanager.impl.TaskDataManagerImpl
import dev.fanfly.wingslog.feature.tasks.datamanager.impl.TaskDueManagerImpl
import dev.fanfly.wingslog.feature.tasks.datamanager.impl.TaskStatusManagerImpl
import org.koin.dsl.module

val tasksDataManagerModule = module {
  single<TaskDataManager> {
    TaskDataManagerImpl(
      get<ThingScopeResolver>(),
      get<CommentManager>(),
      get<EntityStoreFactory>()
    )
  }
  single<TaskDueManager> { TaskDueManagerImpl() }
  single<TaskStatusManager> {
    TaskStatusManagerImpl(get<ThingScopeResolver>(), get<EntityStoreFactory>(), get<TaskDueManager>())
  }
}
