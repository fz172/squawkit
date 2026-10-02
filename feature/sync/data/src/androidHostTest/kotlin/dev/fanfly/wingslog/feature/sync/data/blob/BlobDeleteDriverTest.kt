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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BlobDeleteDriverTest {

  private lateinit var blobs: LocalBlobStore
  private lateinit var db: WingsLogDatabase
  private lateinit var schemaQueries: SchemaQueries
  private lateinit var driver: BlobDeleteDriver

  @Before
  fun setUp() {
    blobs = mockk()
    schemaQueries = mockk(relaxed = true)
    db = mockk()
    every { db.schemaQueries } returns schemaQueries
    driver = BlobDeleteDriver(blobs, db, DatabaseWriteLock())
  }

  private fun blobRef(
    remoteState: RemoteState = RemoteState.Synced,
    remotePath: String? = REMOTE_PATH,
    deleted: Boolean = true,
    ownerUid: String = "u1",
  ) = BlobRef(
    id = BlobId(BLOB_ID),
    scope = EntityScope.thingChildUnsafe(ownerUid, "ac1"),
    relativePath = "blobs/$BLOB_ID.bin",
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
  fun runOnce_tombstoneInAnyRemoteState_dropsTheLocalRowOnly() = runTest {
    // Own tree or foreign, uploaded or not: the device only forgets the row. The driver holds no
    // Storage client, so the canonical bytes are the server's to collect once no record names them
    // (design §8.3), which is what keeps a document several tasks cite alive.
    for (state in ALL_STATES) {
      for (owner in listOf("u1", "host-uid")) {
        coEvery { blobs.get(BlobId(BLOB_ID)) } returns blobRef(
          remoteState = state,
          ownerUid = owner
        )

        assertThat(driver.runOnce(BlobId(BLOB_ID))).isTrue()
      }
    }
    coVerify(exactly = ALL_STATES.size * 2) {
      schemaQueries.hardDeleteBlob(
        BLOB_ID
      )
    }
  }

  @Test
  fun runOnce_notTombstoned_isNoOp() = runTest {
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns blobRef(deleted = false)

    assertThat(driver.runOnce(BlobId(BLOB_ID))).isTrue()
    coVerify(exactly = 0) { schemaQueries.hardDeleteBlob(any()) }
  }

  @Test
  fun runOnce_missingRow_isNoOp() = runTest {
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns null

    assertThat(driver.runOnce(BlobId(BLOB_ID))).isTrue()
    coVerify(exactly = 0) { schemaQueries.hardDeleteBlob(any()) }
  }

  private companion object {
    const val BLOB_ID = "blob-123"
    const val REMOTE_PATH = "users/u1/thing/ac1/blobs/blob-123"

    val ALL_STATES = listOf(
      RemoteState.LocalOnly,
      RemoteState.Uploading,
      RemoteState.Synced,
      RemoteState.RemoteOnly,
      RemoteState.RemoteMissing,
    )
  }
}
