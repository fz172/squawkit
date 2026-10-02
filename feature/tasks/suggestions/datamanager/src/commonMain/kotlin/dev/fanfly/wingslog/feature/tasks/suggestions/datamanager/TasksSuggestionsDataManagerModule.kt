package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The suggestions data layer's bindings. Empty until T15 adds `TaskSuggestionManager` and its
 * collaborators (docs/ai/task_population_design.md §7); registered now so the module's place in
 * `tasksModule` is settled with the move (T13).
 */
val tasksSuggestionsDataManagerModule: Module = module {
}
