package dev.fanfly.wingslog.feature.squawk.dashboard.detail

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.RecordingAnalyticsManager
import dev.fanfly.wingslog.core.appinfo.APP_VERSION_CODE
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.comments.model.CommentParentKind
import dev.fanfly.wingslog.feature.comments.model.CommentTarget
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.squawk.model.toWithStatus
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.core.sharedassets.generated.resources.save_failed
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_deleted
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SquawkRes

private const val THING_ID = "thing-1"
private val THING = ThingId(THING_ID)
private val SQUAWK_ID = SquawkId("s1")

@OptIn(ExperimentalCoroutinesApi::class)
class SquawkDetailViewModelTest {

  private val squawkManager: SquawkManager = mockk()
  private val logManager: MaintenanceLogManager = mockk()
  private val commentManager: CommentManager = mockk(relaxed = true) {
    every { observeComments(any()) } returns flowOf(emptyList())
  }
  private val analytics = RecordingAnalyticsManager()

  private val squawk = Squawk(id = "s1", title = "Transponder intermittent").toWithStatus()

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    every { logManager.observeLogs(THING_ID) } returns flowOf(emptyList())
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = SquawkDetailViewModel(
    squawkManager = squawkManager,
    logManager = logManager,
    commentManager = commentManager,
    templateRegistry = BakedInTemplateRegistry(appVersionCode = APP_VERSION_CODE),
    analytics = analytics,
    thingId = THING,
    templateId = AirplaneTemplate.TEMPLATE.id,
  )

  @Test
  fun confirmDeleteSquawk_deletesThroughTheManager_clearsTheIdsAndSaysSo() = runTest {
    coEvery { squawkManager.deleteSquawk(THING, SQUAWK_ID) } returns Result.success(true)
    val vm = viewModel()
    vm.showDetail(squawk)
    vm.onDeleteClick(squawk)
    assertThat(vm.uiState.value.deletingSquawkId).isEqualTo(SQUAWK_ID)

    vm.confirmDelete()

    coVerify(exactly = 1) { squawkManager.deleteSquawk(THING, SQUAWK_ID) }
    assertThat(vm.uiState.value.deletingSquawkId).isNull()
    assertThat(vm.uiState.value.selectedSquawk).isNull()
    assertThat(vm.events.first())
      .isEqualTo(UiText.StringRes(SquawkRes.string.squawk_deleted, listOf("Squawk")))
  }

  @Test
  fun deleteSquawkFailure_closesDialogAndReportsIt() = runTest {
    coEvery { squawkManager.deleteSquawk(THING, SQUAWK_ID) } returns
      Result.failure(IllegalStateException("offline"))
    val vm = viewModel()
    vm.onDeleteClick(squawk)

    vm.confirmDelete()

    assertThat(vm.uiState.value.deletingSquawkId).isNull()
    assertThat(vm.events.first())
      .isEqualTo(UiText.StringRes(CoreRes.string.delete_failed))
  }

  @Test
  fun confirmDismissSquawk_passesTheChosenReason() = runTest {
    coEvery {
      squawkManager.dismissSquawk(
        THING,
        SQUAWK_ID,
        SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
      )
    } returns Result.success(Unit)
    val vm = viewModel()
    vm.onDismissClick(SQUAWK_ID)

    vm.confirmDismiss(SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE)

    coVerify(exactly = 1) {
      squawkManager.dismissSquawk(
        THING,
        SQUAWK_ID,
        SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
      )
    }
    assertThat(vm.uiState.value.dismissingSquawkId).isNull()
  }

  @Test
  fun openingASquawkSheet_opensThatSquawksThread_andClosingItClosesTheThread() = runTest {
    val vm = viewModel()
    assertThat(vm.commentThread.value).isNull()

    vm.showDetail(squawk)
    advanceUntilIdle()

    assertThat(vm.commentThread.value).isNotNull()
    verify {
      commentManager.observeComments(
        CommentTarget(
          THING_ID,
          "s1",
          CommentParentKind.SQUAWK,
        )
      )
    }

    vm.dismissDetail()
    advanceUntilIdle()

    assertThat(vm.commentThread.value).isNull()
  }

  @Test
  fun anUnpostedDraft_survivesClosingTheSheet() = runTest {
    val vm = viewModel()
    vm.showDetail(squawk)
    advanceUntilIdle()
    vm.commentThread.value!!.onDraftChange("Kit arrived")

    vm.dismissDetail()
    advanceUntilIdle()
    vm.showDetail(squawk)
    advanceUntilIdle()

    assertThat(vm.commentThread.value!!.state.value.draft).isEqualTo("Kit arrived")
  }

  @Test
  fun squawkReopenClick_reopensThroughTheManager() = runTest {
    coEvery { squawkManager.reopenSquawk(THING, SQUAWK_ID) } returns Result.success(Unit)
    val vm = viewModel()

    vm.reopenSquawk(SQUAWK_ID)

    coVerify(exactly = 1) { squawkManager.reopenSquawk(THING, SQUAWK_ID) }
  }

  @Test
  fun squawkReopenClick_failing_saysSo() = runTest {
    coEvery { squawkManager.reopenSquawk(THING, SQUAWK_ID) } returns
      Result.failure(IllegalStateException("offline"))
    val vm = viewModel()

    vm.reopenSquawk(SQUAWK_ID)

    assertThat(vm.events.first())
      .isEqualTo(UiText.StringRes(CoreRes.string.save_failed))
  }

  @Test
  fun cancelledConfirmations_writeNothingAndLogNothing() = runTest {
    val vm = viewModel()
    vm.onDeleteClick(squawk)
    vm.cancelDelete()
    vm.onDismissClick(SQUAWK_ID)
    vm.cancelDismiss()

    coVerify(exactly = 0) { squawkManager.deleteSquawk(any(), any()) }
    coVerify(exactly = 0) { squawkManager.dismissSquawk(any(), any(), any()) }
    assertThat(analytics.countOf("record_quick_action")).isEqualTo(0)
  }

  @Test
  fun choosingABubbleOption_logsTheCommitAndClosesTheBubble() = runTest {
    val vm = viewModel()
    vm.onResolveClick(squawk)
    assertThat(vm.uiState.value.resolvingSquawkId).isEqualTo(SQUAWK_ID)

    vm.onFixedClick()

    assertThat(vm.uiState.value.resolvingSquawkId).isNull()
    assertThat(analytics.paramsFor("record_quick_action").single())
      .containsAtLeast(
        "surface",
        "squawks",
        "action",
        "resolve",
        "source",
        "swipe",
      )
  }
}
