package dev.fanfly.wingslog.feature.tasks.dashboard.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.tasks.dashboard.AddedTasksViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.SuggestTasksEntryViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.TaskTabViewModel
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.RecentlyAddedTasks
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The tasks section tab: its filter/search ViewModel, its suggestions state and the tasks just
 * added from them, keyed per thing.
 */
val tasksDashboardModule = module {
  viewModel { params ->
    TaskTabViewModel(
      get<TaskStatusManager>(),
      get<SearchEngine>(),
      get<SearchTuning>(),
      get<AnalyticsManager>(),
      params.get<String>(0),
      params.get<String>(1),
    )
  }
  viewModel { params ->
    AddedTasksViewModel(
      get<RecentlyAddedTasks>(),
      get<TaskDataManager>(),
      params.get<String>(0),
    )
  }
  viewModel { params ->
    SuggestTasksEntryViewModel(
      get<TaskSuggestionEntry>(),
      params.get<String>(0)
    )
  }
}
