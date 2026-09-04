package dev.fanfly.wingslog.feature.tasks.update.starter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.StarterTasksAccepted
import dev.fanfly.wingslog.core.analytics.StarterTasksOffered
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.GenericLexicon
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.toMaintenanceTask
import dev.fanfly.wingslog.thing.Lexicon
import dev.fanfly.wingslog.thing.StarterTask
import dev.fanfly.wingslog.thing.ThingTemplate
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StarterPackItem(
  val task: StarterTask,
  val selected: Boolean,
)

data class StarterPackUiState(
  val isLoading: Boolean = true,
  val template: ThingTemplate? = null,
  val lexicon: Lexicon = GenericLexicon.LEXICON,
  val items: List<StarterPackItem> = emptyList(),
  val isSaving: Boolean = false,
  /** Set once the step is over, either way; how many were written says which way. */
  val isDone: Boolean = false,
  val acceptedCount: Int = 0,
) {
  val selectedCount: Int get() = items.count { it.selected }
}

/**
 * Step 4 of creating a Thing (PRD §8.1): the template's recommended schedule, offered once.
 *
 * Reads the pack off the Thing's own DNA rather than the canonical registry — the DNA is what the
 * Thing was created from, and it is what an empty Tasks tab re-offers later. Both §13 events are
 * emitted from here: `starter_tasks_offered` when the pack is shown, which is the denominator that
 * tells "declined" apart from "never offered", and `starter_tasks_accepted` with how many survived.
 */
class StarterPackViewModel(
  private val fleetManager: FleetManager,
  private val taskDataManager: TaskDataManager,
  private val templateRegistry: TemplateRegistry,
  private val analytics: AnalyticsManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val thingId: String = checkNotNull(savedStateHandle[Screen.THING_ID])

  private val _uiState = MutableStateFlow(StarterPackUiState())
  val uiState = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val thing = fleetManager.loadThing(thingId)
        .filterNotNull()
        .first()
      val template = thing.template
      val items = template?.starter_tasks.orEmpty()
        .map { StarterPackItem(task = it, selected = it.default_selected) }
      _uiState.update {
        it.copy(
          isLoading = false,
          template = template,
          lexicon = templateRegistry.lexiconFor(template),
          items = items,
          // Nothing to offer — a stale route, or a pack removed by a DNA refresh. Not an offer,
          // so not counted as one.
          isDone = items.isEmpty(),
        )
      }
      if (items.isNotEmpty()) {
        analytics.log(StarterTasksOffered(templateId = template?.id.orEmpty(), taskCount = items.size))
      }
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
      val now = Clock.System.now()
      val createdAt = toWireInstant(now.epochSeconds, now.nanosecondsOfSecond)
      // One write per card, and a failure drops only its own card: the pack is a convenience, not
      // a transaction, and a half-written pack is still a better Tasks tab than an empty one.
      val written = chosen.count { item ->
        taskDataManager.addTask(thingId, item.task.toMaintenanceTask(state.template, createdAt))
          .onFailure { logger.w(it) { "Starter task '${item.task.title}' was not written" } }
          .isSuccess
      }
      if (written > 0) {
        analytics.log(
          StarterTasksAccepted(templateId = state.template?.id.orEmpty(), taskCount = written)
        )
      }
      _uiState.update { it.copy(isSaving = false, isDone = true, acceptedCount = written) }
    }
  }

  /** "Skip" is a first-class answer (PRD §8.1), and it leaves no trace but the offered event. */
  fun onSkip() {
    _uiState.update { it.copy(isDone = true) }
  }

  private companion object {
    val logger = Logger.withTag("StarterPackViewModel")
  }
}
