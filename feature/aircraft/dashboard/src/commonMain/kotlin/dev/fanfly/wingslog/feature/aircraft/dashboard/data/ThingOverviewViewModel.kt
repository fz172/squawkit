package dev.fanfly.wingslog.feature.aircraft.dashboard.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentOpener
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.squawk.model.openAog
import dev.fanfly.wingslog.feature.squawk.model.toWithStatus
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** The share-related flows, combined so they fit in one slot of the outer [combine]. */
private data class ShareContext(
  val squawks: List<Squawk>,
  val syncStates: Map<String, BlobSyncState>,
  val myRole: ShareRole?,
  val shared: Boolean,
)

class AircraftOverviewViewModel(
  private val fleetManager: FleetManager,
  private val logManager: MaintenanceLogManager,
  private val taskDataManager: TaskDataManager,
  private val taskDueManager: TaskDueManager,
  private val attachmentOpener: AttachmentOpener,
  private val attachmentManager: AttachmentManager,
  private val squawkManager: SquawkManager,
  private val sharingManager: SharingManager,
  private val aircraftScopeResolver: AircraftScopeResolver,
  private val auth: FirebaseAuth,
  private val aircraftId: String,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow<AircraftOverviewUiState>(AircraftOverviewUiState.Loading)
  val uiState: StateFlow<AircraftOverviewUiState> = _uiState.asStateFlow()

  private val _events = Channel<AircraftOverviewEvent>()
  private var cachedLogs: List<MaintenanceLog> = emptyList()

  /**
   * Bumped when the screen resumes, to re-run due-status computation against a fresh clock.
   *
   * [dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager] compares due dates against
   * `Clock.System`, so a card's status is a function of the stored data *and* the current time —
   * but the store flows only re-emit when data changes. Without this, an app backgrounded overnight
   * comes back still rendering yesterday's NORMAL for a card that is now DUE_SOON.
   */
  private val resumeTick = MutableStateFlow(0)

  /** Call when the dashboard becomes visible again; see [resumeTick]. */
  fun onResumed() {
    resumeTick.value++
  }

  init {
    loadAircraftAndStats()
  }

  // Blob sync state must be observed at the scope that actually holds this aircraft's data: the
  // caller's own tree for an owned plane, or the host's tree for a shared one. Deriving the path
  // from the uid alone (the old `/users/$uid/aircraft/...`) missed a member's shared aircraft
  // entirely, so sync state never resolved. Drive it off [AircraftScopeResolver] instead, which
  // re-emits when the aircraft flips own ↔ shared. See docs/sharing §6.3 and P8.3 (#244).
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun blobStatesFlow(): Flow<Map<String, BlobSyncState>> =
    aircraftScopeResolver.resolve(aircraftId)
      .flatMapLatest { scope ->
        if (scope == null) flowOf(emptyMap())
        else attachmentManager.observeBlobStates(scope.toPath())
      }

  private fun loadAircraftAndStats() {
    viewModelScope.launch {
      _uiState.update { AircraftOverviewUiState.Loading }
      // Every collection shares one SQLDelight `entity` table, and SQLDelight notifies query
      // listeners per *table*, so any write anywhere — adding a squawk, a sync writeback — re-runs
      // and re-emits every observer here with identical content. Unfiltered, that re-ran
      // computeNextDue for every task card several times per unrelated write. distinctUntilChanged
      // drops the duplicates; the store still re-queries and re-decodes, which is a separate
      // (larger) fix at the EntityStore level.
      combine(
        fleetManager.loadAircraft(aircraftId)
          .distinctUntilChanged(),
        logManager.observeLogs(aircraftId)
          .distinctUntilChanged(),
        // The resume tick rides along with the tasks flow rather than occupying a combine slot of
        // its own: `combine` tops out at five typed sources, and re-emitting the task list is
        // exactly what re-runs computeNextDue below. distinctUntilChanged sits *upstream* of the
        // tick, so a resume still gets through.
        combine(
          taskDataManager.observeTasks(aircraftId)
            .distinctUntilChanged(),
          resumeTick,
        ) { tasks, _ -> tasks },
        logManager.observeMaintenanceOverview(aircraftId)
          .distinctUntilChanged(),
        combine(
          squawkManager.observeSquawks(aircraftId)
            .distinctUntilChanged(),
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
          }.distinctUntilChanged(),
          // The caller's role on this aircraft, resolved locally (own ⇒ OWNER, shared ⇒ ref role).
          // Gates owner-only affordances in the UI; server rules remain the real enforcement (§6.3).
          sharingManager.observeMyRole(aircraftId)
            .distinctUntilChanged(),
          sharingManager.observeIsShared(aircraftId)
            .distinctUntilChanged(),
        ) { squawks, syncs, myRole, shared ->
          ShareContext(squawks, syncs, myRole, shared)
        }
      ) { aircraft, logs, taskCards, overview, shareContext ->
        val (squawkList, syncStates, myRole, isShared) = shareContext
        cachedLogs = logs
        if (aircraft != null) {
          val stats = if (overview != null) {
            LogStats(
              total = overview.total_log_count.toLong(),
              airframe = overview.airframe_log_count.toLong(),
              engine = overview.engine_log_count.toLong(),
              propeller = overview.propeller_log_count.toLong(),
              currentAirframeTime = overview.current_airframe_time,
              currentEngineTime = overview.current_engine_time,
              currentPropTime = overview.current_propeller_time
            )
          } else {
            val currentEngineTime =
              logs.filter { it.engine_hour > 0.0 }
                .maxOfOrNull { it.engine_hour }
            val currentAirframeTime =
              logs.filter { it.airframe_time > 0.0 }
                .maxOfOrNull { it.airframe_time }
            val currentPropTime = logs.filter { it.prop_time > 0.0 }
              .maxOfOrNull { it.prop_time }
            LogStats(
              total = logs.size.toLong(),
              airframe = logs.count { it.component_type == ComponentType.COMPONENT_AIRFRAME }
                .toLong(),
              engine = logs.count { it.component_type == ComponentType.COMPONENT_ENGINE }
                .toLong(),
              propeller = logs.count { it.component_type == ComponentType.COMPONENT_PROPELLER }
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
          val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
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
          val complied =
            cardsWithStatus.filter { it.dueStatus.status == DueStatus.COMPLIED }

          val current = _uiState.value as? AircraftOverviewUiState.Success
          val refreshedSelected = current?.selectedTask?.let { sel ->
            cardsWithStatus.find { it.card.id == sel.card.id }
          }
          val refreshedDetailLogs = refreshedSelected?.let { sel ->
            logs.filter { sel.card.id in it.inspection_ids }
              .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
          } ?: emptyList()

          val squawksWithStatus = squawkList.map { it.toWithStatus() }
          val aogSquawks = squawksWithStatus.openAog()

          AircraftOverviewUiState.Success(
            aircraft = aircraft,
            logStats = stats,
            activeTasks = active,
            completedTasks = complied,
            recentLogs = logs.sortedByDescending {
              it.timestamp?.getEpochSecond() ?: 0L
            }
              .take(4),
            selectedTask = refreshedSelected,
            logsForSelectedTask = refreshedDetailLogs,
            deletingTaskId = current?.deletingTaskId,
            syncStates = syncStates,
            squawks = squawksWithStatus,
            aogSquawks = aogSquawks,
            myRole = myRole,
            shared = isShared,
            isAnonymous = auth.currentUser?.isAnonymous ?: true,
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
        viewModelScope.launch {
          _events.send(
            AircraftOverviewEvent.NavigateToEditAircraft(
              action.aircraftId
            )
          )
        }
      }

      AircraftOverviewAction.DeleteConfirm -> {
        deleteAircraft()
      }

      is AircraftOverviewAction.ManageAccessClick -> {
        viewModelScope.launch {
          _events.send(
            AircraftOverviewEvent.NavigateToManageAccess(
              action.aircraftId
            )
          )
        }
      }

      is AircraftOverviewAction.AddLogClick -> {
        viewModelScope.launch {
          _events.send(
            AircraftOverviewEvent.NavigateToAddLog(
              action.aircraftId
            )
          )
        }
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
        viewModelScope.launch {
          _events.send(
            AircraftOverviewEvent.NavigateToAddTask(
              action.aircraftId
            )
          )
        }
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

      is AircraftOverviewAction.AddSquawkClick -> {
        viewModelScope.launch {
          _events.send(
            AircraftOverviewEvent.NavigateToAddSquawk(
              action.aircraftId
            )
          )
        }
      }

      is AircraftOverviewAction.ShowSquawkDetail -> {
        val log =
          cachedLogs.firstOrNull { it.id == action.squawk.squawk.addressed_by_log_id }
        _uiState.update { state ->
          if (state is AircraftOverviewUiState.Success)
            state.copy(
              selectedSquawk = action.squawk,
              logForSelectedSquawk = log
            )
          else state
        }
      }

      AircraftOverviewAction.DismissSquawkDetail -> {
        _uiState.update { state ->
          if (state is AircraftOverviewUiState.Success)
            state.copy(
              selectedSquawk = null,
              logForSelectedSquawk = null
            )
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
    val relevantLogs =
      cachedLogs.filter { cardWithStatus.card.id in it.inspection_ids }
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
      fleetManager.deleteAircraft(aircraftId)
        .onSuccess {
          _events.send(AircraftOverviewEvent.NavigateBack)
        }
        .onFailure { error ->
          _events.send(
            AircraftOverviewEvent.ShowError(
              error.message
            )
          )
        }
    }
  }
}
