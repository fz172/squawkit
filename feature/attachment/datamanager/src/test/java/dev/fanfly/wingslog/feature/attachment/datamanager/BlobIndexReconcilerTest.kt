package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val TEST_USER_ID = "test-user-123"
private const val TEST_AIRCRAFT_ID = "aircraft-abc"
private const val TEST_SHA256 =
  "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"

@OptIn(ExperimentalCoroutinesApi::class)
class BlobIndexReconcilerTest {

  private val scope = EntityScope.aircraftChildUnsafe(TEST_USER_ID, TEST_AIRCRAFT_ID)

  private fun buildAttachment(
    id: String = "att-1",
    sha256: String = TEST_SHA256
  ) = Attachment(
    id = id,
    name = "photo.jpg",
    type = AttachmentType.ATTACHMENT_TYPE_IMAGE,
    storage_path = "$id/path",
    mime_type = "image/jpeg",
    size_bytes = 1024L,
    sha256 = sha256,
  )

  // ---- Squawk ----

  @Test
  fun onEntityWritten_squawkWithAttachment_upsertsRemoteOnlyRow() = runTest {
    val blobs: LocalBlobStore = mockk(relaxed = true)
    val dispatcher = StandardTestDispatcher(testScheduler)
    val reconciler = BlobIndexReconciler(blobs, TestScope(dispatcher))
    val squawk = Squawk(id = "sq-1", attachments = listOf(buildAttachment()))

    reconciler.onEntityWritten(CollectionKind.Squawk, scope, squawk.encode())
    advanceUntilIdle()

    coVerify(exactly = 1) {
      blobs.upsertRemoteOnly(
        id = BlobId("att-1"),
        sha256 = TEST_SHA256,
        sizeBytes = 1024L,
        contentType = "image/jpeg",
        scope = scope,
      )
    }
  }

  @Test
  fun onEntityWritten_squawkWithBlankSha256_doesNotUpsert() = runTest {
    val blobs: LocalBlobStore = mockk(relaxed = true)
    val dispatcher = StandardTestDispatcher(testScheduler)
    val reconciler = BlobIndexReconciler(blobs, TestScope(dispatcher))
    val squawk =
      Squawk(id = "sq-1", attachments = listOf(buildAttachment(sha256 = "")))

    reconciler.onEntityWritten(CollectionKind.Squawk, scope, squawk.encode())
    advanceUntilIdle()

    coVerify(exactly = 0) {
      blobs.upsertRemoteOnly(
        any(),
        any(),
        any(),
        any(),
        any()
      )
    }
  }

  // ---- existing kinds still work ----

  @Test
  fun onEntityWritten_maintenanceLogWithAttachment_upsertsRemoteOnlyRow() =
    runTest {
      val blobs: LocalBlobStore = mockk(relaxed = true)
      val dispatcher = StandardTestDispatcher(testScheduler)
      val reconciler = BlobIndexReconciler(blobs, TestScope(dispatcher))
      val log = MaintenanceLog(
        id = "log-1",
        attachments = listOf(buildAttachment(id = "att-2"))
      )

      reconciler.onEntityWritten(
        CollectionKind.MaintenanceLog,
        scope,
        log.encode()
      )
      advanceUntilIdle()

      coVerify(exactly = 1) {
        blobs.upsertRemoteOnly(
          id = BlobId("att-2"),
          sha256 = TEST_SHA256,
          sizeBytes = 1024L,
          contentType = "image/jpeg",
          scope = scope,
        )
      }
    }

  @Test
  fun onEntityWritten_maintenanceTaskWithAttachment_upsertsRemoteOnlyRow() =
    runTest {
      val blobs: LocalBlobStore = mockk(relaxed = true)
      val dispatcher = StandardTestDispatcher(testScheduler)
      val reconciler = BlobIndexReconciler(blobs, TestScope(dispatcher))
      val task = MaintenanceTask(
        id = "task-1",
        attachments = listOf(buildAttachment(id = "att-3"))
      )

      reconciler.onEntityWritten(
        CollectionKind.MaintenanceTask,
        scope,
        task.encode()
      )
      advanceUntilIdle()

      coVerify(exactly = 1) {
        blobs.upsertRemoteOnly(
          id = BlobId("att-3"),
          sha256 = TEST_SHA256,
          sizeBytes = 1024L,
          contentType = "image/jpeg",
          scope = scope,
        )
      }
    }

  // ---- unhandled kinds ----

  @Test
  fun onEntityWritten_unhandledKind_doesNotUpsert() = runTest {
    val blobs: LocalBlobStore = mockk(relaxed = true)
    val dispatcher = StandardTestDispatcher(testScheduler)
    val reconciler = BlobIndexReconciler(blobs, TestScope(dispatcher))

    reconciler.onEntityWritten(CollectionKind.Aircraft, scope, ByteArray(0))
    advanceUntilIdle()

    coVerify(exactly = 0) {
      blobs.upsertRemoteOnly(
        any(),
        any(),
        any(),
        any(),
        any()
      )
    }
  }
}
