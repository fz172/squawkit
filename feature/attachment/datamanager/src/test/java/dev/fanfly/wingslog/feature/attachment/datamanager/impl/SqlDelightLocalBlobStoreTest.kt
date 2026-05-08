package dev.fanfly.wingslog.feature.attachment.datamanager.impl

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.createWingsLogDatabase
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.attachment.datamanager.FileBlobFilesystem
import dev.fanfly.wingslog.feature.attachment.datamanager.IntegrityError
import dev.fanfly.wingslog.feature.attachment.datamanager.sha256Hex
import java.io.File
import java.nio.file.Files
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightLocalBlobStoreTest {

  private val ioContext = UnconfinedTestDispatcher()
  private val scopeA = EntityScope.userRoot("user-A")
  private val scopeB = EntityScope.userRoot("user-B")

  private lateinit var rootDir: File
  private lateinit var db: WingsLogDatabase
  private lateinit var fs: FileBlobFilesystem
  private lateinit var clock: TestClock
  private lateinit var store: SqlDelightLocalBlobStore

  @Before
  fun setUp() {
    rootDir = Files.createTempDirectory("blob-test").toFile()
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.create(driver)
    db = createWingsLogDatabase(driver)
    fs = FileBlobFilesystem(rootDir)
    clock = TestClock(Instant.fromEpochMilliseconds(1_000L))
    store = SqlDelightLocalBlobStore(db, fs, ioContext, clock)
  }

  @After
  fun tearDown() {
    rootDir.deleteRecursively()
  }

  // ---- put ----

  @Test
  fun put_writes_file_and_row_in_LocalOnly() = runTest(ioContext) {
    val id = BlobId("att-1")
    val bytes = "hello world".toByteArray()

    val ref = store.put(id, bytes, contentType = "text/plain", scope = scopeA)

    assertThat(ref.id).isEqualTo(id)
    assertThat(ref.sizeBytes).isEqualTo(bytes.size.toLong())
    assertThat(ref.sha256).isEqualTo(sha256Hex(bytes))
    assertThat(ref.contentType).isEqualTo("text/plain")
    assertThat(ref.remoteState).isEqualTo(RemoteState.LocalOnly)
    assertThat(ref.remotePath).isNull()
    assertThat(ref.deleted).isFalse()

    // file is on disk
    assertThat(File(rootDir, "blobs/att-1.bin").readBytes()).isEqualTo(bytes)
    // localUri is non-null and resolves to that file
    val uri = store.localUri(id)!!
    assertThat(uri).startsWith("file:")
    assertThat(uri).contains("blobs/att-1.bin")
  }

  @Test
  fun get_returns_matching_BlobRef_after_put() = runTest(ioContext) {
    val id = BlobId("att-2")
    store.put(id, "x".toByteArray(), null, scopeA)

    val ref = store.get(id)!!
    assertThat(ref.id).isEqualTo(id)
    assertThat(ref.remoteState).isEqualTo(RemoteState.LocalOnly)
  }

  // ---- upsertRemoteOnly ----

  @Test
  fun upsertRemoteOnly_inserts_RemoteOnly_row() = runTest(ioContext) {
    val id = BlobId("att-r")
    store.upsertRemoteOnly(id, sha256 = "abc", sizeBytes = 99L, contentType = "image/png", scope = scopeA)

    val ref = store.get(id)!!
    assertThat(ref.remoteState).isEqualTo(RemoteState.RemoteOnly)
    assertThat(ref.sizeBytes).isEqualTo(99L)
    assertThat(ref.sha256).isEqualTo("abc")
    assertThat(ref.contentType).isEqualTo("image/png")
    assertThat(ref.remotePath).isEqualTo("users/user-A/blobs/att-r")
    // localUri is null — no file on disk
    assertThat(store.localUri(id)).isNull()
  }

  @Test
  fun upsertRemoteOnly_does_not_overwrite_an_existing_synced_row() = runTest(ioContext) {
    val id = BlobId("att-3")
    store.put(id, "bytes".toByteArray(), null, scopeA)
    store.markUploading(id)
    store.markUploaded(id, remotePath = "users/user-A/blobs/att-3")

    // Reconciler-style call — should be a no-op.
    store.upsertRemoteOnly(id, sha256 = "different", sizeBytes = 1L, contentType = null, scope = scopeA)

    val ref = store.get(id)!!
    assertThat(ref.remoteState).isEqualTo(RemoteState.Synced)
    assertThat(ref.sha256).isEqualTo(sha256Hex("bytes".toByteArray()))
  }

  // ---- state transitions ----

  @Test
  fun upload_happy_path_transitions_LocalOnly_to_Uploading_to_Synced() = runTest(ioContext) {
    val id = BlobId("att-4")
    store.put(id, "z".toByteArray(), null, scopeA)

    store.markUploading(id)
    assertThat(store.get(id)!!.remoteState).isEqualTo(RemoteState.Uploading)

    store.markUploaded(id, remotePath = "users/user-A/blobs/att-4")
    val ref = store.get(id)!!
    assertThat(ref.remoteState).isEqualTo(RemoteState.Synced)
    assertThat(ref.remotePath).isEqualTo("users/user-A/blobs/att-4")
  }

  @Test
  fun markFailedTransient_reverts_to_LocalOnly_and_increments_attempts() = runTest(ioContext) {
    val id = BlobId("att-5")
    store.put(id, "y".toByteArray(), null, scopeA)
    store.markUploading(id)

    store.markFailedTransient(id)

    assertThat(store.get(id)!!.remoteState).isEqualTo(RemoteState.LocalOnly)
    val raw = db.schemaQueries.selectBlobById(id.value).executeAsOne()
    assertThat(raw.upload_attempts).isEqualTo(1L)
    assertThat(raw.last_attempt_at).isNotNull()
  }

  @Test
  fun invalid_transition_markUploaded_from_LocalOnly_throws() = runTest(ioContext) {
    val id = BlobId("att-6")
    store.put(id, "y".toByteArray(), null, scopeA)

    var threw = false
    try {
      store.markUploaded(id, remotePath = "x")
    } catch (e: IllegalArgumentException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }

  @Test
  fun invalid_transition_markUploading_from_RemoteOnly_throws() = runTest(ioContext) {
    val id = BlobId("att-7")
    store.upsertRemoteOnly(id, "abc", 1L, null, scopeA)

    var threw = false
    try {
      store.markUploading(id)
    } catch (e: IllegalArgumentException) {
      threw = true
    }
    assertThat(threw).isTrue()
  }

  // ---- installDownloaded ----

  @Test
  fun installDownloaded_with_matching_sha_flips_to_Synced() = runTest(ioContext) {
    val id = BlobId("att-8")
    val bytes = "downloaded payload".toByteArray()
    val expectedSha = sha256Hex(bytes)
    store.upsertRemoteOnly(id, sha256 = expectedSha, sizeBytes = bytes.size.toLong(), contentType = null, scope = scopeA)

    val result = store.installDownloaded(id, bytes, expectedSha)

    assertThat(result.isSuccess).isTrue()
    assertThat(store.get(id)!!.remoteState).isEqualTo(RemoteState.Synced)
    assertThat(File(rootDir, "blobs/att-8.bin").readBytes()).isEqualTo(bytes)
    assertThat(store.localUri(id)).isNotNull()
  }

  @Test
  fun installDownloaded_with_mismatched_sha_returns_failure_and_writes_nothing() = runTest(ioContext) {
    val id = BlobId("att-9")
    val bytes = "tampered".toByteArray()
    val claimedSha = "0".repeat(64)
    store.upsertRemoteOnly(id, sha256 = claimedSha, sizeBytes = bytes.size.toLong(), contentType = null, scope = scopeA)

    val result = store.installDownloaded(id, bytes, claimedSha)

    assertThat(result.isFailure).isTrue()
    assertThat(result.exceptionOrNull()).isInstanceOf(IntegrityError::class.java)
    // Row stays REMOTE_ONLY, file not written.
    assertThat(store.get(id)!!.remoteState).isEqualTo(RemoteState.RemoteOnly)
    assertThat(File(rootDir, "blobs/att-9.bin").exists()).isFalse()
  }

  // ---- delete ----

  @Test
  fun delete_after_synced_removes_file_and_tombstones_row() = runTest(ioContext) {
    val id = BlobId("att-10")
    store.put(id, "data".toByteArray(), null, scopeA)
    store.markUploading(id)
    store.markUploaded(id, "users/user-A/blobs/att-10")
    val file = File(rootDir, "blobs/att-10.bin")
    assertThat(file.exists()).isTrue()

    store.delete(id)

    assertThat(file.exists()).isFalse()
    val row = db.schemaQueries.selectBlobById(id.value).executeAsOne()
    assertThat(row.deleted).isTrue() // tombstone, not hard-delete
  }

  @Test
  fun delete_of_unknown_id_is_noop() = runTest(ioContext) {
    store.delete(BlobId("ghost"))
    // No exception, no row.
    assertThat(db.schemaQueries.selectBlobById("ghost").executeAsOneOrNull()).isNull()
  }

  // ---- scope isolation ----

  @Test
  fun rows_in_one_scope_do_not_leak_into_another() = runTest(ioContext) {
    store.put(BlobId("a-only"), "a".toByteArray(), null, scopeA)
    store.put(BlobId("b-only"), "b".toByteArray(), null, scopeB)

    val rowA = db.schemaQueries.selectBlobById("a-only").executeAsOne()
    val rowB = db.schemaQueries.selectBlobById("b-only").executeAsOne()
    assertThat(rowA.scope_path).isEqualTo("/users/user-A/")
    assertThat(rowB.scope_path).isEqualTo("/users/user-B/")

    val sumA = db.schemaQueries.sumBlobSizeInScope("/users/user-A/").executeAsOne()
    val sumB = db.schemaQueries.sumBlobSizeInScope("/users/user-B/").executeAsOne()
    assertThat(sumA).isEqualTo(1L)
    assertThat(sumB).isEqualTo(1L)
  }

  // ---- observe ----

  @Test
  fun observe_emits_initial_state_then_updates() = runTest(ioContext) {
    val id = BlobId("att-obs")
    store.put(id, "obs".toByteArray(), null, scopeA)

    val first = store.observe(id).first()
    assertThat(first?.remoteState).isEqualTo(RemoteState.LocalOnly)

    store.markUploading(id)

    val updated = store.observe(id).first()
    assertThat(updated?.remoteState).isEqualTo(RemoteState.Uploading)
  }

  @Test
  fun observe_unknown_id_emits_null() = runTest(ioContext) {
    val ref = store.observe(BlobId("nope")).first()
    assertThat(ref).isNull()
  }
}

@OptIn(kotlin.time.ExperimentalTime::class)
class TestClock(initial: Instant) : Clock {
  private var currentMs: Long = initial.toEpochMilliseconds()
  override fun now(): Instant = Instant.fromEpochMilliseconds(currentMs)
  fun advanceBy(ms: Long) {
    currentMs += ms
  }
}
