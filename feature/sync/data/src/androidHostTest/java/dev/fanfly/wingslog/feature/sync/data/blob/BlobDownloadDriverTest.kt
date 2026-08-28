package dev.fanfly.wingslog.feature.sync.data.blob

import com.google.common.truth.Truth.assertThat
import com.google.firebase.storage.StorageException
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.StorageReference
import io.ktor.client.HttpClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How a failed download is classified (#426).
 *
 * The distinction is the whole point: a 404 means the object is gone and retrying it forever kept
 * a dead blob waking WorkManager and stalling every export that touched it, while a network blip
 * genuinely does deserve a retry. Getting these two backwards is invisible until someone notices
 * an export that never finishes.
 */
@OptIn(ExperimentalTime::class)
class BlobDownloadDriverTest {

  private lateinit var blobs: LocalBlobStore
  private lateinit var storage: FirebaseStorage
  private lateinit var auth: FirebaseAuth
  private lateinit var broker: AttachmentBroker
  private lateinit var driver: BlobDownloadDriver

  @Before
  fun setUp() {
    blobs = mockk(relaxed = true)
    storage = mockk()
    broker = mockk()
    auth = mockk()
    val user = mockk<FirebaseUser>()
    every { user.uid } returns OWNER_UID
    every { auth.currentUser } returns user
    driver = BlobDownloadDriver(
      blobs = blobs,
      storage = storage,
      httpClient = HttpClient(),
      auth = auth,
      broker = broker,
    )
  }

  private fun blobRef() = BlobRef(
    id = BlobId(BLOB_ID),
    scope = EntityScope.aircraftChildUnsafe(OWNER_UID, "ac1"),
    relativePath = "blobs/$BLOB_ID.bin",
    sizeBytes = 10L,
    sha256 = "sha",
    contentType = "image/jpeg",
    remoteState = RemoteState.RemoteOnly,
    remotePath = REMOTE_PATH,
    uploadAttempts = 0L,
    deleted = false,
    updatedAt = Instant.fromEpochSeconds(0),
  )

  /** Storage answered "no object here". Retrying that forever is what #426 was. */
  @Test
  fun runOnce_objectNotFound_isPermanentAndStopsRetrying() = runTest {
    val storageRef = mockk<StorageReference>()
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns blobRef()
    every { storage.reference(REMOTE_PATH) } returns storageRef
    coEvery { storageRef.getDownloadUrl() } throws objectNotFound()

    val terminal = driver.runOnce(BlobId(BLOB_ID))

    // true == "do not reschedule me".
    assertThat(terminal).isTrue()
    coVerify { blobs.markRemoteMissing(BlobId(BLOB_ID), any()) }
  }

  /** The other half: a blip must still be retried, or a dropped connection loses the attachment. */
  @Test
  fun runOnce_networkFailure_staysTransientAndRetries() = runTest {
    val storageRef = mockk<StorageReference>()
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns blobRef()
    every { storage.reference(REMOTE_PATH) } returns storageRef
    coEvery { storageRef.getDownloadUrl() } throws java.io.IOException("connection reset")

    val terminal = driver.runOnce(BlobId(BLOB_ID))

    assertThat(terminal).isFalse()
    coVerify(exactly = 0) { blobs.markRemoteMissing(any(), any()) }
  }

  /**
   * The 404 arrives wrapped by the Storage SDK rather than thrown bare, so the check has to walk
   * the cause chain — matching only the top-level exception would silently miss it.
   */
  @Test
  fun runOnce_objectNotFoundNestedInCause_isStillPermanent() = runTest {
    val storageRef = mockk<StorageReference>()
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns blobRef()
    every { storage.reference(REMOTE_PATH) } returns storageRef
    coEvery { storageRef.getDownloadUrl() } throws
      RuntimeException("download failed", objectNotFound())

    assertThat(driver.runOnce(BlobId(BLOB_ID))).isTrue()
    coVerify { blobs.markRemoteMissing(BlobId(BLOB_ID), any()) }
  }

  /**
   * What Firebase Storage throws for HTTP 404.
   *
   * Mocked rather than constructed: `StorageException`'s constructors are internal, and
   * `fromErrorStatus` rejects a raw -13010 because it expects a Play Services status code. The
   * mock still satisfies the `is StorageException` check, which is what the classifier reads.
   */
  private fun objectNotFound(): StorageException =
    mockk<StorageException>(relaxed = true) {
      every { errorCode } returns StorageException.ERROR_OBJECT_NOT_FOUND
      every { cause } returns null
    }

  private companion object {
    private const val BLOB_ID = "blob-1"
    private const val OWNER_UID = "owner-1"
    private const val REMOTE_PATH = "users/owner-1/aircraft/ac1/blobs/blob-1"
  }
}
