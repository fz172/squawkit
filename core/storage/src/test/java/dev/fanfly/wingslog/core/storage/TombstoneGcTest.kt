package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.junit.Before
import org.junit.Test

private const val UID = "u1"
private const val AIRCRAFT_ID = "ac-1"
private const val SHA = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"

private val NOW = Instant.fromEpochMilliseconds(100.days.inWholeMilliseconds)
private val LONG_AGO = NOW - 31.days
private val RECENTLY = NOW - 3.days

private val USER_ROOT = EntityScope.userRoot(UID)
  .toPath()
private val AIRCRAFT_SCOPE = EntityScope.aircraftChildUnsafe(UID, AIRCRAFT_ID)
  .toPath()

@OptIn(ExperimentalTime::class)
class TombstoneGcTest {

  private lateinit var db: WingsLogDatabase
  private lateinit var blobs: RecordingBlobStore
  private lateinit var gc: TombstoneGc

  @Before
  fun setUp() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    WingsLogDatabase.Schema.synchronous()
      .create(driver)
    db = createWingsLogDatabase(driver)
    blobs = RecordingBlobStore()
    gc = TombstoneGc(db = db, blobs = blobs)
  }

  // ---- retention ----

  @Test
  fun purgesTombstonesPastRetention_keepsRecentOnes() = runTest {
    putLog(id = "old", at = LONG_AGO, deleted = true)
    putLog(id = "recent", at = RECENTLY, deleted = true)

    gc.runOnce(NOW)

    assertThat(logExists("old")).isFalse()
    assertThat(logExists("recent")).isTrue()
  }

  @Test
  fun keepsUnpushedDeletes_soTheyStillReachFirestore() = runTest {
    putLog(id = "offline-delete", at = LONG_AGO, deleted = true, dirty = true)

    gc.runOnce(NOW)

    assertThat(logExists("offline-delete")).isTrue()
    assertThat(blobs.purged).isEmpty()
  }

  @Test
  fun keepsLiveRecords() = runTest {
    putLog(id = "live", at = LONG_AGO, deleted = false)

    gc.runOnce(NOW)

    assertThat(logExists("live")).isTrue()
  }

  // ---- blob reclamation ----

  @Test
  fun reclaimsBlobsOfPurgedRecord() = runTest {
    putLog(id = "old", at = LONG_AGO, deleted = true, attachmentIds = listOf("blob-a", "blob-b"))

    gc.runOnce(NOW)

    assertThat(blobs.purged).containsExactly(BlobId("blob-a"), BlobId("blob-b"))
  }

  @Test
  fun leavesBlobsOfTombstonesNotYetPurgeable() = runTest {
    putLog(id = "recent", at = RECENTLY, deleted = true, attachmentIds = listOf("blob-a"))

    gc.runOnce(NOW)

    assertThat(blobs.purged).isEmpty()
  }

  @Test
  fun keepsABlobALiveRecordStillReferences() = runTest {
    putLog(id = "old", at = LONG_AGO, deleted = true, attachmentIds = listOf("shared", "only-mine"))
    // A duplicate/copy can put the same attachment id on a live record. Its bytes must survive.
    putLog(id = "copy", at = RECENTLY, deleted = false, attachmentIds = listOf("shared"))

    gc.runOnce(NOW)

    assertThat(blobs.purged).containsExactly(BlobId("only-mine"))
  }

  @Test
  fun keepsABlobALiveRecordInAnotherAircraftStillReferences() = runTest {
    putLog(id = "old", at = LONG_AGO, deleted = true, attachmentIds = listOf("copied"))
    // Same attachment id, different aircraft — a copy across the fleet. The live one wins.
    putEntity(
      kind = CollectionKind.MaintenanceLog,
      scope = EntityScope.aircraftChildUnsafe(UID, "ac-2")
        .toPath(),
      id = "copy",
      payload = MaintenanceLog(
        id = "copy",
        attachments = listOf(Attachment(id = "copied", sha256 = SHA)),
      ).encode(),
      at = RECENTLY,
      deleted = false,
    )

    gc.runOnce(NOW)

    assertThat(blobs.purged).isEmpty()
  }

  /** The cascade (server-side) tombstones the children; the client pulls them down as deleted rows. */
  @Test
  fun reclaimsBlobsOfCascadeProducedChildTombstones() = runTest {
    putAircraft(at = LONG_AGO, deleted = true)
    putLog(id = "child", at = LONG_AGO, deleted = true, attachmentIds = listOf("blob-a"))

    gc.runOnce(NOW)

    assertThat(aircraftExists()).isFalse()
    assertThat(logExists("child")).isFalse()
    assertThat(blobs.purged).contains(BlobId("blob-a"))
  }

  /** Blobs are aircraft-scoped: a deleted aircraft takes its whole blob prefix with it (§5.2). */
  @Test
  fun purgedAircraftReclaimsEveryBlobInItsScope_evenOnesNoPayloadNames() = runTest {
    putAircraft(at = LONG_AGO, deleted = true)
    blobs.rows[BlobId("orphan")] = AIRCRAFT_SCOPE

    gc.runOnce(NOW)

    assertThat(blobs.purged).containsExactly(BlobId("orphan"))
  }

  @Test
  fun purgedAircraftLeavesAnotherAircraftsBlobsAlone() = runTest {
    putAircraft(at = LONG_AGO, deleted = true)
    blobs.rows[BlobId("mine")] = AIRCRAFT_SCOPE
    blobs.rows[BlobId("theirs")] = EntityScope.aircraftChildUnsafe(UID, "ac-2")
      .toPath()

    gc.runOnce(NOW)

    assertThat(blobs.purged).containsExactly(BlobId("mine"))
  }

  @Test
  fun aCorruptPayloadKeepsItsBlobsAndDoesNotStopTheSweep() = runTest {
    putEntity(
      kind = CollectionKind.MaintenanceLog,
      scope = AIRCRAFT_SCOPE,
      id = "corrupt",
      payload = byteArrayOf(0x08, 0x08, 0x08, 0x7F),
      at = LONG_AGO,
      deleted = true,
    )
    putLog(id = "old", at = LONG_AGO, deleted = true, attachmentIds = listOf("blob-a"))

    gc.runOnce(NOW)

    assertThat(blobs.purged).containsExactly(BlobId("blob-a"))
    assertThat(logExists("corrupt")).isFalse()
    assertThat(logExists("old")).isFalse()
  }

  @Test
  fun withoutABlobStore_stillPurgesTombstones() = runTest {
    TombstoneGc(db = db, blobs = null).let { gcWithoutBlobs ->
      putLog(id = "old", at = LONG_AGO, deleted = true, attachmentIds = listOf("blob-a"))

      gcWithoutBlobs.runOnce(NOW)

      assertThat(logExists("old")).isFalse()
    }
  }

  // ---- fixtures ----

  /** Row-level existence — [selectOneForSync] returns tombstoned rows too, which is the point here. */
  private suspend fun logExists(id: String): Boolean =
    db.schemaQueries.selectOneForSync(CollectionKind.MaintenanceLog, AIRCRAFT_SCOPE, id)
      .awaitAsOneOrNull() != null

  private suspend fun aircraftExists(): Boolean =
    db.schemaQueries.selectOneForSync(CollectionKind.Aircraft, USER_ROOT, AIRCRAFT_ID)
      .awaitAsOneOrNull() != null

  private suspend fun putLog(
    id: String,
    at: Instant,
    deleted: Boolean,
    dirty: Boolean = false,
    attachmentIds: List<String> = emptyList(),
  ) {
    val payload = MaintenanceLog(
      id = id,
      attachments = attachmentIds.map { Attachment(id = it, sha256 = SHA) },
    ).encode()
    putEntity(CollectionKind.MaintenanceLog, AIRCRAFT_SCOPE, id, payload, at, deleted, dirty)
  }

  private suspend fun putAircraft(at: Instant, deleted: Boolean) {
    putEntity(
      kind = CollectionKind.Aircraft,
      scope = USER_ROOT,
      id = AIRCRAFT_ID,
      payload = Aircraft(id = AIRCRAFT_ID).encode(),
      at = at,
      deleted = deleted,
    )
  }

  private suspend fun putEntity(
    kind: CollectionKind,
    scope: String,
    id: String,
    payload: ByteArray,
    at: Instant,
    deleted: Boolean,
    dirty: Boolean = false,
  ) {
    db.schemaQueries.upsert(
      collection = kind,
      scope_path = scope,
      id = id,
      payload = payload,
      payload_schema = kind.schemaName,
      updated_at = at.toEpochMilliseconds(),
      remote_updated_at = at.toEpochMilliseconds(),
      dirty = dirty,
      deleted = deleted,
      writer_uid = null,
    )
  }
}

