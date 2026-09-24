package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.DataLogOpened
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.datalog.datamanager.ChartLayoutStore
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.ChartLayout
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.LayoutEdits
import dev.fanfly.wingslog.feature.datalog.model.chart.LayoutMemory
import dev.fanfly.wingslog.feature.datalog.model.chart.LayoutMemoryCodec
import dev.fanfly.wingslog.feature.datalog.model.chart.Navigation
import dev.fanfly.wingslog.feature.datalog.model.chart.PaneKind
import dev.fanfly.wingslog.feature.datalog.model.chart.defaultLayout
import dev.fanfly.wingslog.feature.datalog.model.chart.kindOf
import dev.fanfly.wingslog.feature.datalog.model.chart.withMapFirst
import dev.fanfly.wingslog.feature.datalog.viewing.chart.SidebarTab
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
  /**
   * Waiting on the file. [download] is null until the manager reports a state; [reading] is the
   * phase after it, where the bytes are inflated and parsed.
   *
   * The two are named separately because they take different amounts of time for different reasons,
   * and a spinner that says nothing for five seconds on a large file reads as a hang.
   */
  data class Loading(
    val download: DownloadState? = null,
    val reading: Boolean = false,
  ) : DataLogViewerUiState

  data class Ready(
    val record: DataLog,
    val data: DataLogSeriesData,
    val layout: ChartLayout,
    /** Null is the whole log. */
    val view: ViewWindow?,
    /** Elapsed seconds under the cursor, or null. */
    val cursorT: Double?,
    val deleting: Boolean,
    val sidebarTab: SidebarTab = SidebarTab.SERIES,
    val seriesQuery: String = "",
    /** PRD R32: the axis reads the recorder's wall clock instead of elapsed time. */
    val clockAxis: Boolean = false,
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
  private val layouts: ChartLayoutStore,
  private val analytics: AnalyticsManager,
  private val templates: CurrentThingTemplate,
  private val thingId: ThingId,
  private val dataLogId: DataLogId,
) : ViewModel() {

  private val _uiState =
    MutableStateFlow<DataLogViewerUiState>(DataLogViewerUiState.Loading())
  val uiState: StateFlow<DataLogViewerUiState> = _uiState.asStateFlow()

  private val _events =
    MutableSharedFlow<DataLogViewerEvent>(extraBufferCapacity = 1)
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
        view = Navigation.brushToWindow(
          state.view,
          duration,
          intent.x0Px,
          intent.x1Px,
          intent.widthPx
        ),
      )

      is GestureIntent.Pan -> state.copy(
        view = Navigation.pan(
          state.view,
          duration,
          intent.spanFraction * window.lengthSeconds
        ),
      )

      is GestureIntent.Zoom -> state.copy(
        view = Navigation.zoomAround(
          state.view,
          duration,
          intent.anchorFraction,
          intent.factor
        ),
      )

      is GestureIntent.Cursor -> state.copy(
        cursorT = intent.fraction?.let {
          window.startSeconds + it.coerceIn(
            0.0,
            1.0
          ) * window.lengthSeconds
        },
      )

      GestureIntent.Reset -> state.copy(view = null)
    }
  }

  fun setCursor(t: Double?) = updateReady { it.copy(cursorT = t) }

  fun setLayout(layout: ChartLayout) = updateReady { it.withLayout(layout) }

  /**
   * Every layout edit goes through here so the map pane stays first (design §11.6): the edits
   * themselves are pure and order-blind, and re-sorting in one place beats remembering to do it in
   * each of them.
   */
  private fun DataLogViewerUiState.Ready.withLayout(layout: ChartLayout): DataLogViewerUiState.Ready =
    copy(layout = layout.withMapFirst(catalogue()))

  private fun DataLogViewerUiState.Ready.catalogue(): Map<Int, DataLogSeries> =
    record.series.associateBy { it.column }

  fun toggleClockAxis() = updateReady { it.copy(clockAxis = !it.clockAxis) }

  // Layout edits (design §11.5, PRD R21, R24): each is a pure LayoutEdits call on the Ready state.

  fun setTargetPane(pane: PaneId) =
    updateReady { it.withLayout(LayoutEdits.target(it.layout, pane)) }

  fun addSeries(pane: PaneId, key: SeriesKey) = updateReady {
    it.withLayout(LayoutEdits.place(it.layout, pane, key, it.catalogue()))
  }

  fun removeSeries(pane: PaneId, key: SeriesKey) =
    updateReady { it.withLayout(LayoutEdits.remove(it.layout, pane, key)) }

  /**
   * What a tap in the series list means: a series already in [pane] comes out, anything else goes
   * in. Adding one that is already there is a no-op, so without this the checked row ate its tap
   * and the only way to drop a series was its chip's close button.
   */
  fun toggleSeries(pane: PaneId, key: SeriesKey) = updateReady { state ->
    val catalogue = state.catalogue()
    // Against the pane it would land in, not the one that was tapped: the position series always
    // lands on the map pane, so that is the pane whose tap must take it back out.
    val destination =
      with(LayoutEdits) { state.layout.destinationFor(pane, key, catalogue) }
    val present = destination != null &&
      state.layout.panes.firstOrNull { it.id == destination }?.series?.contains(
        key
      ) == true
    val layout = if (present) {
      val removed = LayoutEdits.remove(state.layout, destination, key)
      // The map pane exists only to hold the map, so emptying it closes it rather than leaving a
      // pane and a half of blank behind. A chart pane stays: it is still somewhere to drop a series.
      val emptied =
        removed.panes.firstOrNull { it.id == destination }?.series?.isEmpty() == true
      val wasMap = state.layout.kindOf(destination, catalogue) == PaneKind.MAP
      if (emptied && wasMap) LayoutEdits.removePane(
        removed,
        destination
      ) else removed
    } else {
      LayoutEdits.place(state.layout, pane, key, catalogue)
    }
    state.withLayout(layout)
  }

  fun moveSeries(key: SeriesKey, from: PaneId, to: PaneId) = updateReady {
    it.withLayout(LayoutEdits.move(it.layout, key, from, to, it.catalogue()))
  }

  /** The *New pane* target: a dropped series lands in a fresh pane; a tap opens an empty one. */
  fun spawnPane(key: SeriesKey? = null) =
    updateReady { it.withLayout(LayoutEdits.spawn(it.layout, key)) }

  fun setSidebarTab(tab: SidebarTab) = updateReady { it.copy(sidebarTab = tab) }

  fun setSeriesQuery(query: String) =
    updateReady { it.copy(seriesQuery = query) }

  fun removePane(pane: PaneId) =
    updateReady { it.withLayout(LayoutEdits.removePane(it.layout, pane)) }

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
      val record = manager.observeOne(thingId, dataLogId)
        .first()
      if (record == null) {
        _uiState.value = DataLogViewerUiState.Failed(LoadFailure.NOT_FOUND)
        return@launch
      }
      val terminal = manager.ensureLocal(thingId, dataLogId)
        .onEach { state ->
          if (state is DownloadState.Downloading) _uiState.value =
            DataLogViewerUiState.Loading(state)
        }
        .first { it !is DownloadState.Downloading }
      if (terminal is DownloadState.Failed) {
        logger.w(terminal.error) { "Data log download failed" }
        _uiState.value =
          DataLogViewerUiState.Failed(LoadFailure.DOWNLOAD_FAILED)
        return@launch
      }
      // PRD R31: what this device last arranged for this log, re-resolved against its series.
      val remembered = layouts.load(dataLogId)
        ?.let { LayoutMemoryCodec.decode(it, record.series) }
      // Announce the phase before the work starts. Nothing has to be done to let the frame be
      // drawn: reading the file and inflating it both suspend, and the parse hands the thread back
      // within a frame of starting, so the browser gets its turn either way.
      _uiState.value = DataLogViewerUiState.Loading(reading = true)
      manager.load(thingId, dataLogId)
        .onSuccess { data ->
          // Re-read the record: loading rewrites a catalogue the parser has outgrown, and the copy
          // read above was taken before that write. Without this the sidebar spends the whole
          // session showing the ranges the fix was meant to replace.
          val current = manager.observeOne(thingId, dataLogId)
            .first() ?: record
          // Design §13.1: one event per successful open, after the parse, so a failed load is not
          // counted as a view. A retry logs again, which is what "opened" means here.
          analytics.log(
            DataLogOpened(
              templateId = templates.templateId,
              durationSeconds = current.duration_seconds,
              seriesCount = current.series.size,
            )
          )
          _uiState.value = DataLogViewerUiState.Ready(
            record = current,
            data = data,
            layout = (remembered?.layout ?: defaultLayout(current.series))
              .withMapFirst(current.series.associateBy { it.column }),
            view = null,
            cursorT = null,
            deleting = false,
            clockAxis = remembered?.clockAxis == true,
          )
        }
        .onFailure {
          _uiState.value = DataLogViewerUiState.Failed(LoadFailure.PARSE_FAILED)
        }
    }
  }

  private inline fun updateReady(transform: (DataLogViewerUiState.Ready) -> DataLogViewerUiState.Ready) {
    _uiState.update { state ->
      if (state !is DataLogViewerUiState.Ready) return@update state
      transform(state).also { next -> remember(state, next) }
    }
  }

  /**
   * Writes the arrangement this device restores next time (PRD R31) — only when it actually
   * changed, because a cursor move runs through here on every pointer frame.
   */
  private fun remember(
    before: DataLogViewerUiState.Ready,
    after: DataLogViewerUiState.Ready,
  ) {
    if (before.layout == after.layout && before.clockAxis == after.clockAxis) return
    layouts.save(
      dataLogId,
      LayoutMemoryCodec.encode(
        LayoutMemory(
          after.layout,
          after.clockAxis
        )
      )
    )
  }

  private companion object {
    val logger = Logger.withTag("DataLogViewer")
  }
}
