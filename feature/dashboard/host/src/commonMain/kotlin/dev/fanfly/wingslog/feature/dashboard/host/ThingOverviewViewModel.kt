package dev.fanfly.wingslog.feature.dashboard.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.core.template.TemplateResolution
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.dashboard.api.LogStats
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewAction
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewEvent
import dev.fanfly.wingslog.feature.dashboard.api.ThingOverviewUiState
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskStatusManager
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.thing.ComponentType
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.core.sharedassets.generated.resources.save_failed
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

class ThingOverviewViewModel(
  private val fleetManager: FleetManager,
  private val logManager: MaintenanceLogManager,
  private val taskStatusManager: TaskStatusManager,
  private val sharingManager: SharingManager,
  private val templateRegistry: TemplateRegistry,
  private val auth: FirebaseAuth,
  private val thingId: String,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow<ThingOverviewUiState>(ThingOverviewUiState.Loading)
  val uiState: StateFlow<ThingOverviewUiState> = _uiState.asStateFlow()

  // Buffered, not rendezvous: a quick action's snackbar can be emitted between the section being
  // torn down and the new one attaching its collector, and a rendezvous send would park there.
  private val _events = Channel<ThingOverviewEvent>(Channel.BUFFERED)

  /** Navigation and snackbar events; `ThingSectionContent` collects them (design §7). */
  val events: Flow<ThingOverviewEvent> = _events.receiveAsFlow()

  /** Due status depends on the clock, not only on stored data; re-evaluate when the screen returns. */
  fun onResumed() {
    taskStatusManager.refreshDueStatus()
  }

  init {
    loadThingAndStats()
  }

  private fun loadThingAndStats() {
    viewModelScope.launch {
      _uiState.update { ThingOverviewUiState.Loading }
      // Every collection shares one SQLDelight `entity` table, and SQLDelight notifies query
      // listeners per *table*, so any write anywhere — adding a squawk, a sync writeback — re-runs
      // and re-emits every observer here with identical content. Unfiltered, that re-ran
      // computeNextDue for every task card several times per unrelated write. distinctUntilChanged
      // drops the duplicates; the store still re-queries and re-decodes, which is a separate
      // (larger) fix at the EntityStore level.
      combine(
        fleetManager.loadThing(thingId)
          .distinctUntilChanged(),
        logManager.observeLogs(thingId)
          .distinctUntilChanged(),
        taskStatusManager.observeTasksWithStatus(thingId)
          .distinctUntilChanged(),
        // The readings as the records give them, not the stored overview: that is only as current
        // as the last client to rebuild it, and one that predates readings set by hand rebuilds
        // it without them (#1368).
        logManager.observeCurrentReadings(thingId)
          .distinctUntilChanged(),
        combine(
          // The caller's role on this thing, resolved locally (own ⇒ OWNER, shared ⇒ ref role).
          // Gates owner-only affordances in the UI; server rules remain the real enforcement (§6.3).
          sharingManager.observeMyRole(thingId)
            .distinctUntilChanged(),
          sharingManager.observeIsShared(thingId)
            .distinctUntilChanged(),
        ) { myRole, shared ->
          ShareContext(myRole, shared)
        }
      ) { thing, logs, cardsWithStatus, currentReadings, shareContext ->
        val (myRole, isShared) = shareContext
        val degraded = thing?.let {
          templateRegistry.resolve(it) as? TemplateResolution.Degraded
        }
        if (degraded != null) {
          // Before anything else is computed: the stats and due-status work below reads the
          // template's meters, and running it under DNA we cannot interpret is what produces the
          // wrong numbers this state exists to avoid showing (design §6.2).
          ThingOverviewUiState.Degraded(thing, degraded.reason)
        } else if (thing != null) {
          val template = templateRegistry.forThingWithFallback(thing)
          // Every meter this template declares that has a reading. A reading whose meter the
          // template no longer declares stays stored and is not shown.
          val declared = currentReadings.filter { reading ->
            template.meters.any { it.key == reading.meterKey }
          }
          val stats = LogStats(
            total = logs.size.toLong(),
            airframe = logs.count { it.component_type == ComponentType.COMPONENT_AIRFRAME }
              .toLong(),
            engine = logs.count { it.component_type == ComponentType.COMPONENT_ENGINE }
              .toLong(),
            propeller = logs.count { it.component_type == ComponentType.COMPONENT_PROPELLER }
              .toLong(),
            readings = declared.associate { it.meterKey to it.value },
            readingsAsOf = declared.mapNotNull { it.asOf }
              .maxOrNull(),
          )

          val active =
            cardsWithStatus.filter { it.dueStatus.status != DueStatus.COMPLIED }
          val complied =
            cardsWithStatus.filter { it.dueStatus.status == DueStatus.COMPLIED }

          ThingOverviewUiState.Success(
            thing = thing,
            logStats = stats,
            activeTasks = active,
            completedTasks = complied,
            recentLogs = logs.sortedByDescending {
              it.timestamp?.getEpochSecond() ?: 0L
            }
              .take(4),
            myRole = myRole,
            shared = isShared,
            isAnonymous = auth.currentUser?.isAnonymous ?: true,
          )
        } else {
          ThingOverviewUiState.Error
        }
      }.collect { state ->
        _uiState.update { state }
      }
    }
  }

  fun onAction(action: ThingOverviewAction) {
    when (action) {
      ThingOverviewAction.BackClick -> {
        viewModelScope.launch { _events.send(ThingOverviewEvent.NavigateBack) }
      }

      is ThingOverviewAction.EditClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToEditThing(
              action.thingId
            )
          )
        }
      }

      ThingOverviewAction.DeleteConfirm -> {
        deleteThing()
      }

      is ThingOverviewAction.ManageAccessClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToManageAccess(
              action.thingId
            )
          )
        }
      }

      is ThingOverviewAction.SetMeterReading ->
        setMeterReading(action.meterKey, action.value)

      is ThingOverviewAction.AddLogClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToAddLog(
              action.thingId
            )
          )
        }
      }

      is ThingOverviewAction.EditLogClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToEditLog(
              action.thingId,
              action.logId
            )
          )
        }
      }

      is ThingOverviewAction.AddTaskClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToAddTask(
              action.thingId
            )
          )
        }
      }

      // Navigation only — ThingSectionContent drives the navController and never forwards it.
      is ThingOverviewAction.BrowseSuggestionsClick -> Unit

      // Navigation only; ThingSectionContent routes it.
      is ThingOverviewAction.OpenDataLogClick -> Unit

      is ThingOverviewAction.EditTaskClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToEditTask(
              action.thingId,
              action.cardId
            )
          )
        }
      }

      is ThingOverviewAction.AddSquawkClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToAddSquawk(
              action.thingId
            )
          )
        }
      }

      is ThingOverviewAction.EditSquawkClick -> {
        viewModelScope.launch {
          _events.send(
            ThingOverviewEvent.NavigateToEditSquawk(
              action.thingId,
              action.squawkId
            )
          )
        }
      }

      // Navigation only — ThingSectionContent drives the navController and never forwards it.
      is ThingOverviewAction.SquawkFixedClick -> Unit
      is ThingOverviewAction.TaskCreateLogClick -> Unit
    }
  }

  private fun setMeterReading(meterKey: String, value: Double) {
    val state = _uiState.value as? ThingOverviewUiState.Success ?: return
    viewModelScope.launch {
      // Nothing to do on success: the card redraws from the reading the manager just stored.
      logManager.setManualReading(state.thing.id, meterKey, value)
        .onFailure {
          _events.send(
            ThingOverviewEvent.ShowMessage(UiText.StringRes(CoreRes.string.save_failed))
          )
        }
    }
  }

  fun deleteThing() {
    viewModelScope.launch {
      fleetManager.deleteThing(thingId)
        .onSuccess {
          _events.send(ThingOverviewEvent.NavigateBack)
        }
        .onFailure { error ->
          _events.send(
            ThingOverviewEvent.ShowMessage(
              error.message?.let { UiText.DynamicString(it) }
                ?: UiText.StringRes(CoreRes.string.delete_failed)
            )
          )
        }
    }
  }
}
