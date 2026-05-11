package dev.fanfly.wingslog.feature.aircraft.dashboard.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.ui.common.navigation.Screen
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.squawk.model.toWithStatus
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class AircraftOverviewViewModel(
  private val fleetManager: FleetManager,
  private val logManager: MaintenanceLogManager,
  private val taskDataManager: TaskDataManager,
  private val taskDueManager: TaskDueManager,
  private val attachmentOpener: AttachmentOpener,
  private val attachmentManager: AttachmentManager,
  private val squawkManager: SquawkManager,
  private val auth: FirebaseAuth,
  private val featureLabManager: FeatureLabManager,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val aircraftId: String = checkNotNull(savedStateHandle[Screen.AIRCRAFT_ID])

  private val _uiState = MutableStateFlow<AircraftOverviewUiState>(AircraftOverviewUiState.Loading)
  val uiState: StateFlow<AircraftOverviewUiState> = _uiState.asStateFlow()

  private val _events = Channel<AircraftOverviewEvent>()
  val events = _events.receiveAsFlow()

  private var cachedLogs: List<MaintenanceLog> = emptyList()
  private var legacyBannerDismissed = false

  init {
    loadAircraftAndStats()
    viewModelScope.launch {
      featureLabManager.observe().collect { flags ->
        _uiState.update { state ->
          if (state is AircraftOverviewUiState.Success)
            state.copy(attachmentEnabled = flags.attachmentUploadEnabled)
          else state
        }
      }
    }
  }

  private fun blobStatesFlow() =
    auth.currentUser?.uid?.let { uid ->
      attachmentManager.observeBlobStates("/users/$uid/aircraft/$aircraftId/")
    } ?: flowOf(emptyMap())

  private fun loadAircraftAndStats() {
    viewModelScope.launch {
      _uiState.update { AircraftOverviewUiState.Loading }
      combine(
        fleetManager.loadAircraft(aircraftId),
        logManager.observeLogs(aircraftId),
        taskDataManager.observeTasks(aircraftId),
        logManager.observeMaintenanceOverview(aircraftId),
        combine(
          squawkManager.observeSquawks(aircraftId),
          combine(
            blobStatesFlow(),
            attachmentOpener.downloadingIds
          ) { blobStates, downloadingIds ->
            buildMap {
              putAll(blobStates)
              downloadingIds.forEach {
                put(
                  it,
                  BlobSyncState.Downloading
                )
              }
            }
          }
        ) { squawks, syncs -> squawks to syncs }
      ) { aircraft, logs, taskCards, overview, squawksAndSyncs ->
        val (squawkList, syncStates) = squawksAndSyncs
        cachedLogs = logs
        if (aircraft != null) {
          val stats = if (overview != null) {
            LogStats(
              total = overview.total_log_count.toLong(),
              airframe = overview.airframe_log_count.toLong(),
              engine = overview.engine_log_count.toLong(),
              propeller = overview.propeller_log_count.toLong(),
              avionics = overview.avionics_log_count.toLong(),
              currentAirframeTime = overview.current_airframe_time,
              currentEngineTime = overview.current_engine_time,
              currentPropTime = overview.current_propeller_time
            )
          } else {
            val currentEngineTime =
              logs.filter { it.engine_hour > 0.0 }.maxOfOrNull { it.engine_hour }
            val currentAirframeTime =
              logs.filter { it.airframe_time > 0.0 }.maxOfOrNull { it.airframe_time }
            val currentPropTime = logs.filter { it.prop_time > 0.0 }.maxOfOrNull { it.prop_time }
            LogStats(
              total = logs.size.toLong(),
              airframe = logs.count { it.component_type == ComponentType.COMPONENT_AIRFRAME }
                .toLong(),
              engine = logs.count { it.component_type == ComponentType.COMPONENT_ENGINE }
                .toLong(),
              propeller = logs.count { it.component_type == ComponentType.COMPONENT_PROPELLER }
                .toLong(),
              avionics = logs.count { it.component_type == ComponentType.COMPONENT_AVIONICS }
                .toLong(),
              currentEngineTime = currentEngineTime,
              currentAirframeTime = currentAirframeTime,
              currentPropTime = currentPropTime)
          }

          val cardsWithStatus = taskCards.map { card ->
            MaintenanceTaskWithStatus(
              card = card,
              dueStatus = taskDueManager.computeNextDue(
                card,
                logs,
                taskCards
              ),
            )
          }
          val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
          val currentEngineHours = stats.currentEngineTime ?: 0.0
          val active = cardsWithStatus
            .filter { it.dueStatus.status != DueStatus.COMPLIED }
            .sortedBy { task ->
              val due = task.dueStatus
              if (due.isImmediate) return@sortedBy Long.MIN_VALUE
              val candidates = mutableListOf<Long>()
              due.nextDueDate?.let {
                candidates.add(it.toEpochDays() - today.toEpochDays())
              }
              due.nextDueEngine?.let {
                candidates.add((it.toDouble() - currentEngineHours).toLong())
              }
              candidates.minOrNull() ?: Long.MAX_VALUE
            }
          val complied = cardsWithStatus.filter { it.dueStatus.status == DueStatus.COMPLIED }

          val current = _uiState.value as? AircraftOverviewUiState.Success
          val refreshedSelected = current?.selectedTask?.let { sel ->
            cardsWithStatus.find { it.card.id == sel.card.id }
          }
          val refreshedDetailLogs = refreshedSelected?.let { sel ->
            logs.filter { sel.card.id in it.inspection_ids }
              .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
          } ?: emptyList()

          val hasLegacy = logs.any { log ->
            log.attachments.any { att -> att.sha256.isBlank() && att.storage_path.isNotBlank() }
          }

          val squawksWithStatus = squawkList.map { it.toWithStatus() }
          val aogSquawks = squawkList.filter {
            it.priority == SquawkPriority.SQUAWK_PRIORITY_AOG &&
              it.addressed_by_log_id.isEmpty()
          }

          AircraftOverviewUiState.Success(
            aircraft = aircraft,
            logStats = stats,
            activeTasks = active,
            completedTasks = complied,
            selectedTask = refreshedSelected,
            logsForSelectedTask = refreshedDetailLogs,
            deletingTaskId = current?.deletingTaskId,
            syncStates = syncStates,
            showLegacyAttachmentBanner = hasLegacy && !legacyBannerDismissed,
            squawks = squawksWithStatus,
            aogSquawks = aogSquawks,
          )
        } else {
          AircraftOverviewUiState.Error
        }
      }.collect { state ->
        _uiState.update { state }
      }
    }
  }

  fun onAction(action: AircraftOverviewAction) {
    when (action) {
      AircraftOverviewAction.BackClick -> {
        viewModelScope.launch { _events.send(AircraftOverviewEvent.NavigateBack) }
      }

      is AircraftOverviewAction.EditClick -> {
        viewModelScope.launch { _events.send(AircraftOverviewEvent.NavigateToEditAircraft(action.aircraftId)) }
      }

      AircraftOverviewAction.DeleteConfirm -> {
        deleteAircraft()
      }

      is AircraftOverviewAction.AddLogClick -> {
        viewModelScope.launch { _events.send(AircraftOverviewEvent.NavigateToAddLog(action.aircraftId)) }
      }

      is AircraftOverviewAction.EditLogClick -> {
        viewModelScope.launch {
          _events.send(
            AircraftOverviewEvent.NavigateToEditLog(
              action.aircraftId,
              action.logId
            )
          )
        }
      }

      is AircraftOverviewAction.AddTaskClick -> {
        viewModelScope.launch { _events.send(AircraftOverviewEvent.NavigateToAddTask(action.aircraftId)) }
      }

      is AircraftOverviewAction.TaskCardClick -> {
        showTaskDetails(action.card)
      }

      AircraftOverviewAction.DismissTaskDetail -> {
        hideTaskDetail()
      }

      is AircraftOverviewAction.EditTaskClick -> {
        hideTaskDetail()
        viewModelScope.launch {
          _events.send(
            AircraftOverviewEvent.NavigateToEditTask(
              action.aircraftId,
              action.cardId
            )
          )
        }
      }

      AircraftOverviewAction.CancelDeleteTask -> {
        cancelDeleteTask()
      }

      AircraftOverviewAction.ConfirmDeleteTask -> {
        confirmDeleteTask()
      }

      is AircraftOverviewAction.TaskFromLogClick -> {
        val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
        val card = (state.activeTasks + state.completedTasks)
          .find { it.card.id == action.taskId } ?: return
        showTaskDetails(card)
      }

      AircraftOverviewAction.DismissLegacyBanner -> {
        legacyBannerDismissed = true
        _uiState.update { state ->
          if (state is AircraftOverviewUiState.Success) {
            state.copy(showLegacyAttachmentBanner = false)
          } else state
        }
      }

      is AircraftOverviewAction.AddSquawkClick -> {
        viewModelScope.launch { _events.send(AircraftOverviewEvent.NavigateToAddSquawk(action.aircraftId)) }
      }

      is AircraftOverviewAction.ShowSquawkDetail -> {
        val log = cachedLogs.firstOrNull { it.id == action.squawk.squawk.addressed_by_log_id }
        _uiState.update { state ->
          if (state is AircraftOverviewUiState.Success)
            state.copy(selectedSquawk = action.squawk, logForSelectedSquawk = log)
          else state
        }
      }

      AircraftOverviewAction.DismissSquawkDetail -> {
        _uiState.update { state ->
          if (state is AircraftOverviewUiState.Success)
            state.copy(selectedSquawk = null, logForSelectedSquawk = null)
          else state
        }
      }

      is AircraftOverviewAction.EditSquawkClick -> {
        viewModelScope.launch {
          _events.send(
            AircraftOverviewEvent.NavigateToEditSquawk(
              action.aircraftId,
              action.squawkId
            )
          )
        }
      }
    }
  }

  private fun showTaskDetails(cardWithStatus: MaintenanceTaskWithStatus) {
    val relevantLogs = cachedLogs.filter { cardWithStatus.card.id in it.inspection_ids }
      .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(
          selectedTask = cardWithStatus,
          logsForSelectedTask = relevantLogs,
        )
      } else state
    }
  }

  fun hideTaskDetail() {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(
          selectedTask = null,
          logsForSelectedTask = emptyList()
        )
      } else state
    }
  }

  fun cancelDeleteTask() {
    _uiState.update { state ->
      if (state is AircraftOverviewUiState.Success) {
        state.copy(deletingTaskId = null)
      } else state
    }
  }

  fun confirmDeleteTask() {
    val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
    val cardId = state.deletingTaskId ?: return
    deleteTask(cardId)
  }

  fun deleteTask(cardId: String) {
    val state = _uiState.value as? AircraftOverviewUiState.Success ?: return
    viewModelScope.launch {
      taskDataManager.deleteTask(
        state.aircraft.id,
        cardId
      )
      _uiState.update { s ->
        if (s is AircraftOverviewUiState.Success) {
          s.copy(
            deletingTaskId = null,
            selectedTask = null
          )
        } else s
      }
    }
  }

  fun deleteAircraft() {
    viewModelScope.launch {
      fleetManager.deleteAircraft(aircraftId).onSuccess {
        _events.send(AircraftOverviewEvent.NavigateBack)
      }.onFailure { error ->
        _events.send(
          AircraftOverviewEvent.ShowError(
            error.message
          )
        )
      }
    }
  }
}
