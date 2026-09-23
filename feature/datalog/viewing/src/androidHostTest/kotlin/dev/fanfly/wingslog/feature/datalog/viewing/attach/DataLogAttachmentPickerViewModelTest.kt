package dev.fanfly.wingslog.feature.datalog.viewing.attach

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.core.analytics.RecordedEvent
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DataLogAttachmentPickerViewModelTest {

  private val thingId = ThingId("thing-1")
  private val logs = MutableStateFlow<List<DataLog>>(emptyList())
  private lateinit var manager: DataLogManager
  private lateinit var auth: AuthManager
  private lateinit var analytics: RecordingAnalyticsManager
  private lateinit var templates: CurrentThingTemplate
  private val file = PickedFile("content://x", "x.csv", "text/csv", 1)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    manager = mockk()
    every { manager.observe(thingId) } returns logs
    every { manager.observeOne(any(), any()) } returns flowOf(null)
    auth = mockk()
    analytics = RecordingAnalyticsManager()
    templates = mockk()
    every { templates.templateId } returns "airplane"
    signIn(anonymous = false)
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun signIn(anonymous: Boolean) {
    val user = mockk<FirebaseUser>()
    every { user.isAnonymous } returns anonymous
    every { auth.getCurrentUser() } returns user
  }

  private fun viewModel() =
    DataLogAttachmentPickerViewModel(
      manager,
      auth,
      analytics,
      templates,
      thingId
    )

  private fun log(id: String) = DataLog(
    id = DataLogId(id),
    start = Instant.parse("2026-09-02T21:47:56Z")
      .toWireInstant(),
    duration_seconds = 255,
  )

  @Test
  fun rowsLoadAndTapsToggleASelectionOfMany() = runTest {
    logs.value = listOf(log("a"), log("b"))
    val vm = viewModel()
    val state = vm.uiState.first { it.loaded }
    assertThat(state.rows.map { it.id }).containsExactly(
      DataLogId("a"),
      DataLogId("b")
    )
    assertThat(state.selected).isEmpty()
    assertThat(state.canUpload).isTrue()

    vm.toggle(DataLogId("b"))
    vm.toggle(DataLogId("a"))
    assertThat(vm.uiState.value.selected).containsExactly(
      DataLogId("a"),
      DataLogId("b")
    )

    vm.toggle(DataLogId("b"))
    assertThat(vm.uiState.value.selected).containsExactly(DataLogId("a"))
  }

  @Test
  fun aFinishedUploadAddsTheNewRecordToTheSelection() = runTest {
    every { manager.import(thingId, file, false, true) } returns
      flowOf(
        ImportProgress.Reading,
        ImportProgress.Storing,
        ImportProgress.Done(DataLogId("new"))
      )
    val vm = viewModel()
    val collecting = launch { vm.uiState.collect {} }
    vm.uiState.first { it.loaded }
    vm.toggle(DataLogId("old"))

    vm.upload(listOf(file))

    assertThat(vm.uiState.value.selected).containsExactly(
      DataLogId("old"),
      DataLogId("new")
    )
    assertThat(vm.uiState.value.import).isNull()
    collecting.cancel()
  }

  @Test
  fun aProbableDuplicateWaitsForKeepBoth() = runTest {
    every { manager.import(thingId, file, false, true) } returns
      flowOf(
        ImportProgress.Reading,
        ImportProgress.NeedsConfirmation(DataLogId("old"))
      )
    every { manager.import(thingId, file, true, true) } returns flowOf(
      ImportProgress.Done(DataLogId("new"))
    )
    val vm = viewModel()
    val collecting = launch { vm.uiState.collect {} }
    vm.uiState.first { it.loaded }

    vm.upload(listOf(file))
    assertThat(vm.uiState.value.import?.progress).isEqualTo(
      ImportProgress.NeedsConfirmation(
        DataLogId("old")
      )
    )
    assertThat(vm.uiState.value.selected).isEmpty()

    vm.confirmImport()
    assertThat(vm.uiState.value.selected).containsExactly(DataLogId("new"))
    assertThat(vm.uiState.value.import).isNull()
    collecting.cancel()
  }

  @Test
  fun aFailedUploadStaysUntilDismissed() = runTest {
    every {
      manager.import(
        thingId,
        file,
        false,
        true
      )
    } returns flow { throw IllegalStateException("boom") }
    val vm = viewModel()
    val collecting = launch { vm.uiState.collect {} }
    vm.uiState.first { it.loaded }

    vm.upload(listOf(file))
    assertThat(vm.uiState.value.import?.progress).isEqualTo(
      ImportProgress.Failed(
        ImportFailure.PARSE_ERROR
      )
    )

    vm.dismissImport()
    assertThat(vm.uiState.value.import).isNull()
    collecting.cancel()
  }

  @Test
  fun aGuestCannotUpload() = runTest {
    signIn(anonymous = true)
    val vm = viewModel()
    assertThat(vm.uiState.value.canUpload).isFalse()

    vm.upload(listOf(file))
    verify(exactly = 0) { manager.import(any(), any(), any(), any()) }
  }

  @Test
  fun anImportFromHereIsReportedAsTheAttachmentSource() = runTest {
    every {
      manager.observeOne(
        thingId,
        DataLogId("new")
      )
    } returns flowOf(log("new"))
    every { manager.import(thingId, any(), false, true) } returns
      flowOf(ImportProgress.Done(DataLogId("new")))
    val vm = viewModel()
    vm.uiState.first { it.loaded }

    vm.upload(listOf(file))

    assertThat(analytics.events.single().name).isEqualTo("data_log_imported")
    assertThat(analytics.events.single().params).containsEntry(
      "source",
      "attachment"
    )
  }
}
