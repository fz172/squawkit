package dev.fanfly.wingslog.feature.datalog.update.viewer

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.datamanager.ChartLayoutStore
import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSeriesKind
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.PaneId
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.update.analytics.RecordingAnalytics
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DataLogViewerViewModelTest {

  private val thingId = ThingId("thing-1")
  private val id = DataLogId("dl-1")
  private val record =
    DataLog(id = id, file_name = "x.csv", duration_seconds = 3600)
  private val data =
    DataLogSeriesData(IntArray(3), emptyMap(), emptyMap(), null)
  private lateinit var manager: DataLogManager
  private lateinit var layouts: ChartLayoutStore
  private lateinit var analytics: RecordingAnalytics
  private lateinit var templates: CurrentThingTemplate
  private var remembered: String? = null

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    manager = mockk()
    layouts = mockk()
    analytics = RecordingAnalytics()
    templates = mockk()
    every { templates.templateId } returns "airplane"
    every { layouts.load(id) } answers { remembered }
    every { layouts.save(id, any()) } answers { remembered = secondArg() }
    every { manager.observeOne(thingId, id) } returns flowOf(record)
    every {
      manager.ensureLocal(
        thingId,
        id
      )
    } returns flowOf(DownloadState.Done)
    coEvery { manager.load(thingId, id) } returns Result.success(data)
    coEvery { manager.delete(thingId, id) } returns Result.success(Unit)
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() =
    DataLogViewerViewModel(manager, layouts, analytics, templates, thingId, id)

  private val engineCatalogue = listOf(
    DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = CanonicalSeries.engine(1, "rpm")),
    DataLogSeries(column = 2, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = CanonicalSeries.engine(1, "oil_temp")),
    DataLogSeries(column = 3, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = CanonicalSeries.IAS),
  )

  private fun ready(vm: DataLogViewerViewModel) = vm.uiState.value as DataLogViewerUiState.Ready

  @Test
  fun theLayoutThisDeviceLeftIsWhatTheNextOpenRestores() = runTest {
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = engineCatalogue))

    val first = viewModel()
    first.addSeries(PaneId(0), SeriesKey(3))
    first.toggleClockAxis()
    assertThat(remembered).isNotNull()

    val reopened = ready(viewModel())
    assertThat(reopened.layout.panes.single().series)
      .containsExactly(SeriesKey(1), SeriesKey(3)).inOrder()
    assertThat(reopened.clockAxis).isTrue()
  }

  @Test
  fun aRememberedLayoutNamingSeriesTheLogLostFallsBackToTheDefault() = runTest {
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = engineCatalogue))
    remembered = "v1;c=0;t=0;p=41,42"

    val state = ready(viewModel())

    assertThat(state.layout.panes.single().series).containsExactly(SeriesKey(1))
    assertThat(state.clockAxis).isFalse()
  }

  @Test
  fun movingTheCursorNeverRewritesTheRememberedLayout() = runTest {
    // updateReady runs on every pointer frame; only a real layout or axis change may hit the store.
    val vm = viewModel()
    vm.toggleClockAxis()
    val afterToggle = remembered

    vm.setCursor(12.0)
    vm.setCursor(13.0)
    vm.setCursor(null)

    assertThat(remembered).isEqualTo(afterToggle)
    verify(exactly = 1) { layouts.save(id, any()) }
  }

  @Test
  fun theClockAxisTogglesAndIsRemembered() = runTest {
    val vm = viewModel()
    assertThat(ready(vm).clockAxis).isFalse()

    vm.toggleClockAxis()
    assertThat(ready(vm).clockAxis).isTrue()
    assertThat(remembered).contains("c=1")

    vm.toggleClockAxis()
    assertThat(ready(vm).clockAxis).isFalse()
    assertThat(remembered).contains("c=0")
  }

  @Test
  fun localBytesLoadStraightToReady() = runTest {
    val state = viewModel().uiState.value as DataLogViewerUiState.Ready
    assertThat(state.record).isEqualTo(record)
    assertThat(state.data).isSameInstanceAs(data)
    assertThat(state.view).isNull()
    assertThat(state.deleting).isFalse()
  }

  @Test
  fun aRemoteFileShowsDownloadProgressThenLoads() = runTest {
    val download =
      MutableStateFlow<DownloadState>(DownloadState.Downloading(0f))
    every { manager.ensureLocal(thingId, id) } returns download
    val vm = viewModel()

    assertThat(vm.uiState.value).isEqualTo(
      DataLogViewerUiState.Loading(
        DownloadState.Downloading(0f)
      )
    )
    download.value = DownloadState.Done
    assertThat(vm.uiState.value).isInstanceOf(DataLogViewerUiState.Ready::class.java)
  }

  @Test
  fun eachFailureHasItsOwnReason() = runTest {
    every { manager.observeOne(thingId, id) } returns flowOf(null)
    assertThat(viewModel().uiState.value).isEqualTo(
      DataLogViewerUiState.Failed(
        LoadFailure.NOT_FOUND
      )
    )

    every { manager.observeOne(thingId, id) } returns flowOf(record)
    every {
      manager.ensureLocal(
        thingId,
        id
      )
    } returns flowOf(DownloadState.Failed(Exception("gone")))
    assertThat(viewModel().uiState.value).isEqualTo(
      DataLogViewerUiState.Failed(
        LoadFailure.DOWNLOAD_FAILED
      )
    )

    every {
      manager.ensureLocal(
        thingId,
        id
      )
    } returns flowOf(DownloadState.Done)
    coEvery { manager.load(thingId, id) } returns Result.failure(
      IllegalStateException("corrupt")
    )
    val vm = viewModel()
    assertThat(vm.uiState.value).isEqualTo(
      DataLogViewerUiState.Failed(
        LoadFailure.PARSE_FAILED
      )
    )

    coEvery { manager.load(thingId, id) } returns Result.success(data)
    vm.retry()
    assertThat(vm.uiState.value).isInstanceOf(DataLogViewerUiState.Ready::class.java)
  }

  @Test
  fun deleteAsksThenPopsWithAnEvent() = runTest {
    val vm = viewModel()
    val events = mutableListOf<DataLogViewerEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

    vm.requestDelete()
    assertThat((vm.uiState.value as DataLogViewerUiState.Ready).deleting).isTrue()
    vm.cancelDelete()
    assertThat((vm.uiState.value as DataLogViewerUiState.Ready).deleting).isFalse()
    assertThat(events).isEmpty()

    vm.requestDelete()
    vm.confirmDelete()
    assertThat(events).containsExactly(DataLogViewerEvent.Deleted)

    coEvery { manager.delete(thingId, id) } returns Result.failure(
      IllegalStateException("offline")
    )
    vm.confirmDelete()
    assertThat(events).containsExactly(
      DataLogViewerEvent.Deleted,
      DataLogViewerEvent.DeleteFailed
    )
  }

  @Test
  fun gesturesMoveTheSharedTimeDomain() = runTest {
    val vm = viewModel()
    fun ready() = vm.uiState.value as DataLogViewerUiState.Ready

    // Brush 25%..50% of the full log.
    vm.onGesture(GestureIntent.Brush(250f, 500f, 1000))
    assertThat(ready().view).isEqualTo(ViewWindow(900, 1800))
    // Pan by a tenth of the visible span.
    vm.onGesture(GestureIntent.Pan(0.1))
    assertThat(ready().view).isEqualTo(ViewWindow(990, 1890))
    // Zoom in twice around the centre.
    vm.onGesture(GestureIntent.Zoom(0.5, 2.0))
    assertThat(ready().view).isEqualTo(ViewWindow(1215, 1665))
    // The cursor lands inside the visible window.
    vm.onGesture(GestureIntent.Cursor(0.5))
    assertThat(ready().cursorT).isWithin(0.01)
      .of(1440.0)
    vm.onGesture(GestureIntent.Cursor(null))
    assertThat(ready().cursorT).isNull()
    // Reset returns to the whole log, and pan at full zoom-out changes nothing.
    vm.onGesture(GestureIntent.Reset)
    assertThat(ready().view).isNull()
    vm.onGesture(GestureIntent.Pan(0.5))
    assertThat(ready().view).isNull()
  }

  @Test
  fun gesturesAreIgnoredUntilReady() = runTest {
    every { manager.observeOne(thingId, id) } returns flowOf(null)
    val vm = viewModel()
    vm.onGesture(GestureIntent.Zoom(0.5, 2.0))
    assertThat(vm.uiState.value).isEqualTo(
      DataLogViewerUiState.Failed(
        LoadFailure.NOT_FOUND
      )
    )
  }

  @Test
  fun tappingASeriesAlreadyInTheTargetPaneTakesItOut() = runTest {
    val catalogue = listOf(
      DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = "engine[1].rpm"),
      DataLogSeries(column = 2, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC),
    )
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = catalogue))
    val vm = viewModel()
    fun layout() = (vm.uiState.value as DataLogViewerUiState.Ready).layout

    // Opens on RPM; tapping it again in the list takes it back out rather than doing nothing.
    assertThat(layout().panes.single().series).containsExactly(SeriesKey(1))
    vm.toggleSeries(PaneId(0), SeriesKey(1))
    assertThat(layout().panes.single().series).isEmpty()

    // A series the pane does not hold still goes in, and can come straight back out.
    vm.toggleSeries(PaneId(0), SeriesKey(2))
    assertThat(layout().panes.single().series).containsExactly(SeriesKey(2))
    vm.toggleSeries(PaneId(0), SeriesKey(2))
    assertThat(layout().panes.single().series).isEmpty()
  }

  @Test
  fun aSeriesInAnotherPaneIsAddedToTheTargetRatherThanRemoved() = runTest {
    val catalogue = listOf(
      DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = "engine[1].rpm"),
      DataLogSeries(column = 2, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC),
    )
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = catalogue))
    val vm = viewModel()
    fun layout() = (vm.uiState.value as DataLogViewerUiState.Ready).layout

    vm.spawnPane()
    // RPM sits in pane 0; the target is pane 1, so the same tap adds it there.
    vm.toggleSeries(PaneId(1), SeriesKey(1))

    assertThat(layout().panes[0].series).containsExactly(SeriesKey(1))
    assertThat(layout().panes[1].series).containsExactly(SeriesKey(1))
  }

  @Test
  fun thePositionSeriesGetsItsOwnPaneAndNeverASecondOne() = runTest {
    val catalogue = listOf(
      DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = "engine[1].rpm"),
      DataLogSeries(column = 2, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC),
      DataLogSeries(column = 9, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION),
    )
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = catalogue))
    val vm = viewModel()
    fun layout() = (vm.uiState.value as DataLogViewerUiState.Ready).layout

    // Tapping position while a chart pane is the target opens a map pane rather than joining it.
    vm.toggleSeries(PaneId(0), SeriesKey(9))
    assertThat(layout().panes.map { it.series }).containsExactly(
      listOf(SeriesKey(9)),
      listOf(SeriesKey(1)),
    ).inOrder()

    // Tapping it again from a chart pane takes it out instead of opening a second map pane.
    vm.toggleSeries(PaneId(0), SeriesKey(9))
    assertThat(layout().panes.map { it.series }).containsExactly(listOf(SeriesKey(1)))
  }

  @Test
  fun noChartSeriesEverJoinsTheMapPane() = runTest {
    val catalogue = listOf(
      DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = "engine[1].rpm"),
      DataLogSeries(column = 2, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC),
      DataLogSeries(column = 9, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION),
    )
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = catalogue))
    val vm = viewModel()
    fun layout() = (vm.uiState.value as DataLogViewerUiState.Ready).layout

    vm.toggleSeries(PaneId(0), SeriesKey(9))
    val mapPane = layout().panes.first().id

    // Aimed straight at the map pane, by tap and by drag: both land in a chart pane instead.
    vm.addSeries(mapPane, SeriesKey(2))
    assertThat(layout().panes.first { it.id == mapPane }.series).containsExactly(SeriesKey(9))
    assertThat(layout().panes.flatMap { it.series }).contains(SeriesKey(2))

    vm.moveSeries(SeriesKey(1), PaneId(0), mapPane)
    assertThat(layout().panes.first { it.id == mapPane }.series).containsExactly(SeriesKey(9))
    assertThat(layout().panes.first().id).isEqualTo(mapPane)
  }

  @Test
  fun aMapPaneAddedLastStillOpensFirst() = runTest {
    val catalogue = listOf(
      DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = "engine[1].rpm"),
      DataLogSeries(column = 9, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION),
    )
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = catalogue))
    val vm = viewModel()
    fun layout() = (vm.uiState.value as DataLogViewerUiState.Ready).layout

    // Opens on RPM alone; the position series spawns a map pane at the end of the list.
    assertThat(layout().panes.single().series).containsExactly(SeriesKey(1))
    vm.spawnPane(SeriesKey(9))

    assertThat(layout().panes.map { it.series }).containsExactly(
      listOf(SeriesKey(9)),
      listOf(SeriesKey(1)),
    ).inOrder()
    // Re-ordering does not steal the target from the pane the user just made.
    assertThat(layout().targetPane).isEqualTo(layout().panes.first().id)
  }

  @Test
  fun layoutEditsFlowThroughTheReadyState() = runTest {
    val catalogue = listOf(
      DataLogSeries(column = 1, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC, canonical_id = "engine[1].rpm"),
      DataLogSeries(column = 2, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_NUMERIC),
      DataLogSeries(column = 3, kind = DataLogSeriesKind.DATA_LOG_SERIES_KIND_POSITION),
    )
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = catalogue))
    val vm = viewModel()
    fun layout() = (vm.uiState.value as DataLogViewerUiState.Ready).layout

    // Opens on RPM in pane 0, the target.
    assertThat(layout().panes.single().series).containsExactly(SeriesKey(1))
    assertThat(layout().targetPane).isEqualTo(PaneId(0))

    vm.addSeries(PaneId(0), SeriesKey(2))
    assertThat(layout().panes[0].series).containsExactly(SeriesKey(1), SeriesKey(2)).inOrder()

    vm.spawnPane()
    assertThat(layout().panes).hasSize(2)
    assertThat(layout().targetPane).isEqualTo(PaneId(1))

    vm.moveSeries(SeriesKey(2), PaneId(0), PaneId(1))
    assertThat(layout().panes[0].series).containsExactly(SeriesKey(1))
    assertThat(layout().panes[1].series).containsExactly(SeriesKey(2))

    // The position series opens a pane of its own, which leads the stack; dragging it onto a chart
    // pane afterwards changes nothing, because that one pane is the only place it can be.
    vm.addSeries(PaneId(1), SeriesKey(3))
    vm.moveSeries(SeriesKey(3), PaneId(1), PaneId(0))
    assertThat(layout().panes).hasSize(3)
    assertThat(layout().panes.first().series).containsExactly(SeriesKey(3))
    assertThat(layout().panes.map { it.id })
      .containsExactly(PaneId(2), PaneId(0), PaneId(1)).inOrder()

    vm.removeSeries(PaneId(0), SeriesKey(1))
    assertThat(layout().panes.first { it.id == PaneId(0) }.series).isEmpty()
    vm.setTargetPane(PaneId(0))
    vm.removePane(PaneId(0))
    assertThat(layout().panes.map { it.id }).containsExactly(PaneId(2), PaneId(1)).inOrder()
    assertThat(layout().targetPane).isEqualTo(PaneId(1))
  }

  @Test
  fun aSuccessfulOpenIsLoggedOnceWithTheLogsShape() = runTest {
    every { manager.observeOne(thingId, id) } returns flowOf(record.copy(series = engineCatalogue))

    viewModel()

    assertThat(analytics.events).containsExactly(
      "data_log_opened" to mapOf(
        "template_id" to "airplane",
        "duration_bucket" to "1-3h",
        "series_count" to "3",
      )
    )
  }

  @Test
  fun theRecordIsReReadAfterLoadingSoARewrittenCatalogueIsWhatTheSidebarSees() = runTest {
    // Loading rewrites a catalogue the parser has outgrown. The copy read before that write is the
    // one the fix was meant to replace, so the viewer must not keep it for the session.
    val refreshed = record.copy(series = engineCatalogue, duration_seconds = 7200)
    every { manager.observeOne(thingId, id) } returnsMany listOf(
      flowOf(record),
      flowOf(refreshed),
    )

    val state = ready(viewModel())

    assertThat(state.record.series).hasSize(3)
    assertThat(state.record.duration_seconds).isEqualTo(7200)
  }

  @Test
  fun aFailedLoadIsNotAnOpen() = runTest {
    coEvery { manager.load(thingId, id) } returns Result.failure(IllegalStateException("bad csv"))

    viewModel()

    assertThat(analytics.events).isEmpty()
  }
}
