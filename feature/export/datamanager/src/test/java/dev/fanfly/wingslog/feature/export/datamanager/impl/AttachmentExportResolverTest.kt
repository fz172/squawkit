package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The rule this class exists to enforce: **a missing attachment never blocks the export.**
 *
 * It used to. Resolution ran one attachment at a time and each unfetchable one burned the full
 * `ensureLocal` timeout, so eleven of them cost five and a half silent minutes (#426). These tests
 * pin both halves of the fix — partial results, and a bound that does not scale with the number of
 * broken attachments.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AttachmentExportResolverTest {

  private val localBlobStore: LocalBlobStore = mockk()
  private val blobFilesystem: BlobFilesystem = mockk()

  /** Never reaches a terminal state — stands in for a blob whose bytes are gone. */
  private fun stuckForever(): Flow<DownloadState> = flow {
    emit(DownloadState.Downloading(0f))
    delay(Long.MAX_VALUE)
  }

  private fun resolver(manager: AttachmentManager) =
    AttachmentExportResolver(manager, localBlobStore, blobFilesystem)

  private fun attachment(id: String) = Attachment(
    id = id,
    name = "$id.jpg",
    mime_type = "image/jpeg",
    type = AttachmentType.ATTACHMENT_TYPE_FILE,
  )

  private fun bundle(vararg attachments: Attachment) = AircraftBundle(
    aircraft = Aircraft(id = "ac1"),
    logs = listOf(MaintenanceLog(id = "log1", attachments = attachments.toList())),
    tasks = emptyList(),
    dueByTaskId = emptyMap(),
    lastCompliedByTaskId = emptyMap(),
    squawks = emptyList(),
    tasksById = emptyMap(),
    squawksById = emptyMap(),
    techniciansById = emptyMap(),
  )

  private fun blobRef(id: String) = BlobRef(
    id = BlobId(id),
    scope = EntityScope.aircraftChildUnsafe("uid", "ac1"),
    relativePath = "blobs/$id.bin",
    sizeBytes = 3L,
    sha256 = "sha",
    contentType = "image/jpeg",
    remoteState = RemoteState.Synced,
    remotePath = null,
    uploadAttempts = 0L,
    deleted = false,
    updatedAt = Instant.fromEpochSeconds(0),
  )

  /** The whole point: one good attachment still ships even when others never resolve. */
  @Test
  fun resolve_returnsWhatItCanWhenSomeAttachmentsNeverResolve() = runTest {
    val good = attachment("good")
    val manager = mockk<AttachmentManager>()
    every { manager.ensureLocal(good) } returns flowOf(DownloadState.Done)
    every { manager.ensureLocal(match { it.id != "good" }) } returns stuckForever()
    coEvery { localBlobStore.get(BlobId("good")) } returns blobRef("good")
    coEvery { blobFilesystem.read("blobs/good.bin") } returns byteArrayOf(1, 2, 3)

    val manifest = resolver(manager).resolve(
      bundle(attachment("dead1"), good, attachment("dead2")),
    )

    assertThat(manifest.byAttachmentId.keys).containsExactly("good")
    assertThat(manifest.notes).hasSize(2)
  }

  /**
   * The regression that made this look like a hang: the cost must not scale with the number of
   * broken attachments. Twelve stuck attachments must not cost twelve timeouts.
   */
  @Test
  fun resolve_boundsTheWaitRegardlessOfHowManyAreBroken() = runTest {
    val manager = mockk<AttachmentManager>()
    every { manager.ensureLocal(any()) } returns stuckForever()
    val many = (1..12).map { attachment("dead$it") }

    val started = currentTime
    val manifest = resolver(manager).resolve(bundle(*many.toTypedArray()))
    val elapsed = currentTime - started

    assertThat(manifest.byAttachmentId).isEmpty()
    assertThat(manifest.notes).hasSize(12)
    // One budget, not twelve timeouts. Sequentially this was 12 × ensureLocal's own 30s.
    assertThat(elapsed).isAtMost(70_000L)
  }

  /** Link attachments have no bytes to fetch and must not consume the budget. */
  @Test
  fun resolve_ignoresLinkAttachments() = runTest {
    val manager = mockk<AttachmentManager>()
    val link = attachment("link1").copy(type = AttachmentType.ATTACHMENT_TYPE_LINK)

    val manifest = resolver(manager).resolve(bundle(link))

    assertThat(manifest.byAttachmentId).isEmpty()
    assertThat(manifest.notes).isEmpty()
  }
}
