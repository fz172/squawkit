package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.StarterTasksAccepted
import dev.fanfly.wingslog.core.analytics.StarterTasksOffered
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.toMaintenanceTask
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionRun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionEntry
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.TaskSuggestionManager
import dev.fanfly.wingslog.feature.tasks.suggestions.model.AcceptedSuggestion
import dev.fanfly.wingslog.feature.tasks.suggestions.model.StarterPackItem
import dev.fanfly.wingslog.feature.tasks.suggestions.model.toSuggestion
import dev.fanfly.wingslog.rpc.suggesttasks.SuggestTasksResult
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The empty task list's recommended tasks, and the task list's *Suggest tasks* (PRD R1, R2). Not a
 * step after creating a Thing since 2026-10-03.
 *
 * Two sources until the v1 flag removal (T25):
 * - **The app's starter pack**, off the Thing's own DNA, where suggestions are not supported yet
 *   (production). The DNA is what the Thing was created from, and what an empty Tasks tab
 *   re-offers later.
 * - **The suggestion RPC**, where they are (developer builds; design §6.8, PRD R1). The starter
 *   mode asks for the curated suggestions alone; the suggest mode starts the model run, whose
 *   first result is the same curated list.
 *
 * Both §13 events are emitted from here either way: `starter_tasks_offered` when cards are first
 * shown, the denominator that tells "declined" apart from "never offered", and
 * `starter_tasks_accepted` with how many survived.
 */
