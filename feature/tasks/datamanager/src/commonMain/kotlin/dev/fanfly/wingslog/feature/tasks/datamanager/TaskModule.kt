package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.feature.tasks.datamanager.impl.TaskDataManagerImpl
import dev.fanfly.wingslog.feature.tasks.datamanager.impl.TaskDueManagerImpl
import org.koin.dsl.module

val tasksModule = module {
  single<TaskDataManager> { TaskDataManagerImpl(get(), get()) }
  single<TaskDueManager> { TaskDueManagerImpl() }
}