/**
 * Records what the GC asked to be reclaimed. Only the two methods [TombstoneGc] uses are real —
 * anything else it starts calling should fail loudly rather than pass silently.
 */
private class RecordingBlobStore : LocalBlobStore {

  /** blob id → scope path, standing in for the `blob_object` table. */
  val rows = mutableMapOf<BlobId, String>()
  val purged = mutableListOf<BlobId>()

  override suspend fun idsInScopePrefix(scopePrefix: String): List<BlobId> {
    val prefix = scopePrefix.removeSuffix("%")
    return rows.filterValues { it.startsWith(prefix) }
      .keys
      .toList()
  }

  override suspend fun purgeLocal(ids: Collection<BlobId>) {
    purged += ids
    rows -= ids.toSet()
  }

  override suspend fun put(
    id: BlobId,
    bytes: ByteArray,
    contentType: String?,
    scope: EntityScope
  ): BlobRef = notUsed()

  override suspend fun upsertRemoteOnly(
    id: BlobId,
    sha256: String,
    sizeBytes: Long,
    contentType: String?,
    scope: EntityScope
  ): Unit = notUsed()

  override fun observe(id: BlobId): Flow<BlobRef?> = notUsed()
  override suspend fun get(id: BlobId): BlobRef? = notUsed()
  override suspend fun localUri(id: BlobId): String? = notUsed()
  override suspend fun markUploading(id: BlobId): Unit = notUsed()
  override suspend fun markUploaded(id: BlobId, remotePath: String): Unit = notUsed()
  override suspend fun markFailedTransient(id: BlobId): Unit = notUsed()
  override suspend fun markFailedPermanent(id: BlobId, cause: Throwable): Unit = notUsed()
  override suspend fun installDownloaded(
    id: BlobId,
    bytes: ByteArray,
    expectedSha256: String
  ): Result<Unit> = notUsed()

  override suspend fun delete(id: BlobId): Unit = notUsed()
  override fun observeForScope(scopePath: String): Flow<List<BlobRef>> = notUsed()
  override suspend fun resetUploadAttempts(id: BlobId): Unit = notUsed()
  override suspend fun wipeForUser(uid: String): Unit = notUsed()

  private fun notUsed(): Nothing = error("TombstoneGc must not call this")
}
