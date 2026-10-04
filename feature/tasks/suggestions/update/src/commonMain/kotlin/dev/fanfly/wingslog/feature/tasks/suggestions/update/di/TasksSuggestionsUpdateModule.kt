package dev.fanfly.wingslog.feature.tasks.suggestions.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.update.starter.StarterPackViewModel
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tasksSuggestionsUpdateModule = module {
  viewModel<StarterPackViewModel> {
    StarterPackViewModel(
      fleetManager = get<FleetManager>(),
      taskDataManager = get<TaskDataManager>(),
      templateRegistry = get<TemplateRegistry>(),
      analytics = get<AnalyticsManager>(),
      suggestionManager = get<TaskSuggestionManager>(),
      suggestEntry = get<TaskSuggestionEntry>(),
      attachmentManager = get<AttachmentManager>(),
      savedStateHandle = get<SavedStateHandle>(),
    )
  }
}
