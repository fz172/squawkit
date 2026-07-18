package dev.fanfly.wingslog.feature.attachment.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.feature.attachment.datamanager.FileByteReader
import dev.fanfly.wingslog.feature.attachment.datamanager.FileTooLargeException
import dev.fanfly.wingslog.feature.attachment.datamanager.ImageCompressor
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.feature.attachment.model.AttachmentStatus
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val TEST_USER_ID = "test-user-123"
private const val TEST_AIRCRAFT_ID = "aircraft-abc"
private const val TEST_SHA256 =
  "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
private val FIXED_EPOCH_SECONDS = 1_700_000_000L

@OptIn(ExperimentalTime::class)
class LocalFirstAttachmentManagerImplTest {

  private lateinit var blobs: LocalBlobStore
  private lateinit var auth: AuthManager
  private lateinit var fileByteReader: FileByteReader
  private lateinit var imageCompressor: ImageCompressor
  private lateinit var uploadScheduler: UploadScheduler
  private lateinit var clock: Clock
  private lateinit var manager: LocalFirstAttachmentManagerImpl

  @Before
  fun setUp() {
    blobs = mockk(relaxed = true)
    auth = mockk(relaxed = true)
    fileByteReader = mockk(relaxed = true)
    // Default: the compressor declines (returns null), so bytes pass through untouched. Tests
    // that exercise compression stub compressToJpeg explicitly.
    imageCompressor = mockk(relaxed = true)
    every { imageCompressor.compressToJpeg(any()) } returns null
    uploadScheduler = mockk(relaxed = true)
    clock = mockk(relaxed = true)

    val fixedNow = Instant.fromEpochSeconds(FIXED_EPOCH_SECONDS, 0)
    every { clock.now() } returns fixedNow

    val mockUser = mockk<FirebaseUser>()
    every { mockUser.uid } returns TEST_USER_ID
    every { auth.getCurrentUser() } returns mockUser

    manager = LocalFirstAttachmentManagerImpl(
      blobs,
      auth,
      fileByteReader,
      imageCompressor,
      uploadScheduler,
      clock = clock
    )
  }

  // ---- helpers ----

  private fun buildPickedFile(
    uri: String = "content://example/file",
    name: String = "photo.jpg",
    mimeType: String = "image/jpeg",
    sizeBytes: Long = 1024L,
  ) = PickedFile(
    uri = uri,
    name = name,
    mimeType = mimeType,
    sizeBytes = sizeBytes
  )

  private fun buildAttachment(
    id: String = "att-1",
    sha256: String = TEST_SHA256,
  ) = Attachment(
    id = id,
    name = "photo.jpg",
    type = AttachmentType.ATTACHMENT_TYPE_IMAGE,
    storage_path = "$id/path",
    mime_type = "image/jpeg",
    size_bytes = 1024L,
    sha256 = sha256,
  )

  private fun buildBlobRef(
    sha256: String = TEST_SHA256,
    remoteState: RemoteState = RemoteState.LocalOnly,
  ) = BlobRef(
    id = BlobId("some-id"),
    scope = EntityScope.userRoot(TEST_USER_ID),
    relativePath = "blobs/some-id.bin",
    sizeBytes = 1024L,
    sha256 = sha256,
    contentType = "image/jpeg",
    remoteState = remoteState,
    remotePath = null,
    uploadAttempts = 0L,
    deleted = false,
    updatedAt = Instant.fromEpochSeconds(FIXED_EPOCH_SECONDS, 0),
  )

  // ---- addPickedFile ----

  @Test
  fun addPickedFile_populatesSha256FromBlobRefAndStoragePath() = runTest {
    val picked = buildPickedFile(
      uri = "content://example/photo.jpg",
      mimeType = "image/jpeg"
    )
    val fakeBytes = byteArrayOf(1, 2, 3)
    every { fileByteReader.readBytes(picked.uri) } returns fakeBytes
    coEvery {
      blobs.put(
        any(),
        fakeBytes,
        contentType = any(),
        scope = any()
      )
    } returns
      buildBlobRef(sha256 = TEST_SHA256)

    val result =
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "My Photo")

