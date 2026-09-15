package dev.fanfly.wingslog.feature.datalog.viewing.list

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.datalog.DataLogSource
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DataLogListViewModelTest {

  private val thingId = ThingId("thing-1")
  private val logs = MutableStateFlow<List<DataLog>>(emptyList())
  private lateinit var manager: DataLogManager
  private lateinit var auth: AuthManager

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    manager = mockk()
    every { manager.observe(thingId) } returns logs
    auth = mockk()
    signIn(anonymous = false)
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun signIn(anonymous: Boolean) {
    val user = mockk<FirebaseUser>()
    every { user.isAnonymous } returns anonymous
    every { auth.getCurrentUser() } returns user
  }

  private fun viewModel() = DataLogListViewModel(manager, auth, thingId)

  private fun log(
    id: String,
    start: String,
    airborne: Boolean = false,
    ident: String = "XX1",
    offset: Int = -420
  ) = DataLog(
    id = DataLogId(id),
    start = Instant.parse(start)
      .toWireInstant(),
    utc_offset_minutes = offset,
    duration_seconds = 255,
    airborne = airborne,
    start_location_ident = ident,
    file_name = "log_$id.csv",
    source = DataLogSource(product = "GDU 460", identity = "N1234X"),
    series = listOf(DataLogSeries(column = 1), DataLogSeries(column = 2)),
  )

  @Test
  fun rowsRenderInTheRecordersOwnClock() = runTest {
    logs.value = listOf(log("a", "2026-09-02T21:47:56Z"))
    val state = viewModel().uiState.first { !it.isLoading }
    val row = state.rows.single()
    assertThat(row.startLocal.toString()).isEqualTo("2026-09-02T14:47:56")
    assertThat(row.durationSeconds).isEqualTo(255)
    assertThat(row.seriesCount).isEqualTo(2)
    assertThat(row.product).isEqualTo("GDU 460")
    assertThat(state.uploadGate).isEqualTo(UploadGate.SignedIn)
  }

  @Test
  fun aGuestSeesTheListButCannotUpload() = runTest {
    signIn(anonymous = true)
    logs.value = listOf(log("a", "2026-09-02T21:47:56Z"))
    val vm = viewModel()
    val state = vm.uiState.first { !it.isLoading }
    assertThat(state.rows).hasSize(1)
    assertThat(state.uploadGate).isEqualTo(UploadGate.Guest)

    vm.upload(listOf(PickedFile("content://x", "x.csv", "text/csv", 1)))
    verify(exactly = 0) { manager.import(any(), any(), any(), any()) }
  }

  @Test
  fun noUserAtAllIsAGuest() = runTest {
    every { auth.getCurrentUser() } returns null
    assertThat(viewModel().uiState.value.uploadGate).isEqualTo(UploadGate.Guest)
  }

  @Test
  fun searchMatchesDateIdentTailProductOrFileName() = runTest {
    logs.value = listOf(
      log("a", "2026-09-02T21:47:56Z", ident = "KSQL"),
      log("b", "2026-08-01T10:00:00Z", ident = "XX1")
    )
    val vm = viewModel()
    vm.uiState.first { !it.isLoading }
    vm.onQueryChange("ksql")
    assertThat(vm.uiState.value.visibleRows.map { it.id }).containsExactly(
      DataLogId("a")
    )
    vm.onQueryChange("2026-08")
    assertThat(vm.uiState.value.visibleRows.map { it.id }).containsExactly(
      DataLogId("b")
    )
    vm.onQueryChange("n1234x")
    assertThat(vm.uiState.value.visibleRows).hasSize(2)
    vm.onQueryChange("log_b")
    assertThat(vm.uiState.value.visibleRows.map { it.id }).containsExactly(
      DataLogId("b")
    )
    vm.onQueryChange("")
    assertThat(vm.uiState.value.visibleRows).hasSize(2)
  }

  @Test
  fun anImportShowsInlineUntilDoneAndAFailureStaysUntilDismissed() = runTest {
    val progress = MutableSharedFlow<ImportProgress>()
    every { manager.import(thingId, any(), false, false) } returns progress
    val vm = viewModel()
    vm.uiState.first { !it.isLoading }
    val file = PickedFile("content://x", "x.csv", "text/csv", 1)

    vm.upload(listOf(file))
    assertThat(vm.uiState.value.imports.single().progress).isEqualTo(
      ImportProgress.Reading
    )
    progress.emit(ImportProgress.Parsing(0))
    assertThat(vm.uiState.value.imports.single().progress).isEqualTo(
      ImportProgress.Parsing(0)
    )
    progress.emit(ImportProgress.Done(DataLogId("new")))
    assertThat(vm.uiState.value.imports).isEmpty()

    vm.upload(listOf(file))
    progress.emit(ImportProgress.Failed(ImportFailure.UNRECOGNIZED))
    val failed = vm.uiState.value.imports.single()
    assertThat(failed.progress).isEqualTo(ImportProgress.Failed(ImportFailure.UNRECOGNIZED))
    vm.dismissImport(failed.key)
    assertThat(vm.uiState.value.imports).isEmpty()
  }

  @Test
  fun keepBothRerunsTheImportWithConfirmation() = runTest {
    every { manager.import(thingId, any(), false, false) } returns flow {
      emit(
        ImportProgress.NeedsConfirmation(DataLogId("older"))
      )
    }
    every { manager.import(thingId, any(), true, false) } returns flow {
      emit(
        ImportProgress.Storing
      ); emit(ImportProgress.Done(DataLogId("new")))
    }
    val vm = viewModel()
    vm.uiState.first { !it.isLoading }

    vm.upload(listOf(PickedFile("content://x", "x.csv", "text/csv", 1)))
    val row = vm.uiState.value.imports.single()
    assertThat(row.progress).isEqualTo(
      ImportProgress.NeedsConfirmation(
        DataLogId("older")
      )
    )

    vm.confirmImport(row.key)
    verify { manager.import(thingId, any(), true, false) }
    assertThat(vm.uiState.value.imports).isEmpty()
  }

  @Test
  fun aLogNamingAnotherThingCanBeFiledThereOrKeptHere() = runTest {
    val other = ThingId("thing-2")
    val file = PickedFile("content://x", "x.csv", "text/csv", 1)
    every { manager.import(thingId, any(), false, false) } returns
      flowOf(ImportProgress.OtherThing(other, "N5678Y Cub"))
    every { manager.import(other, any(), true, false) } returns flowOf(ImportProgress.Done(DataLogId("moved")))
    every { manager.import(thingId, any(), true, true) } returns flowOf(ImportProgress.Done(DataLogId("kept")))
    val vm = viewModel()
    vm.uiState.first { !it.isLoading }

    vm.upload(listOf(file))
    val offered = vm.uiState.value.imports.single()
    assertThat(offered.progress).isEqualTo(ImportProgress.OtherThing(other, "N5678Y Cub"))

    // Filing it there imports against the other Thing, so nothing lands on this one.
    vm.fileUnderOtherThing(offered.key)
    verify { manager.import(other, any(), true, false) }
    assertThat(vm.uiState.value.imports).isEmpty()

    vm.upload(listOf(file))
    val again = vm.uiState.value.imports.single()
    vm.keepHere(again.key)
    verify { manager.import(thingId, any(), true, true) }
    assertThat(vm.uiState.value.imports).isEmpty()
  }

  @Test
  fun swipeDeleteAsksFirstThenDeletesThroughTheManager() = runTest {
    logs.value = listOf(log("a", "2026-09-02T21:47:56Z"))
    coEvery { manager.delete(thingId, DataLogId("a")) } returns Result.success(
      Unit
    )
    val vm = viewModel()
    val row = vm.uiState.first { !it.isLoading }.rows.single()

    vm.onDeleteClick(row)
    assertThat(vm.uiState.value.deleting).isEqualTo(row)
    vm.cancelDelete()
    assertThat(vm.uiState.value.deleting).isNull()
    coVerify(exactly = 0) { manager.delete(any(), any()) }

    vm.onDeleteClick(row)
    vm.confirmDelete()
    assertThat(vm.uiState.value.deleting).isNull()
    coVerify { manager.delete(thingId, DataLogId("a")) }
  }

  @Test
  fun aFailedDeleteRaisesAnEvent() = runTest {
    logs.value = listOf(log("a", "2026-09-02T21:47:56Z"))
    coEvery { manager.delete(thingId, DataLogId("a")) } returns Result.failure(
      IllegalStateException("offline")
    )
    val vm = viewModel()
    val row = vm.uiState.first { !it.isLoading }.rows.single()
    val events = mutableListOf<DataLogListEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

    vm.onDeleteClick(row)
    vm.confirmDelete()

    assertThat(events).containsExactly(DataLogListEvent.DeleteFailed)
  }
}
