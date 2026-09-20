package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.NoOpAnalyticsManager
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.comments.datamanager.CommentManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.thing.Squawk
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant
import java.util.TimeZone as JavaTimeZone

private const val TEST_THING_ID = "thing-456"
private const val TEST_SQUAWK_ID = "squawk-789"

@OptIn(ExperimentalCoroutinesApi::class)
class SquawkFormViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var squawkManager: SquawkManager
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var commentManager: CommentManager
  private lateinit var logManager: MaintenanceLogManager
  private lateinit var auth: FirebaseAuth
  private lateinit var subscriptionManager: SubscriptionManager

  // Only gates the attach affordance on shared thing; irrelevant to these assertions.
  private lateinit var sharingManager: SharingManager

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    squawkManager = mockk(relaxed = true)
    attachmentManager = mockk(relaxed = true)
    commentManager = mockk(relaxed = true)
    every { commentManager.observeComments(any()) } returns flowOf(emptyList())
    logManager = mockk(relaxed = true)
    auth = mockk(relaxed = true)
    subscriptionManager = mockk(relaxed = true)
    sharingManager = mockk(relaxed = true)

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.isAnonymous } returns false
    every { auth.currentUser } returns mockUser

    // Prevent the init-block flows from suspending forever.
    every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
    // Own thing by default; foreign-hosted tests override this.
    every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(false)
    every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
      emptyList()
    )
    every { logManager.observeLogs(TEST_THING_ID) } returns flowOf(emptyList())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ---- showResolveMenu / selectDismissNoWorkPlanned ----

  // ---- hideDismissDialog ----

  // ---- confirmDismiss — success ----

  // ---- showResolveMenu / hideResolveMenu ----

  // ---- selectFixed ----

  // ---- delete ----

  @Test
  fun delete_callsDeleteSquawk_andEmitsSaveSuccess() = runTest(testDispatcher) {
    coEvery { squawkManager.deleteSquawk(any(), any()) } returns Result.success(
      true
    )
    val viewModel = buildViewModelForEdit()
    viewModel.showDeleteDialog()

    viewModel.delete("Squawk deleted")
    val event = viewModel.events.first()

    assertThat(viewModel.state.value.showDeleteDialog).isFalse()
    coVerify { squawkManager.deleteSquawk(TEST_THING_ID, TEST_SQUAWK_ID) }
    assertThat(event).isEqualTo(SquawkFormEvent.SaveSuccess("Squawk deleted"))
  }

  @Test
  fun delete_onFailure_surfacesDeleteFailed_andStaysOnTheForm() =
    runTest(testDispatcher) {
      coEvery { squawkManager.deleteSquawk(any(), any()) } returns
        Result.failure(IllegalStateException("offline"))
      val viewModel = buildViewModelForEdit()
      val events = mutableListOf<SquawkFormEvent>()
      val collecting = launch { viewModel.events.collect { events.add(it) } }

      viewModel.delete("Squawk deleted")
      advanceUntilIdle()

      assertThat(events).isEmpty()
      assertThat(viewModel.state.value.error).isNotNull()
      collecting.cancel()
    }

  @Test
  fun delete_withNoSquawkId_doesNotCallManager() = runTest(testDispatcher) {
    val viewModel = buildViewModelForNew()

    viewModel.delete("Squawk deleted")

    coVerify(exactly = 0) { squawkManager.deleteSquawk(any(), any()) }
  }

  /** The form baseline for PRD §7: the swipe share only means something against form commits. */
  @Test
  fun delete_onSuccess_logsRecordQuickActionFromTheForm() =
    runTest(testDispatcher) {
      coEvery {
        squawkManager.deleteSquawk(
          any(),
          any()
        )
      } returns Result.success(true)
      val analytics = mockk<AnalyticsManager>(relaxed = true)
      val viewModel = buildViewModelForEdit(analytics)

      viewModel.delete("Squawk deleted")
      advanceUntilIdle()

      verify {
        analytics.logEvent(
          "record_quick_action",
          match { it["surface"] == "squawks" && it["action"] == "delete" && it["source"] == "form" },
        )
      }
    }

  // ---- reopen — success ----

  // ---- addLocalFiles — error surfacing ----

  @Test
  fun addLocalFiles_whenAddPickedFileThrows_setsErrorOnState() =
    runTest(testDispatcher) {
      coEvery {
        attachmentManager.addPickedFile(any(), any(), any())
      } throws RuntimeException("disk full")
      val viewModel = buildViewModelForNew()

      viewModel.addLocalFiles(
        listOf(
          PickedFile(
            "uri",
            "photo.jpg",
            "image/jpeg",
            100L
          )
        )
      )
      advanceUntilIdle()

      assertThat(viewModel.state.value.error).isNotNull()
    }

  @Test
  fun addLocalFiles_whenAddPickedFileSucceeds_doesNotSetError() =
    runTest(testDispatcher) {
      coEvery {
        attachmentManager.addPickedFile(any(), any(), any())
      } returns mockk(relaxed = true)
      val viewModel = buildViewModelForNew()

      viewModel.addLocalFiles(
        listOf(
          PickedFile(
            "uri",
            "photo.jpg",
            "image/jpeg",
            100L
          )
        )
      )
      advanceUntilIdle()

      assertThat(viewModel.state.value.error).isNull()
    }

  @Test
  fun addLocalFiles_whenPickExceedsTheFileCap_setsErrorOnState() =
    runTest(testDispatcher) {
      coEvery {
        attachmentManager.addPickedFile(any(), any(), any())
      } returns mockk(relaxed = true)
      val viewModel = buildViewModelForNew()

      // No platform picker can cap multi-select, so five files can arrive for three slots. The
      // two that do not fit must be reported, not dropped in silence.
      viewModel.addLocalFiles(
        List(5) { index ->
          PickedFile(
            "uri-$index",
            "photo-$index.jpg",
            "image/jpeg",
            100L
          )
        }
      )
      advanceUntilIdle()

      assertThat(viewModel.state.value.error).isNotNull()
    }

  // ---- onFilePickError ----

  @Test
  fun onFilePickError_emitsPickErrorEvent() = runTest(testDispatcher) {
    val viewModel = buildViewModelForNew()

    viewModel.onFilePickError()
    advanceUntilIdle()

    val event = viewModel.events.first()
    assertThat(event).isInstanceOf(SquawkFormEvent.PickError::class.java)
  }

  // ---- clearError ----

  @Test
  fun clearError_setsErrorToNull() = runTest(testDispatcher) {
    coEvery {
      attachmentManager.addPickedFile(any(), any(), any())
    } throws RuntimeException("disk full")
    val viewModel = buildViewModelForNew()
    viewModel.addLocalFiles(
      listOf(
        PickedFile(
          "uri",
          "photo.jpg",
          "image/jpeg",
          100L
        )
      )
    )
    advanceUntilIdle()

    viewModel.clearError()

    assertThat(viewModel.state.value.error).isNull()
  }

  // ---- save — created_at preservation ----

  @Test
  fun save_onEdit_preservesOriginalCreatedAt() = runTest(testDispatcher) {
    val originalCreatedAt = Instant.fromEpochSeconds(1_700_000_000L)
    every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
      listOf(
        Squawk(
          id = TEST_SQUAWK_ID,
          title = "Nose gear shimmy",
          created_at = originalCreatedAt.toWireInstant(),
        )
      )
    )
    val saved = slot<Squawk>()
    coEvery {
      squawkManager.updateSquawk(any(), capture(saved))
    } returns Result.success(true)
    val viewModel = buildViewModelForEdit()

    viewModel.onTitleChange("Nose gear shimmy (worse)")
    viewModel.save("Saved")
    advanceUntilIdle()

    assertThat(saved.captured.created_at?.epochSecond)
      .isEqualTo(originalCreatedAt.epochSeconds)
  }

  @Test
  fun save_onEdit_whenCreatedAtMissing_backfillsIt() = runTest(testDispatcher) {
    every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
      listOf(
        Squawk(
          id = TEST_SQUAWK_ID,
          title = "Legacy squawk",
          created_at = null,
        )
      )
    )
    val saved = slot<Squawk>()
    coEvery {
      squawkManager.updateSquawk(any(), capture(saved))
    } returns Result.success(true)
    val viewModel = buildViewModelForEdit()

    viewModel.save("Saved")
    advanceUntilIdle()

    assertThat(saved.captured.created_at?.epochSecond ?: 0L).isGreaterThan(
      0L
    )
  }

  @Test
  fun save_onNew_setsCreatedAt() = runTest(testDispatcher) {
    val saved = slot<Squawk>()
    coEvery {
      squawkManager.addSquawk(any(), capture(saved))
    } returns Result.success(true)
    val viewModel = buildViewModelForNew()

    viewModel.onTitleChange("Radio static")
    viewModel.save("Saved")
    advanceUntilIdle()

    assertThat(saved.captured.created_at?.epochSecond ?: 0L).isGreaterThan(
      0L
    )
  }

  // ---- reported date — timezone (#224) ----

  @Test
  fun loadExisting_showsTheReportedDateInTheDeviceZone_notUtc() =
    runTest(testDispatcher) {
      // 2026-07-13 23:00 PDT — the same instant is already 07/14 in UTC, so reading it back in
      // UTC showed a squawk filed late on the 13th as reported on the 14th.
      val createdAt = Instant.parse("2026-07-14T06:00:00Z")
      every { squawkManager.observeSquawks(TEST_THING_ID) } returns flowOf(
        listOf(
          Squawk(
            id = TEST_SQUAWK_ID,
            title = "Nose gear shimmy",
            created_at = createdAt.toWireInstant(),
          )
        )
      )

      val viewModel = withDefaultTimeZone("America/Los_Angeles") {
        buildViewModelForEdit().also { advanceUntilIdle() }
      }

      assertThat(viewModel.state.value.reportedDateFormatted).isEqualTo("07/13/2026")
    }

  // ---- helpers ----

  /** Runs [block] with the JVM default zone set to [zoneId], which is what
   *  `TimeZone.currentSystemDefault()` reads. */
  private fun <T> withDefaultTimeZone(zoneId: String, block: () -> T): T {
    val original = JavaTimeZone.getDefault()
    JavaTimeZone.setDefault(JavaTimeZone.getTimeZone(zoneId))
    try {
      return block()
    } finally {
      JavaTimeZone.setDefault(original)
    }
  }

  private fun buildViewModelForEdit(
    analytics: AnalyticsManager = NoOpAnalyticsManager,
  ): SquawkFormViewModel =
    SquawkFormViewModel(
      squawkManager = squawkManager,
      currentThingTemplate = mockk<CurrentThingTemplate>(relaxed = true),
      analytics = analytics,
      attachmentManager = attachmentManager,
      commentManager = commentManager,
      logManager = logManager,
      auth = auth,
      subscriptionManager = subscriptionManager,
      sharingManager = sharingManager,
      savedStateHandle = SavedStateHandle(
        mapOf(
          Screen.THING_ID to TEST_THING_ID,
          Screen.SQUAWK_ID to TEST_SQUAWK_ID,
        )
      ),
    )

  private fun buildViewModelForNew(): SquawkFormViewModel =
    SquawkFormViewModel(
      squawkManager = squawkManager,
      currentThingTemplate = mockk<CurrentThingTemplate>(relaxed = true),
      analytics = NoOpAnalyticsManager,
      attachmentManager = attachmentManager,
      commentManager = commentManager,
      logManager = logManager,
      auth = auth,
      subscriptionManager = subscriptionManager,
      sharingManager = sharingManager,
      savedStateHandle = SavedStateHandle(
        mapOf(Screen.THING_ID to TEST_THING_ID)
      ),
    )

  // --- Attachments on a shared thing (design §9, #146) ---

  @Test
  fun attachAvailable_onAnThingHostedByAnotherAccount() =
    runTest(testDispatcher) {
      // A member's upload now travels through the broker into the host's tree (P8.4 §9.2), so the
      // attach button is offered on a shared thing — no longer hard-disabled by hosting.
      every { subscriptionManager.canUploadAttachments() } returns flowOf(true)

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(viewModel.attachmentUploadEnabled.value).isTrue()
    }

  @Test
  fun attachAvailable_onOwnThing_whenTheFlagIsOn() =
    runTest(testDispatcher) {
      every { subscriptionManager.canUploadAttachments() } returns flowOf(true)

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(viewModel.attachmentUploadEnabled.value).isTrue()
    }

  @Test
  fun attachStaysOff_onOwnThing_whenTheEntitlementIsOff() =
    runTest(testDispatcher) {
      // On an OWN thing the member's own entitlement has the final say (P8.7 §9.7).
      every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
      every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(
        false
      )

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(viewModel.attachmentUploadEnabled.value).isFalse()
    }

  @Test
  fun attachAvailable_onForeignHostedThing_evenWithoutOwnEntitlement() =
    runTest(testDispatcher) {
      // The host pays and the broker enforces the host's entitlement, so a member with no subscription
      // of their own can still attach on a paid owner's thing (P8.7 §9.7).
      every { subscriptionManager.canUploadAttachments() } returns flowOf(false)
      every { sharingManager.observeIsForeignHosted(any()) } returns flowOf(true)

      val viewModel = buildViewModelForNew()
      advanceUntilIdle()

      assertThat(viewModel.attachmentUploadEnabled.value).isTrue()
    }
}
