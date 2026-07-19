package dev.fanfly.wingslog.feature.sync.data.blob

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.storage.FirebaseStorage
import io.ktor.client.HttpClient
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The own-vs-foreign routing P8.4 (#245) adds to the blob drivers: a blob whose owning tree is the
 * signed-in user goes straight to Firebase Storage; a foreign-hosted (shared-aircraft) blob crosses
 * through the [AttachmentBroker]. These assert the foreign branch reaches the broker and never
 * touches direct Storage (which `storage.rules` would deny cross-account anyway).
 */
@OptIn(ExperimentalTime::class)
class AttachmentBrokerRoutingTest {

  private val member = "member-uid"
  private val host = "host-uid"

  @Test
  fun blobLocation_ownVsForeign() {
    val own = BlobLocation.of(refAt(member))
    assertThat(own).isEqualTo(BlobLocation(member, "ac1"))
    assertThat(own!!.isForeign(member)).isFalse()

    val shared = BlobLocation.of(refAt(host))
    assertThat(shared!!.isForeign(member)).isTrue()
    // Signed out: nothing is "foreign" to nobody — never broker without a caller identity.
    assertThat(shared.isForeign(null)).isFalse()
  }

  @Test
  fun download_foreignHosted_goesThroughBrokerNotStorage() = runTest {
    val blobs = mockk<LocalBlobStore>()
    val storage = mockk<FirebaseStorage>()
    val broker = FakeAttachmentBroker().apply { downloadResult = byteArrayOf(1, 2, 3) }
    val ref = refAt(host, remoteState = RemoteState.RemoteOnly)
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns ref
    coEvery { blobs.installDownloaded(BlobId(BLOB_ID), any(), "sha") } returns Result.success(Unit)

    val driver = BlobDownloadDriver(
      blobs = blobs,
      storage = storage,
      httpClient = mockk<HttpClient>(),
      auth = authFor(member),
      broker = broker,
    )
    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    assertThat(broker.downloads).containsExactly(Call(host, "ac1", BLOB_ID))
    verify(exactly = 0) { storage.reference(any()) }
  }

  @Test
  fun upload_foreignHosted_goesThroughBrokerNotStorage() = runTest {
    val blobs = mockk<LocalBlobStore>(relaxed = true)
    val storage = mockk<FirebaseStorage>()
    val fs = mockk<BlobFilesystem>()
    val broker = FakeAttachmentBroker()
    val ref = refAt(host, remoteState = RemoteState.LocalOnly)
    coEvery { blobs.get(BlobId(BLOB_ID)) } returns ref
    coEvery { fs.read(ref.relativePath) } returns byteArrayOf(9, 9)

    val driver = BlobUploadDriver(
      blobs = blobs,
      storage = storage,
      auth = authFor(member),
      fs = fs,
      broker = broker,
    )
    val result = driver.runOnce(BlobId(BLOB_ID))

    assertThat(result).isTrue()
    assertThat(broker.uploads).containsExactly(Call(host, "ac1", BLOB_ID))
    verify(exactly = 0) { storage.reference(any()) }
  }

  private fun authFor(uid: String): FirebaseAuth {
    val user = mockk<FirebaseUser> {
      every { this@mockk.uid } returns uid
      every { isAnonymous } returns false
    }
    return mockk { every { currentUser } returns user }
  }

  private fun refAt(
    ownerUid: String,
    remoteState: RemoteState = RemoteState.RemoteOnly,
  ) = BlobRef(
    id = BlobId(BLOB_ID),
    scope = EntityScope.aircraftChild(ownerUid, "ac1"),
    relativePath = "blobs/$BLOB_ID.bin",
    sizeBytes = 3L,
    sha256 = "sha",
    contentType = "image/jpeg",
    remoteState = remoteState,
    remotePath = "users/$ownerUid/aircraft/ac1/blobs/$BLOB_ID",
    uploadAttempts = 0L,
    deleted = false,
    updatedAt = Instant.fromEpochSeconds(0),
  )

  private companion object {
    const val BLOB_ID = "blob-1"
  }
}

private data class Call(val hostUid: String, val aircraftId: String, val blobId: String)

private class FakeAttachmentBroker : AttachmentBroker {
  val uploads = mutableListOf<Call>()
  val downloads = mutableListOf<Call>()
  var downloadResult: ByteArray = ByteArray(0)

  override suspend fun upload(
    hostUid: String,
    aircraftId: String,
    blobId: String,
    contentType: String?,
    bytes: ByteArray,
  ) {
    uploads += Call(hostUid, aircraftId, blobId)
  }

  override suspend fun download(hostUid: String, aircraftId: String, blobId: String): ByteArray {
    downloads += Call(hostUid, aircraftId, blobId)
    return downloadResult
  }
}
