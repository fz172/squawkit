package dev.fanfly.wingslog.feature.tasks.suggestions.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.JobDocumentReleaser
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.RecentlyAddedTasks
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.update.add.AddTasksViewModel
import dev.fanfly.wingslog.feature.tasks.suggestions.update.review.SuggestionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tasksSuggestionsUpdateModule = module {
  viewModel<AddTasksViewModel> {
    AddTasksViewModel(
      fleetManager = get<FleetManager>(),
      templateRegistry = get<TemplateRegistry>(),
      suggestionManager = get<TaskSuggestionManager>(),
      suggestEntry = get<TaskSuggestionEntry>(),
      documents = get<JobDocumentReleaser>(),
      savedStateHandle = get<SavedStateHandle>(),
    )
  }
  viewModel<SuggestionsViewModel> {
    SuggestionsViewModel(
      fleetManager = get<FleetManager>(),
      taskDataManager = get<TaskDataManager>(),
      templateRegistry = get<TemplateRegistry>(),
      analytics = get<AnalyticsManager>(),
      suggestionManager = get<TaskSuggestionManager>(),
      suggestEntry = get<TaskSuggestionEntry>(),
      documents = get<JobDocumentReleaser>(),
      recentlyAdded = get<RecentlyAddedTasks>(),
      savedStateHandle = get<SavedStateHandle>(),
    )
  }
}
