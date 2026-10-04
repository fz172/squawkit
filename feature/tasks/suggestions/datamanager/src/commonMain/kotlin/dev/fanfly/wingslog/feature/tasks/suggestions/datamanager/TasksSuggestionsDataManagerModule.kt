package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.core.storage.AiJobDocumentStore
import dev.fanfly.wingslog.core.storage.CurrentUidProvider
import dev.fanfly.wingslog.core.storage.EntitySyncObserver
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.impl.TaskSuggestionManagerImpl
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.Module
import org.koin.dsl.module

/** The suggestions data layer (docs/ai/task_population_design.md §7). */
val tasksSuggestionsDataManagerModule: Module = module {
  single<SuggestionContextBuilder> {
    SuggestionContextBuilder(
      fleetManager = get<FleetManager>(),
      taskDataManager = get<TaskDataManager>(),
      logManager = get<MaintenanceLogManager>(),
      templateRegistry = get<TemplateRegistry>(),
      scopeResolver = get<ThingScopeResolver>(),
    )
  }
  single<SuggestionMapper> { SuggestionMapper() }
  single<TaskSuggestionEntry> {
    TaskSuggestionEntry(
      capability = get<AppCapability>(),
      auth = get<FirebaseAuth>(),
      fleetManager = get<FleetManager>(),
      templateRegistry = get<TemplateRegistry>(),
    )
  }
  // createdAtStart so it releases finished runs' documents from the first session on, with no host
  // wiring; its Firebase-backed parts are providers, as SessionBoundaryScanTrigger's are.
  single<JobDocumentReleaser>(createdAtStart = true) {
    JobDocumentReleaser(
      store = get<AiJobDocumentStore>(),
      currentUid = { get<CurrentUidProvider>().currentUid() },
      client = { get<AiJobClient>() },
      attachments = { get<AttachmentManager>() },
      foreground = get<AppForegroundObserver>(),
    ).also { it.start() }
  }
  single<TaskSuggestionManager> {
    TaskSuggestionManagerImpl(
      client = get<AiJobClient>(),
      contextBuilder = get<SuggestionContextBuilder>(),
      mapper = get<SuggestionMapper>(),
      fleetManager = get<FleetManager>(),
      taskDataManager = get<TaskDataManager>(),
      templateRegistry = get<TemplateRegistry>(),
      scopeResolver = get<ThingScopeResolver>(),
      syncObserver = get<EntitySyncObserver>(),
      attachmentManager = get<AttachmentManager>(),
      jobDocuments = get<JobDocumentReleaser>(),
      currentUid = get<CurrentUidProvider>(),
    )
  }
}
