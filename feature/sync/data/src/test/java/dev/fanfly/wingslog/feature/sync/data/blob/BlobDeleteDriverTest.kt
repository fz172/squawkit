package dev.fanfly.wingslog.feature.sync.data.blob

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.db.SchemaQueries
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.StorageReference
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BlobDeleteDriverTest {

  private lateinit var blobs: LocalBlobStore
  private lateinit var storage: FirebaseStorage
  private lateinit var db: WingsLogDatabase
  private lateinit var schemaQueries: SchemaQueries
  private lateinit var auth: FirebaseAuth
  private lateinit var driver: BlobDeleteDriver

  @Before
  fun setUp() {
    blobs = mockk()
    storage = mockk()
    schemaQueries = mockk(relaxed = true)
    db = mockk()
    every { db.schemaQueries } returns schemaQueries
    // The blobRef scope is owned by "u1"; signing in as "u1" keeps these blobs own-tree, so the
    // driver takes the direct Firebase Storage delete path (foreign-host skipping is its own test).
    val user = mockk<FirebaseUser> { every { uid } returns "u1" }
    auth = mockk { every { currentUser } returns user }
    driver = BlobDeleteDriver(blobs, storage, db, auth, DatabaseWriteLock())
  }

  private fun blobRef(
    id: String = BLOB_ID,
    remoteState: RemoteState = RemoteState.RemoteOnly,
    remotePath: String? = REMOTE_PATH,
    deleted: Boolean = true,
    ownerUid: String = "u1",
  ) = BlobRef(
    id = BlobId(id),
    scope = EntityScope.aircraftChildUnsafe(ownerUid, "ac1"),
    relativePath = "blobs/$id.bin",
    sizeBytes = 10L,
    sha256 = "sha",
    contentType = "image/jpeg",
    remoteState = remoteState,
    remotePath = remotePath,
    uploadAttempts = 0L,
    deleted = deleted,
    updatedAt = Instant.fromEpochSeconds(0),
  )

  @Test
  fun runOnce_remoteOnlyTombstone_deletesRemoteObjectAndHardDeletesRow() = runTest {
    // The regression this driver missed: a RemoteOnly blob (known from a synced record but never
    // downloaded to this device, e.g. after a reinstall) must still have its gs:// object removed.
    val ref = blobRef(remoteState = RemoteState.RemoteOnly)
    val storageRef = mockk<StorageReference>()
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns ref
    every { storage.reference(REMOTE_PATH) } returns storageRef
    coEvery { storageRef.delete() } just Runs

    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    coVerify(exactly = 1) { storageRef.delete() }
    coVerify(exactly = 1) { schemaQueries.hardDeleteBlob(BLOB_ID) }
  }

  @Test
  fun runOnce_syncedTombstone_stillDeletesRemoteObject() = runTest {
    val ref = blobRef(remoteState = RemoteState.Synced)
    val storageRef = mockk<StorageReference>()
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns ref
    every { storage.reference(REMOTE_PATH) } returns storageRef
    coEvery { storageRef.delete() } just Runs

    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    coVerify(exactly = 1) { storageRef.delete() }
    coVerify(exactly = 1) { schemaQueries.hardDeleteBlob(BLOB_ID) }
  }

  @Test
  fun runOnce_localOnlyTombstone_skipsRemoteDeleteAndHardDeletesRow() = runTest {
    // LocalOnly never uploaded — remotePath is null and there is nothing in gs:// to remove.
    val ref = blobRef(remoteState = RemoteState.LocalOnly, remotePath = null)
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns ref

    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    verify(exactly = 0) { storage.reference(any()) }
    coVerify(exactly = 1) { schemaQueries.hardDeleteBlob(BLOB_ID) }
  }

  @Test
  fun runOnce_foreignHostedTombstone_skipsRemoteDeleteButDropsRow() = runTest {
    // A member has no write rights to the host's tree and the broker has no delete door, so a direct
    // delete would only earn a permanent PERMISSION_DENIED. Drop the local row; the host reclaims the
    // remote object via its own deletion cascade / sweep (§9.6, P8.6).
    val ref = blobRef(remoteState = RemoteState.Synced, ownerUid = "host-uid")
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns ref

    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    verify(exactly = 0) { storage.reference(any()) }
    coVerify(exactly = 1) { schemaQueries.hardDeleteBlob(BLOB_ID) }
  }

  @Test
  fun runOnce_transientRemoteFailure_returnsFalseAndKeepsRow() = runTest {
    val ref = blobRef(remoteState = RemoteState.RemoteOnly)
    val storageRef = mockk<StorageReference>()
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns ref
    every { storage.reference(REMOTE_PATH) } returns storageRef
    coEvery { storageRef.delete() } throws RuntimeException("network down")

    val result = driver.runOnce(BlobId(BLOB_ID))

    // Not deleted remotely → do not drop the row; a retry must be possible.
    assertThat(result).isFalse()
    coVerify(exactly = 0) { schemaQueries.hardDeleteBlob(any()) }
  }

  @Test
  fun runOnce_alreadyGoneRemoteObject_treatedAsSuccess() = runTest {
    val ref = blobRef(remoteState = RemoteState.RemoteOnly)
    val storageRef = mockk<StorageReference>()
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns ref
    every { storage.reference(REMOTE_PATH) } returns storageRef
    coEvery { storageRef.delete() } throws RuntimeException("Object not found (404)")

    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    coVerify(exactly = 1) { schemaQueries.hardDeleteBlob(BLOB_ID) }
  }

  @Test
  fun runOnce_notTombstoned_isNoOp() = runTest {
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns blobRef(deleted = false)

    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    verify(exactly = 0) { storage.reference(any()) }
    coVerify(exactly = 0) { schemaQueries.hardDeleteBlob(any()) }
  }

  @Test
  fun runOnce_missingRow_isNoOp() = runTest {
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns null

    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    verify(exactly = 0) { storage.reference(any()) }
    coVerify(exactly = 0) { schemaQueries.hardDeleteBlob(any()) }
  }

  private companion object {
    const val BLOB_ID = "blob-123"
    const val REMOTE_PATH = "users/u1/aircraft/ac1/blobs/blob-123"
  }
}