    assertThat(result.sha256).isEqualTo(TEST_SHA256)
    assertThat(result.storage_path)
      .startsWith("users/$TEST_USER_ID/aircraft/$TEST_AIRCRAFT_ID/blobs/")
    // storage_path must be: users/{uid}/aircraft/{aircraftId}/blobs/{id}
    val parts = result.storage_path.split("/")
    assertThat(parts).hasSize(6)
    assertThat(parts[0]).isEqualTo("users")
    assertThat(parts[1]).isEqualTo(TEST_USER_ID)
    assertThat(parts[2]).isEqualTo("aircraft")
    assertThat(parts[3]).isEqualTo(TEST_AIRCRAFT_ID)
    assertThat(parts[4]).isEqualTo("blobs")
    assertThat(parts[5]).isNotEmpty()
  }

  @Test
  fun addPickedFile_createdAtMatchesFixedClock() = runTest {
    val picked = buildPickedFile()
    every { fileByteReader.readBytes(any()) } returns byteArrayOf(1)
    coEvery {
      blobs.put(
        any(),
        any(),
        contentType = any(),
        scope = any()
      )
    } returns buildBlobRef()

    val result =
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "file")

    assertThat(result.created_at).isNotNull()
    assertThat(result.created_at!!.getEpochSecond()).isEqualTo(
      FIXED_EPOCH_SECONDS
    )
  }

  @Test
  fun addPickedFile_throwsIllegalStateException_whenNoSignedInUser() = runTest {
    every { auth.getCurrentUser() } returns null

    var caught: Throwable? = null
    try {
      manager.addPickedFile(
        TEST_AIRCRAFT_ID,
        buildPickedFile(),
        displayName = "file"
      )
    } catch (e: IllegalStateException) {
      caught = e
    }

    assertThat(caught).isNotNull()
  }

  @Test
  fun addPickedFile_throws_whenFileByteReaderReturnsNull() = runTest {
    every { fileByteReader.readBytes(any()) } returns null

    var caught: Throwable? = null
    try {
      manager.addPickedFile(
        TEST_AIRCRAFT_ID,
        buildPickedFile(),
        displayName = "file"
      )
    } catch (e: IllegalStateException) {
      caught = e
    }

    assertThat(caught).isNotNull()
  }

  @Test
  fun addPickedFile_derivesType_imagePng_toImage() = runTest {
    val picked = buildPickedFile(mimeType = "image/png")
    every { fileByteReader.readBytes(any()) } returns byteArrayOf(1)
    coEvery {
      blobs.put(
        any(),
        any(),
        contentType = any(),
        scope = any()
      )
    } returns buildBlobRef()

    val result =
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "pic")

    assertThat(result.type.name).isEqualTo("ATTACHMENT_TYPE_IMAGE")
  }

  @Test
  fun addPickedFile_derivesType_applicationPdf_toPdf() = runTest {
    val picked = buildPickedFile(mimeType = "application/pdf")
    every { fileByteReader.readBytes(any()) } returns byteArrayOf(1)
    coEvery {
      blobs.put(
        any(),
        any(),
        contentType = any(),
        scope = any()
      )
    } returns buildBlobRef()

    val result =
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "doc.pdf")

    assertThat(result.type.name).isEqualTo("ATTACHMENT_TYPE_PDF")
  }

  @Test
  fun addPickedFile_derivesType_textPlain_toFile() = runTest {
    val picked = buildPickedFile(mimeType = "text/plain")
    every { fileByteReader.readBytes(any()) } returns byteArrayOf(1)
    coEvery {
      blobs.put(
        any(),
        any(),
        contentType = any(),
        scope = any()
      )
    } returns buildBlobRef()

    val result =
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "notes.txt")

    assertThat(result.type.name).isEqualTo("ATTACHMENT_TYPE_FILE")
  }

  // ---- addPickedFile — compression ----

  @Test
  fun addPickedFile_compressesPhoto_storesCompressedBytesAndRewritesMetadata() = runTest {
    val picked = buildPickedFile(mimeType = "image/jpeg", sizeBytes = 4_000_000L)
    val rawBytes = byteArrayOf(1, 2, 3, 4)
    val compressedBytes = byteArrayOf(9, 9)
    every { fileByteReader.readBytes(picked.uri) } returns rawBytes
    every { imageCompressor.compressToJpeg(rawBytes) } returns compressedBytes
    coEvery {
      blobs.put(any(), compressedBytes, contentType = "image/jpeg", scope = any())
    } returns buildBlobRef()

    val result =
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "My Photo")

    // Stored the compressed bytes with the JPEG content type…
    coVerify(exactly = 1) {
      blobs.put(any(), compressedBytes, contentType = "image/jpeg", scope = any())
    }
    // …and the returned proto reflects the compressed file, not the picked original.
    assertThat(result.mime_type).isEqualTo("image/jpeg")
    assertThat(result.size_bytes).isEqualTo(compressedBytes.size.toLong())
    assertThat(result.name).isEqualTo("My Photo.jpg")
    assertThat(result.type.name).isEqualTo("ATTACHMENT_TYPE_IMAGE")
  }

  @Test
  fun addPickedFile_photoCompressorDeclines_storesOriginalBytes() = runTest {
    val picked = buildPickedFile(mimeType = "image/jpeg")
    val rawBytes = byteArrayOf(1, 2, 3, 4)
    every { fileByteReader.readBytes(picked.uri) } returns rawBytes
    every { imageCompressor.compressToJpeg(rawBytes) } returns null
    coEvery {
      blobs.put(any(), rawBytes, contentType = "image/jpeg", scope = any())
    } returns buildBlobRef()

    val result =
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "photo.jpg")

    coVerify(exactly = 1) {
      blobs.put(any(), rawBytes, contentType = "image/jpeg", scope = any())
    }
    assertThat(result.name).isEqualTo("photo.jpg")
    assertThat(result.size_bytes).isEqualTo(rawBytes.size.toLong())
  }

  @Test
  fun addPickedFile_nonPhotoMime_neverCallsCompressor() = runTest {
    val picked = buildPickedFile(mimeType = "application/pdf", name = "doc.pdf")
    every { fileByteReader.readBytes(any()) } returns byteArrayOf(1, 2, 3)
    coEvery {
      blobs.put(any(), any(), contentType = any(), scope = any())
    } returns buildBlobRef()

    manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "doc.pdf")

    io.mockk.verify(exactly = 0) { imageCompressor.compressToJpeg(any()) }
  }

  @Test
  fun addPickedFile_pngIsNotCompressed_storedAsPng() = runTest {
    // PNG is deliberately excluded from compression (alpha flattening), so it passes through.
    val picked = buildPickedFile(mimeType = "image/png", name = "chart.png")
    val rawBytes = byteArrayOf(1, 2, 3)
    every { fileByteReader.readBytes(any()) } returns rawBytes
    coEvery {
      blobs.put(any(), rawBytes, contentType = "image/png", scope = any())
    } returns buildBlobRef()

    val result =
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "chart.png")

    io.mockk.verify(exactly = 0) { imageCompressor.compressToJpeg(any()) }
    assertThat(result.mime_type).isEqualTo("image/png")
    assertThat(result.name).isEqualTo("chart.png")
  }

  @Test
  fun addPickedFile_photoStillOverCapAfterCompression_throwsFileTooLarge() = runTest {
    val picked = buildPickedFile(mimeType = "image/jpeg", sizeBytes = 9_000_000L)
    val rawBytes = byteArrayOf(1, 2, 3)
    // Compressor hands back something still over the per-file cap.
    val stillHuge = ByteArray((5L * 1024 * 1024 + 1).toInt())
    every { fileByteReader.readBytes(any()) } returns rawBytes
    every { imageCompressor.compressToJpeg(rawBytes) } returns stillHuge

    var caught: Throwable? = null
    try {
      manager.addPickedFile(TEST_AIRCRAFT_ID, picked, displayName = "big.jpg")
    } catch (e: FileTooLargeException) {
      caught = e
    }

    assertThat(caught).isNotNull()
    coVerify(exactly = 0) { blobs.put(any(), any(), contentType = any(), scope = any()) }
  }

  // ---- makeLink ----

  @Test
  fun makeLink_buildsLinkAttachment_withTypeLink_emptyStoragePathAndSha256_andSuppliedUrl() {
    val url = "https://example.com/doc"
    val result = manager.makeLink(url, displayName = "Example Doc")

    assertThat(result.type.name).isEqualTo("ATTACHMENT_TYPE_LINK")
    assertThat(result.storage_path).isEmpty()
    assertThat(result.sha256).isEmpty()
    assertThat(result.url).isEqualTo(url)
    assertThat(result.name).isEqualTo("Example Doc")
  }

  @Test
  fun makeLink_populatesCreatedAtFromClock() {
    val result = manager.makeLink("https://example.com", displayName = "Link")

    assertThat(result.created_at).isNotNull()
    assertThat(result.created_at!!.getEpochSecond()).isEqualTo(
      FIXED_EPOCH_SECONDS
    )
  }

  // ---- delete ----

  @Test
  fun delete_isNoOp_forLinkAttachment() = runTest {
    val picked = manager.makeLink("https://example.com", displayName = "Link")

    manager.delete(picked)

    coVerify(exactly = 0) { blobs.delete(any()) }
    verify(exactly = 0) { uploadScheduler.scheduleDelete(any()) }
  }

  @Test
  fun delete_schedulesRemoteDeleteImmediately() = runTest {
    // The tombstone alone waits for the next startup sweep; deleting in-session must kick the
    // delete driver now so the remote object is reclaimed without an app restart.
    val fakeBytes = byteArrayOf(1)
    every { fileByteReader.readBytes(any()) } returns fakeBytes
    coEvery {
      blobs.put(any(), any(), contentType = any(), scope = any())
    } returns buildBlobRef()
    val attachment = manager.addPickedFile(
      TEST_AIRCRAFT_ID,
      buildPickedFile(),
      displayName = "file"
    )

    manager.delete(attachment)

    verify(exactly = 1) { uploadScheduler.scheduleDelete(BlobId(attachment.id)) }
  }

  @Test
  fun delete_callsBlobsDelete_withBlobIdMatchingAttachmentId() = runTest {
    val fakeBytes = byteArrayOf(1)
    every { fileByteReader.readBytes(any()) } returns fakeBytes
    coEvery {
      blobs.put(
        any(),
        any(),
        contentType = any(),
        scope = any()
      )
    } returns buildBlobRef()
    val attachment = manager.addPickedFile(
      TEST_AIRCRAFT_ID,
      buildPickedFile(),
      displayName = "file"
    )

    manager.delete(attachment)

    coVerify(exactly = 1) { blobs.delete(BlobId(attachment.id)) }
  }

  // ---- observeStatus ----

  @Test
  fun observeStatus_localOnly_emitsLocalOnly() = runTest {
    val id = "att-local"
    every { blobs.observe(BlobId(id)) } returns flowOf(buildBlobRef(remoteState = RemoteState.LocalOnly))

    val status = manager.observeStatus(id)
      .first()

    assertThat(status).isEqualTo(AttachmentStatus.LocalOnly)
  }

  @Test
  fun observeStatus_uploading_emitsUploadingWithZeroProgress() = runTest {
    val id = "att-uploading"
    every { blobs.observe(BlobId(id)) } returns flowOf(buildBlobRef(remoteState = RemoteState.Uploading))

    val status = manager.observeStatus(id)
      .first()

    assertThat(status).isInstanceOf(AttachmentStatus.Uploading::class.java)
    assertThat((status as AttachmentStatus.Uploading).progress).isEqualTo(0f)
  }

  @Test
  fun observeStatus_synced_emitsSynced() = runTest {
    val id = "att-synced"
    every { blobs.observe(BlobId(id)) } returns flowOf(buildBlobRef(remoteState = RemoteState.Synced))

    val status = manager.observeStatus(id)
      .first()

    assertThat(status).isEqualTo(AttachmentStatus.Synced)
  }

  @Test
  fun observeStatus_remoteOnly_emitsRemoteOnly() = runTest {
    val id = "att-remote"
    every { blobs.observe(BlobId(id)) } returns flowOf(buildBlobRef(remoteState = RemoteState.RemoteOnly))

    val status = manager.observeStatus(id)
      .first()

    assertThat(status).isEqualTo(AttachmentStatus.RemoteOnly)
  }

  @Test
  fun observeStatus_nullBlobRef_emitsRemoteOnly() = runTest {
    val id = "att-unknown"
    every { blobs.observe(BlobId(id)) } returns flowOf(null)

    val status = manager.observeStatus(id)
      .first()

    assertThat(status).isEqualTo(AttachmentStatus.RemoteOnly)
  }

  // ---- ensureLocal ----

  @Test
  fun ensureLocal_syncedRow_emitsDoneOnly() = runTest {
    val id = "att-synced"
    every { blobs.observe(BlobId(id)) } returns flowOf(
      buildBlobRef(remoteState = RemoteState.Synced)
    )

    val states = manager.ensureLocal(buildAttachment(id = id))
      .toList()

    assertThat(states).containsExactly(DownloadState.Done)
  }

  @Test
  fun ensureLocal_remoteOnlyRowThatNeverResolves_emitsFailedAfterTimeout() =
    runTest {
      val id = "att-stuck"
      // A hot flow that never completes and never emits again — mirrors the real DB-backed
      // observe() when the download driver keeps failing/retrying without ever finishing.
      every { blobs.observe(BlobId(id)) } returns MutableStateFlow(
        buildBlobRef(remoteState = RemoteState.RemoteOnly)
      )

      val states = manager.ensureLocal(buildAttachment(id = id))
        .toList()

      assertThat(states.last()).isInstanceOf(DownloadState.Failed::class.java)
    }

  @Test
  fun ensureLocal_missingRowThatNeverGetsIndexed_emitsFailedAfterTimeout() =
    runTest {
      val id = "att-missing"
      // No blob_object row ever appears — e.g. a reconciler bug that skips this entity kind.
      every { blobs.observe(BlobId(id)) } returns MutableStateFlow(null)

      val states =
        manager.ensureLocal(buildAttachment(id = id, sha256 = TEST_SHA256))
          .toList()

      assertThat(states.last()).isInstanceOf(DownloadState.Failed::class.java)
    }
}