class StarterPackViewModel(
  private val fleetManager: FleetManager,
  private val taskDataManager: TaskDataManager,
  private val templateRegistry: TemplateRegistry,
  private val analytics: AnalyticsManager,
  private val capability: AppCapability,
  private val suggestionManager: TaskSuggestionManager,
  private val suggestEntry: TaskSuggestionEntry,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val thingId: String = checkNotNull(savedStateHandle[Screen.THING_ID])

  private val _uiState = MutableStateFlow(
    StarterPackUiState(
      mode = savedStateHandle.get<String>(Screen.SUGGESTIONS_MODE) ?: Screen.StarterPack.MODE_STARTER,
    ),
  )
  val uiState = _uiState.asStateFlow()

  /** The run the cards come from, on the server source; null on the app's pack. */
  private var run: SuggestionRun? = null

  /** The job the cards follow: the one this screen started last. */
  private var followedJob: AiJobId? = null

  init {
    viewModelScope.launch {
      val template = fleetManager.loadThing(thingId)
        .filterNotNull()
        .first()
        .template
      _uiState.update { it.copy(template = template, lexicon = templateRegistry.lexiconFor(template)) }
      if (capability.isTaskSuggestionsSupported) showRun(template) else showPack(template)
    }
  }

  private fun showPack(template: ThingTemplate?) {
    val items = template?.starter_tasks.orEmpty()
      .mapIndexed { index, task ->
        StarterPackItem(suggestion = task.toSuggestion(index), selected = task.default_selected, starterTask = task)
      }
    // Nothing to offer — a stale route, or a pack removed by a DNA refresh. Not an offer, so not
    // counted as one.
    _uiState.update { it.copy(isLoading = false, items = items, isDone = items.isEmpty()) }
    if (items.isNotEmpty()) offered(template, items.size)
  }

  private suspend fun showRun(template: ThingTemplate?) {
    val curatedOnly = uiState.value.mode == Screen.StarterPack.MODE_STARTER
    val started = suggestionManager.start(thingId, entryPoint = uiState.value.mode, curatedOnly = curatedOnly)
    if (started !is AiStartResult.Started) {
      // A guest, a connection that failed, a refusal: nothing to show. T17 says which.
      logger.i { "No suggestions to show: $started" }
      _uiState.update { it.copy(isLoading = false, isDone = true) }
      return
    }
    followedJob = started.jobId
    // The curated list came without the model; the user can ask for it here (PRD R1), where the
    // Thing is described well enough (R5).
    if (curatedOnly && suggestEntry.observe(thingId).first() == SuggestEntry.Available) {
      _uiState.update { it.copy(canSuggest = true) }
    }
    var counted = false
    suggestionManager.observeRun(thingId).collect { latest ->
      // Until the listener catches up with the job just started, the newest it knows is older.
      if (latest.jobIdOrNull != followedJob) return@collect
      run = latest
      val result = latest.resultOrNull
      val finished = latest !is SuggestionRun.Working
      // A curated-only run is finished from the start, so only a model run reads as working.
      _uiState.update { it.copy(isSuggesting = !finished) }
      if (result == null) {
        // A template with no curated list, and no model answer (yet).
        if (finished) _uiState.update { it.copy(isLoading = false, isDone = it.items.isEmpty()) }
        return@collect
      }
      _uiState.update { state -> state.copy(isLoading = false, items = itemsOf(result, state.items)) }
      if (!counted) {
        counted = true
        offered(template, result.suggestions.size)
      }
    }
  }

  /**
   * *Suggest tasks* on the curated list: starts the model run, whose first result is the same
   * curated list, so the cards stay while it works and its answer replaces them (design §9.2).
   * Documents join with the sources sheet (T22).
   */
  fun onSuggest() {
    if (!uiState.value.canSuggest) return
    _uiState.update { it.copy(canSuggest = false, isSuggesting = true) }
    viewModelScope.launch {
      val curatedRun = run
      val started = suggestionManager.start(thingId, entryPoint = Screen.StarterPack.MODE_STARTER, curatedOnly = false)
      if (started !is AiStartResult.Started) {
        logger.i { "Suggest tasks did not start: $started" }
        _uiState.update { it.copy(canSuggest = true, isSuggesting = false) }
        return@launch
      }
      followedJob = started.jobId
      // The curated-only run is finished with; the new one carries the same list.
      curatedRun?.jobIdOrNull?.takeIf { it != started.jobId }?.let { suggestionManager.dismiss(it) }
    }
  }

  fun onToggle(index: Int) {
    _uiState.update { state ->
      state.copy(
        items = state.items.mapIndexed { i, item ->
          if (i == index) item.copy(selected = !item.selected) else item
        }
      )
    }
  }

  fun onAccept() {
    val state = uiState.value
    val chosen = state.items.filter { it.selected }
    if (chosen.isEmpty() || state.isSaving) return
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true) }
      val current = run
      val written = if (current == null) writePack(chosen, state.template) else writeRun(current, chosen)
      if (written > 0) {
        analytics.log(
          StarterTasksAccepted(
            templateId = state.template?.id.orEmpty(),
            taskCount = written
          )
        )
      }
      _uiState.update {
        it.copy(
          isSaving = false,
          isDone = true,
          acceptedCount = written
        )
      }
    }
  }

  /** "Skip" is a first-class answer (PRD §8.1), and it leaves no trace but the offered event. */
  fun onSkip() {
    // A finished run is closed; one still working carries on, and its push brings the user back
    // (PRD R19, R20).
    val current = run
    if (current != null && current !is SuggestionRun.Working) {
      viewModelScope.launch { current.jobIdOrNull?.let { suggestionManager.dismiss(it) } }
    }
    _uiState.update { it.copy(isDone = true) }
  }

  /**
   * One write per card, and a failure drops only its own card: the pack is a convenience, not a
   * transaction, and a half-written pack is still a better Tasks tab than an empty one.
   */
  private suspend fun writePack(chosen: List<StarterPackItem>, template: ThingTemplate?): Int {
    val now = Clock.System.now()
    val createdAt = toWireInstant(now.epochSeconds, now.nanosecondsOfSecond)
    return chosen.mapNotNull { it.starterTask }.count { task ->
      taskDataManager.addTask(thingId, task.toMaintenanceTask(template, createdAt))
        .onFailure { logger.w(it) { "Starter task '${task.title}' was not written" } }
        .isSuccess
    }
  }

  /**
   * Through the manager, which maps each as the server describes it and closes the run. Accepting
   * while the model still works takes the cards on screen and ends the run.
   */
  private suspend fun writeRun(current: SuggestionRun, chosen: List<StarterPackItem>): Int {
    val jobId = current.jobIdOrNull ?: return 0
    val result = current.resultOrNull ?: return 0
    val ready = current as? SuggestionRun.Ready ?: SuggestionRun.Ready(jobId, result)
    return suggestionManager.accept(thingId, ready, chosen.map { AcceptedSuggestion(it.suggestion) })
  }

  private fun offered(template: ThingTemplate?, count: Int) {
    analytics.log(StarterTasksOffered(templateId = template?.id.orEmpty(), taskCount = count))
  }

  private companion object {
    val logger = Logger.withTag("StarterPackViewModel")

    /**
     * Cards for [result], ticked as the server says (R27), except that a card already on screen
     * keeps the user's choice when the model's answer replaces the curated list.
     */
    fun itemsOf(result: SuggestTasksResult, shown: List<StarterPackItem>): List<StarterPackItem> {
      val shownIds = shown.mapTo(mutableSetOf()) { it.suggestion.suggestion_id?.value_ }
      val chosenIds = shown.filter { it.selected }.mapTo(mutableSetOf()) { it.suggestion.suggestion_id?.value_ }
      return result.suggestions.map { suggestion ->
        val id = suggestion.suggestion_id?.value_
        StarterPackItem(suggestion = suggestion, selected = if (id in shownIds) id in chosenIds else suggestion.preselect)
      }
    }

    /** The run's job, whatever its state; null when there is no run. */
    val SuggestionRun.jobIdOrNull: AiJobId?
      get() = when (this) {
        SuggestionRun.Idle -> null
        is SuggestionRun.Working -> jobId
        is SuggestionRun.Ready -> jobId
        is SuggestionRun.Empty -> jobId
        is SuggestionRun.Failed -> jobId
      }

    /** The suggestions the run holds now: the curated list, or the merged answer (design §6.8). */
    val SuggestionRun.resultOrNull: SuggestTasksResult?
      get() = when (this) {
        SuggestionRun.Idle -> null
        is SuggestionRun.Working -> result
        is SuggestionRun.Ready -> result
        is SuggestionRun.Empty -> result
        is SuggestionRun.Failed -> result
      }
  }
}
