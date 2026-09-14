package dev.fanfly.wingslog.feature.datalog.update.viewer

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.GestureIntent
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
  private val record = DataLog(id = id, file_name = "x.csv", duration_seconds = 3600)
  private val data = DataLogSeriesData(IntArray(3), emptyMap(), emptyMap(), null)
  private lateinit var manager: DataLogManager

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    manager = mockk()
    every { manager.observeOne(thingId, id) } returns flowOf(record)
    every { manager.ensureLocal(thingId, id) } returns flowOf(DownloadState.Done)
    coEvery { manager.load(thingId, id) } returns Result.success(data)
    coEvery { manager.delete(thingId, id) } returns Result.success(Unit)
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = DataLogViewerViewModel(manager, thingId, id)

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
    val download = MutableStateFlow<DownloadState>(DownloadState.Downloading(0f))
    every { manager.ensureLocal(thingId, id) } returns download
    val vm = viewModel()

    assertThat(vm.uiState.value).isEqualTo(DataLogViewerUiState.Loading(DownloadState.Downloading(0f)))
    download.value = DownloadState.Done
    assertThat(vm.uiState.value).isInstanceOf(DataLogViewerUiState.Ready::class.java)
  }

  @Test
  fun eachFailureHasItsOwnReason() = runTest {
    every { manager.observeOne(thingId, id) } returns flowOf(null)
    assertThat(viewModel().uiState.value).isEqualTo(DataLogViewerUiState.Failed(LoadFailure.NOT_FOUND))

    every { manager.observeOne(thingId, id) } returns flowOf(record)
    every { manager.ensureLocal(thingId, id) } returns flowOf(DownloadState.Failed(Exception("gone")))
    assertThat(viewModel().uiState.value).isEqualTo(DataLogViewerUiState.Failed(LoadFailure.DOWNLOAD_FAILED))

    every { manager.ensureLocal(thingId, id) } returns flowOf(DownloadState.Done)
    coEvery { manager.load(thingId, id) } returns Result.failure(IllegalStateException("corrupt"))
    val vm = viewModel()
    assertThat(vm.uiState.value).isEqualTo(DataLogViewerUiState.Failed(LoadFailure.PARSE_FAILED))

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

    coEvery { manager.delete(thingId, id) } returns Result.failure(IllegalStateException("offline"))
    vm.confirmDelete()
    assertThat(events).containsExactly(DataLogViewerEvent.Deleted, DataLogViewerEvent.DeleteFailed)
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
    assertThat(ready().cursorT).isWithin(0.01).of(1440.0)
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
    assertThat(vm.uiState.value).isEqualTo(DataLogViewerUiState.Failed(LoadFailure.NOT_FOUND))
  }
}
