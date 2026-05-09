package dev.fanfly.wingslog.feature.tasks.update.viewmodel

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tasksUiModule = module {
  viewModel<TaskViewModel> { TaskViewModel(get(), get(), get(), get(), get(), get()) }
}
