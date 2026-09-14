package dev.fanfly.wingslog.feature.datalog.update.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.Navigation
import dev.fanfly.wingslog.feature.datalog.model.chart.defaultLayout
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoadFailure { NOT_FOUND, DOWNLOAD_FAILED, PARSE_FAILED }

sealed interface DataLogViewerUiState {
  /** Waiting on the bytes: [download] is null until the manager reports a state. */
  data class Loading(val download: DownloadState? = null) : DataLogViewerUiState

  data class Ready(
    val record: DataLog,
    val data: DataLogSeriesData,
    val layout: ChartLayout,
    /** Null is the whole log. */
    val view: ViewWindow?,
    /** Elapsed seconds under the cursor, or null. */
    val cursorT: Double?,
    val deleting: Boolean,
  ) : DataLogViewerUiState

  data class Failed(val reason: LoadFailure) : DataLogViewerUiState
}

sealed interface DataLogViewerEvent {
  data object Deleted : DataLogViewerEvent
  data object DeleteFailed : DataLogViewerEvent
}

/**
 * Owns everything the user can change in the viewer (design §11.1). Loads through
 * [DataLogManager.ensureLocal] then [DataLogManager.load]; the panes, gestures and layout memory
 * arrive with T27 to T40 and edit the state held here.
 */
class DataLogViewerViewModel(
  private val manager: DataLogManager,
  private val thingId: ThingId,
  private val dataLogId: DataLogId,
) : ViewModel() {

  private val _uiState = MutableStateFlow<DataLogViewerUiState>(DataLogViewerUiState.Loading())
  val uiState: StateFlow<DataLogViewerUiState> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<DataLogViewerEvent>(extraBufferCapacity = 1)
  val events: SharedFlow<DataLogViewerEvent> = _events

  init {
    load()
  }

  fun retry() {
    _uiState.value = DataLogViewerUiState.Loading()
    load()
  }

  fun setView(view: ViewWindow?) = updateReady { it.copy(view = view) }

  /** Applies a pane gesture to the shared time domain (design §11.4); every pane sees the result. */
  fun onGesture(intent: GestureIntent) = updateReady { state ->
    val duration = state.record.duration_seconds
    val window = Navigation.effective(state.view, duration)
    when (intent) {
      is GestureIntent.Brush -> state.copy(
        view = Navigation.brushToWindow(state.view, duration, intent.x0Px, intent.x1Px, intent.widthPx),
      )
      is GestureIntent.Pan -> state.copy(
        view = Navigation.pan(state.view, duration, intent.spanFraction * window.lengthSeconds),
      )
      is GestureIntent.Zoom -> state.copy(
        view = Navigation.zoomAround(state.view, duration, intent.anchorFraction, intent.factor),
      )
      is GestureIntent.Cursor -> state.copy(
        cursorT = intent.fraction?.let { window.startSeconds + it.coerceIn(0.0, 1.0) * window.lengthSeconds },
      )
      GestureIntent.Reset -> state.copy(view = null)
    }
  }

  fun setCursor(t: Double?) = updateReady { it.copy(cursorT = t) }

  fun setLayout(layout: ChartLayout) = updateReady { it.copy(layout = layout) }

  fun requestDelete() = updateReady { it.copy(deleting = true) }

  fun cancelDelete() = updateReady { it.copy(deleting = false) }

  fun confirmDelete() {
    updateReady { it.copy(deleting = false) }
    viewModelScope.launch {
      manager.delete(thingId, dataLogId)
        .onSuccess { _events.tryEmit(DataLogViewerEvent.Deleted) }
        .onFailure { _events.tryEmit(DataLogViewerEvent.DeleteFailed) }
    }
  }

  private fun load() {
    viewModelScope.launch {
      val record = manager.observeOne(thingId, dataLogId).first()
      if (record == null) {
        _uiState.value = DataLogViewerUiState.Failed(LoadFailure.NOT_FOUND)
        return@launch
      }
      val terminal = manager.ensureLocal(thingId, dataLogId)
        .onEach { state -> if (state is DownloadState.Downloading) _uiState.value = DataLogViewerUiState.Loading(state) }
        .first { it !is DownloadState.Downloading }
      if (terminal is DownloadState.Failed) {
        logger.w(terminal.error) { "Data log download failed" }
        _uiState.value = DataLogViewerUiState.Failed(LoadFailure.DOWNLOAD_FAILED)
        return@launch
      }
      manager.load(thingId, dataLogId)
        .onSuccess { data ->
          _uiState.value = DataLogViewerUiState.Ready(
            record = record, data = data, layout = defaultLayout(record.series), view = null, cursorT = null, deleting = false,
          )
        }
        .onFailure { _uiState.value = DataLogViewerUiState.Failed(LoadFailure.PARSE_FAILED) }
    }
  }

  private inline fun updateReady(transform: (DataLogViewerUiState.Ready) -> DataLogViewerUiState.Ready) {
    _uiState.update { state -> if (state is DataLogViewerUiState.Ready) transform(state) else state }
  }

  private companion object {
    val logger = Logger.withTag("DataLogViewer")
  }
}
