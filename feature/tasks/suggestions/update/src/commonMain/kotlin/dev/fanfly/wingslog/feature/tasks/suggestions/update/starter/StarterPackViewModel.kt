package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.ai.AiStartResult
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.StarterTasksAccepted
import dev.fanfly.wingslog.core.analytics.StarterTasksOffered
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsAccepted
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsFailed
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsRequested
import dev.fanfly.wingslog.core.analytics.TaskSuggestionsShown
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
  private val clock: Clock = Clock.System,
) : ViewModel() {

  val thingId: String = checkNotNull(savedStateHandle[Screen.THING_ID])

  private val _uiState = MutableStateFlow(
    StarterPackUiState(
      mode = savedStateHandle.get<String>(Screen.SUGGESTIONS_MODE)
        ?: Screen.StarterPack.MODE_STARTER,
    ),
  )
  val uiState = _uiState.asStateFlow()

  /** The run the cards come from, on the server source; null on the app's pack. */
  private var run: SuggestionRun? = null

  /** The job the cards follow: the one this screen started last. */
  private var followedJob: AiJobId? = null

  /** The user asked for the model, here or from *Suggest tasks*, rather than the curated list alone. */
  private var modelRequested = false

  /** When the model was last asked, for the shown event's latency; and which job it was. */
  private var requestedAt: Instant? = null
  private var reportedJob: AiJobId? = null

  init {
    viewModelScope.launch {
      val template = fleetManager.loadThing(thingId)
        .filterNotNull()
        .first()
        .template
      _uiState.update {
        it.copy(
          template = template,
          lexicon = templateRegistry.lexiconFor(template)
        )
      }
      if (capability.isTaskSuggestionsSupported) showRun(template) else showPack(
        template
      )
    }
  }

  private fun showPack(template: ThingTemplate?) {
    val items = template?.starter_tasks.orEmpty()
      .mapIndexed { index, task ->
        StarterPackItem(
          suggestion = task.toSuggestion(index),
          // Nothing is checked to start (PRD R27, revised 2026-10-03).
          selected = false,
          starterTask = task
        )
      }
    // Nothing to offer — a stale route, or a pack removed by a DNA refresh. Not an offer, so not
    // counted as one.
    _uiState.update {
      it.copy(
        isLoading = false,
        items = items,
        isDone = items.isEmpty()
      )
    }
    if (items.isNotEmpty()) offered(template, items.size)
  }

  private suspend fun showRun(template: ThingTemplate?) {
    // R19: the model's answer is held for a day until the user acts on it. Opening the list again
    // shows it rather than starting over: a new run would hide it behind a newer job, and a second
    // model run the same day is refused anyway (R49).
    // The listener answers at once, from cache if need be; a slow one is not worth waiting on.
    val earlier = withTimeoutOrNull(RESUME_WAIT) { suggestionManager.observeRun(thingId).firstOrNull() }
    if (earlier != null && earlier.holdsModelAnswer()) {
      modelRequested = true
      followedJob = earlier.jobIdOrNull
      // Already reported when it first arrived.
      if (earlier !is SuggestionRun.Working) reportedJob = followedJob
      follow(template)
      return
    }
    val curatedOnly = uiState.value.mode == Screen.StarterPack.MODE_STARTER
    modelRequested = !curatedOnly
    val started = suggestionManager.start(
      thingId,
      entryPoint = uiState.value.mode,
      curatedOnly = curatedOnly
    )
    if (started !is AiStartResult.Started) {
      // Nothing to show: the screen closes, and the task tab says why (PRD R21, R51).
      logger.i { "No suggestions to show: $started" }
      val reason = (started as AiStartResult.Refused).reason
      if (modelRequested) failed(reason)
      _uiState.update { it.copy(isLoading = false, isDone = true, closingError = reason) }
      return
    }
    followedJob = started.jobId
    if (modelRequested) requested(uiState.value.mode)
    // The curated list came without the model; the user can ask for it here (PRD R1), where the
    // Thing is described well enough (R5).
    if (curatedOnly && suggestEntry.observe(thingId)
        .first() == SuggestEntry.Available
    ) {
      _uiState.update { it.copy(canSuggest = true) }
    }
    follow(template)
  }

  /** Follows [followedJob] for as long as the screen is open, into the cards and their states. */
  private suspend fun follow(template: ThingTemplate?) {
    var counted = false
    suggestionManager.observeRun(thingId)
      .collect { latest ->
        // Until the listener catches up with the job just started, the newest it knows is older.
        if (latest.jobIdOrNull != followedJob) return@collect
        run = latest
        val result = latest.resultOrNull
        val finished = latest !is SuggestionRun.Working
        val failure = (latest as? SuggestionRun.Failed)?.reason
        // A curated-only run is finished from the start, so only a model run reads as working.
        val working = latest as? SuggestionRun.Working
        // Only a model run can come back with nothing to say; a curated-only one that is empty is
        // a template with no list.
        val notEnough = latest is SuggestionRun.Empty && modelRequested
        if (finished && modelRequested) report(latest)
        _uiState.update {
          it.copy(
            isSuggesting = !finished,
            failure = failure,
            stage = working?.stage,
            stageArg = working?.stageArg,
            aiSkipped = (latest as? SuggestionRun.Ready)?.aiSkipped,
            notEnough = notEnough,
          )
        }
        if (result == null) {
          // A template with no curated list, and no model answer (yet). A failure with no cards
          // to fall back on closes the screen, and the task tab says why.
          // An empty model run stays, to offer *Add details* (R21a; for the custom template that
          // message is the whole screen).
          if (finished) _uiState.update {
            val close = it.items.isEmpty() && !notEnough
            it.copy(
              isLoading = false,
              isDone = close,
              closingError = failure.takeIf { _ -> close },
            )
          }
          return@collect
        }
        _uiState.update { state ->
          state.copy(
            isLoading = false,
            items = itemsOf(
              result,
              state.items
            )
          )
        }
        if (!counted) {
          counted = true
          offered(template, result.suggestions.size)
        }
        _uiState.update { it.copy(firstDues = firstDuesOf(result)) }
      }
  }

  /**
   * *Suggest tasks* on the curated list: starts the model run, whose first result is the same
   * curated list, so the cards stay while it works and its answer replaces them (design §9.2).
   * Documents join with the sources sheet (T22).
   */
  fun onSuggest() {
    if (!uiState.value.canSuggest) return
    startModelRun(onRefused = { it.copy(canSuggest = true) })
  }

  /** *Try again* after a failed model run: starts a new one over the cards still on screen. */
  fun onRetry() {
    if (uiState.value.failure == null) return
    startModelRun(onRefused = { it })
  }

  /** The refusal of *Suggest more* or *Try again*, once shown. */
  fun onNoticeShown() {
    _uiState.update { it.copy(notice = null) }
  }

  /**
   * The due engine's answer for each card the user could add (R29): mapped exactly as accepting
   * maps it, dated from now, so the line cannot disagree with the task it becomes. Already-tracked
   * cards are not added, so they get none.
   */
  private suspend fun firstDuesOf(result: SuggestTasksResult): List<CardFirstDue> =
    result.suggestions
      .filter { it.matches_existing_task_id?.value_.isNullOrEmpty() }
      .mapNotNull { suggestion ->
        val id = suggestion.suggestion_id?.value_ ?: return@mapNotNull null
        runCatching { suggestionManager.firstDue(thingId, suggestion) }
          .onFailure { logger.w(it) { "No first due for '${suggestion.title}'" } }
          .getOrNull()
          ?.let { CardFirstDue(id, it) }
      }

  /** *Add details* after an empty run: the run is done with; the screen gives way to the Thing's edit form. */
  fun onAddDetails() {
    run?.jobIdOrNull?.let { viewModelScope.launch { suggestionManager.dismiss(it) } }
  }

  private fun startModelRun(onRefused: (StarterPackUiState) -> StarterPackUiState) {
    modelRequested = true
    _uiState.update { it.copy(canSuggest = false, isSuggesting = true, failure = null, notEnough = false) }
    viewModelScope.launch {
      val curatedRun = run
      val started = suggestionManager.start(
        thingId,
        entryPoint = Screen.StarterPack.MODE_STARTER,
        curatedOnly = false
      )
      if (started !is AiStartResult.Started) {
        logger.i { "The model run did not start: $started" }
        val reason = (started as AiStartResult.Refused).reason
        failed(reason)
        _uiState.update { onRefused(it.copy(isSuggesting = false, notice = reason)) }
        return@launch
      }
      followedJob = started.jobId
      requested(SUGGEST_MORE)
      // The run on screen (curated-only, or failed) is finished with; the new one carries the
      // same curated list.
      curatedRun?.jobIdOrNull?.takeIf { it != started.jobId }
        ?.let { suggestionManager.dismiss(it) }
    }
  }

  fun onToggle(index: Int) {
    _uiState.update { state ->
      state.copy(
        items = state.items.mapIndexed { i, item ->
          // An already-tracked card cannot be added again (PRD R24).
          if (i == index && !item.isAlreadyTracked) item.copy(selected = !item.selected) else item
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
      val written =
        if (current == null) writePack(chosen, state.template) else writeRun(
          current,
          chosen
        )
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
    // A curated-only or failed run is closed. The model's answer is kept for the day (R19), so
    // leaving and coming back finds it; one still working carries on, and its push brings the
    // user back (R20). Accepting is what closes an answer.
    val current = run
    if (current != null && !current.holdsModelAnswer()) {
      viewModelScope.launch {
        current.jobIdOrNull?.let {
          suggestionManager.dismiss(
            it
          )
        }
      }
    }
    _uiState.update { it.copy(isDone = true) }
  }

  /**
   * One write per card, and a failure drops only its own card: the pack is a convenience, not a
   * transaction, and a half-written pack is still a better Tasks tab than an empty one.
   */
  private suspend fun writePack(
    chosen: List<StarterPackItem>,
    template: ThingTemplate?
  ): Int {
    val now = Clock.System.now()
    val createdAt = toWireInstant(now.epochSeconds, now.nanosecondsOfSecond)
    return chosen.mapNotNull { it.starterTask }
      .count { task ->
        taskDataManager.addTask(
          thingId,
          task.toMaintenanceTask(template, createdAt)
        )
          .onFailure { logger.w(it) { "Starter task '${task.title}' was not written" } }
          .isSuccess
      }
  }

  /**
   * Through the manager, which maps each as the server describes it and closes the run. Accepting
   * while the model still works takes the cards on screen and ends the run.
   */
  private suspend fun writeRun(
    current: SuggestionRun,
    chosen: List<StarterPackItem>
  ): Int {
    val jobId = current.jobIdOrNull ?: return 0
    val result = current.resultOrNull ?: return 0
    val ready =
      current as? SuggestionRun.Ready ?: SuggestionRun.Ready(jobId, result)
    val written = suggestionManager.accept(
      thingId,
      ready,
      chosen.map { AcceptedSuggestion(it.suggestion) })
    if (written > 0) {
      analytics.log(
        TaskSuggestionsAccepted(
          templateId = uiState.value.template?.id.orEmpty(),
          curatedCount = chosen.count { !it.suggestion.isFromModel() },
          aiCount = chosen.count { it.suggestion.isFromModel() },
        ),
      )
    }
    return written
  }

  /** R50: the model was asked; its answer or failure is reported once per job by [report]. */
  private fun requested(entryPoint: String) {
    requestedAt = clock.now()
    analytics.log(
      TaskSuggestionsRequested(
        templateId = uiState.value.template?.id.orEmpty(),
        entryPoint = entryPoint,
        documentCount = 0,
      ),
    )
  }

  private fun report(finished: SuggestionRun) {
    val jobId = finished.jobIdOrNull ?: return
    if (jobId == reportedJob) return
    reportedJob = jobId
    when (finished) {
      is SuggestionRun.Failed -> failed(finished.reason)
      else -> {
        val cards = finished.resultOrNull?.suggestions.orEmpty()
        val latency = requestedAt?.let { (clock.now() - it).inWholeSeconds } ?: 0L
        analytics.log(
          TaskSuggestionsShown(
            templateId = uiState.value.template?.id.orEmpty(),
            curatedCount = cards.count { !it.isFromModel() },
            aiCount = cards.count { it.isFromModel() },
            latencySeconds = latency,
          ),
        )
      }
    }
  }

  private fun failed(reason: AiErrorCode) {
    analytics.log(
      TaskSuggestionsFailed(templateId = uiState.value.template?.id.orEmpty(), reason = reason.name.lowercase()),
    )
  }

  private fun offered(template: ThingTemplate?, count: Int) {
    analytics.log(
      StarterTasksOffered(
        templateId = template?.id.orEmpty(),
        taskCount = count
      )
    )
  }

  private companion object {
    val logger = Logger.withTag("StarterPackViewModel")

    /** How long opening the list waits to learn whether a model answer is held (R19). */
    val RESUME_WAIT = 2.seconds

    /** The entry point a model run asked from the curated list reports (R50). */
    const val SUGGEST_MORE = "suggest_more"

    /**
     * Cards for [result], none checked to start (PRD R27, revised 2026-10-03: the user checks what
     * they need, and the server's `preselect` is not read). A card already on screen keeps the
     * user's check when the model's answer replaces the curated list.
     */
    fun itemsOf(
      result: SuggestTasksResult,
      shown: List<StarterPackItem>
    ): List<StarterPackItem> {
      val chosenIds = shown.filter { it.selected }
        .mapTo(mutableSetOf()) { it.suggestion.suggestion_id?.value_ }
      return result.suggestions.map { suggestion ->
        StarterPackItem(
          suggestion = suggestion,
          selected = suggestion.suggestion_id?.value_ in chosenIds
        )
      }
    }

    /**
     * A model run working, or one whose answer has the model's cards in it: what the user would
     * lose by closing it or by starting another. A curated-only run, an empty or failed model run,
     * and no run at all hold nothing of the kind.
     */
    fun SuggestionRun.holdsModelAnswer(): Boolean =
      this is SuggestionRun.Working ||
        resultOrNull?.suggestions.orEmpty().any { it.isFromModel() }

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
