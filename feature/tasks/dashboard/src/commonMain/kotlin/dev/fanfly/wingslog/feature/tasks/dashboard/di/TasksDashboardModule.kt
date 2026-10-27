package dev.fanfly.wingslog.feature.tasks.dashboard.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.search.datamanager.SearchEngine
import dev.fanfly.wingslog.feature.search.model.SearchTuning
import dev.fanfly.wingslog.feature.tasks.dashboard.AddedTasksViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.ReadySuggestionsViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.SuggestTasksEntryViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.TaskOverviewViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.TaskTabViewModel
import dev.fanfly.wingslog.feature.tasks.dashboard.detail.TaskDetailViewModel
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.RecentlyAddedTasks
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.id.TemplateId
import dev.fanfly.wingslog.id.ThingId

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
      params.get<ThingId>(),
      params.get<TemplateId>(),
    )
  }
  viewModel { params ->
    TaskDetailViewModel(
      get<TaskDataManager>(),
      get<TaskStatusManager>(),
      get<MaintenanceLogManager>(),
      get<CommentManager>(),
      get<TemplateRegistry>(),
      get<AnalyticsManager>(),
      params.get<ThingId>(),
      params.get<TemplateId>(),
    )
  }
  viewModel { params ->
    TaskOverviewViewModel(
      get<TaskStatusManager>(),
      params.get<ThingId>(),
    )
  }
  viewModel { params ->
    ReadySuggestionsViewModel(
      get<TaskSuggestionManager>(),
      params.get<ThingId>(),
    )
  }
  viewModel { params ->
    AddedTasksViewModel(
      get<RecentlyAddedTasks>(),
      get<TaskDataManager>(),
      params.get<ThingId>(),
    )
  }
  viewModel { params ->
    SuggestTasksEntryViewModel(
      get<TaskSuggestionEntry>(),
      params.get<ThingId>(),
    )
  }
}
