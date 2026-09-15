package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.file.GzipCodec
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.datalog.DataLogEncoding
import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogCache
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogImporter
import dev.fanfly.wingslog.feature.datalog.datamanager.Fixtures
import dev.fanfly.wingslog.feature.datalog.datamanager.garmin.GarminParser
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Instant

class DataLogManagerImplTest {

  private val thingId = ThingId("thing-1")
  private val scope = EntityScope.thingChildUnsafe("host-uid", "thing-1")
  private val blobId = BlobId("blob-1")

  private lateinit var store: EntityStore<DataLog>
  private lateinit var blobs: LocalBlobStore
  private lateinit var filesystem: BlobFilesystem
  private lateinit var scheduler: UploadScheduler
  private lateinit var importer: DataLogImporter
  private lateinit var cache: DataLogCache
  private lateinit var manager: DataLogManagerImpl

  @Before
  fun setUp() {
    store = mockk(relaxed = true)
    val factory = mockk<EntityStoreFactory>()
    every { factory.create<DataLog>(CollectionKind.DataLog) } returns store
    val resolver = mockk<ThingScopeResolver>()
    every { resolver.resolve("thing-1") } returns flowOf(scope)
    coEvery { resolver.resolveNow("thing-1") } returns scope
    blobs = mockk(relaxed = true)
    filesystem = mockk()
    scheduler = mockk(relaxed = true)
    importer = mockk()
    cache = DataLogCache()
    manager = DataLogManagerImpl(
      scopeResolver = resolver,
      storeFactory = factory,
      blobs = blobs,
      filesystem = filesystem,
      scheduler = scheduler,
      importer = importer,
      cache = cache,
      parsers = listOf(GarminParser()),
    )
  }

  private fun record(
    id: String,
    start: String,
    encoding: DataLogEncoding = DataLogEncoding.DATA_LOG_ENCODING_GZIP,
    parserVersion: Int = GarminParser().version,
  ) =
    DataLog(
      id = DataLogId(id), format = DataLogFormat.DATA_LOG_FORMAT_GARMIN_G3X,
      parser_version = parserVersion,
      start = Instant.parse(start)
        .toWireInstant(),
      encoding = encoding, file_name = Fixtures.GROUND_RUN,
      raw_file = Attachment(
        id = blobId.value,
        type = AttachmentType.ATTACHMENT_TYPE_FILE
      ),
    )

  private fun ref(state: RemoteState) = BlobRef(
    id = blobId,
    scope = scope,
    relativePath = "blobs/blob-1.bin",
    sizeBytes = 1,
    sha256 = "x",
    contentType = null,
    remoteState = state,
    remotePath = null,
    uploadAttempts = 0,
    deleted = false,
    updatedAt = Instant.DISTANT_PAST,
  )

  @Test
  fun observeIsNewestFirstAndDropsRowsWithoutAnId() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        StorageEntity(
          "a",
          record("a", "2026-09-01T00:00:00Z"),
          Instant.DISTANT_PAST
        ),
        StorageEntity(
          "b",
          record("b", "2026-09-03T00:00:00Z"),
          Instant.DISTANT_PAST
        ),
        StorageEntity("corrupt", DataLog(id = null), Instant.DISTANT_PAST),
      ),
    )

    val logs = manager.observe(thingId)
      .first()

    assertThat(logs.map { it.id }).containsExactly(
      DataLogId("b"),
      DataLogId("a")
    )
      .inOrder()
    assertThat(
      manager.observeOne(thingId, DataLogId("a"))
        .first()?.id
    ).isEqualTo(DataLogId("a"))
    assertThat(
      manager.observeOne(thingId, DataLogId("zzz"))
        .first()
    ).isNull()
  }

  @Test
  fun loadReadsTheLocalBlobDecodesParsesAndCaches() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        StorageEntity(
          "a",
          record("a", "2026-09-02T21:47:56Z"),
          Instant.DISTANT_PAST
        )
      ),
    )
    coEvery { blobs.get(blobId) } returns ref(RemoteState.Synced)
    coEvery { filesystem.read("blobs/blob-1.bin") } returns GzipCodec.compress(
      Fixtures.bytes(Fixtures.GROUND_RUN)
    )

    val data = manager.load(thingId, DataLogId("a"))
      .getOrThrow()
    assertThat(data.rowCount).isEqualTo(256)
    assertThat(cache.get(DataLogId("a"))).isSameInstanceAs(data)

    // Second load is the cache: no filesystem read.
    val again = manager.load(thingId, DataLogId("a"))
      .getOrThrow()
    assertThat(again).isSameInstanceAs(data)
    coVerify(exactly = 1) { filesystem.read(any()) }
  }

  @Test
  fun aCatalogueOlderThanTheParserIsRewrittenOnOpen() = runTest {
    // The catalogue is frozen into the record at import while the values are re-parsed on every
    // open, so without this a parser fix reaches the chart and never reaches the sidebar beside it.
    val stale = record("a", "2026-09-02T21:47:56Z", parserVersion = 1)
      .copy(series = emptyList(), duration_seconds = 0, sample_count = 0)
    every { store.observeAll(scope) } returns flowOf(
      listOf(StorageEntity("a", stale, Instant.DISTANT_PAST))
    )
    coEvery { blobs.get(blobId) } returns ref(RemoteState.Synced)
    coEvery { filesystem.read("blobs/blob-1.bin") } returns GzipCodec.compress(
      Fixtures.bytes(Fixtures.GROUND_RUN)
    )

    manager.load(thingId, DataLogId("a"))
      .getOrThrow()

    val written = slot<DataLog>()
    coVerify { store.put("a", capture(written), scope) }
    assertThat(written.captured.parser_version).isEqualTo(GarminParser().version)
    assertThat(written.captured.series).isNotEmpty()
    assertThat(written.captured.sample_count).isEqualTo(256)
    assertThat(written.captured.duration_seconds).isEqualTo(255)
    // Not the parser's to change: the comparison against the Thing, and the stored bytes.
    assertThat(written.captured.raw_file).isEqualTo(stale.raw_file)
    assertThat(written.captured.identity_mismatch).isEqualTo(stale.identity_mismatch)
  }

  @Test
  fun aCatalogueTheParserAgreesWithIsLeftAlone() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        StorageEntity(
          "a",
          record("a", "2026-09-02T21:47:56Z"),
          Instant.DISTANT_PAST
        )
      ),
    )
    coEvery { blobs.get(blobId) } returns ref(RemoteState.Synced)
    coEvery { filesystem.read("blobs/blob-1.bin") } returns GzipCodec.compress(
      Fixtures.bytes(Fixtures.GROUND_RUN)
    )

    manager.load(thingId, DataLogId("a"))
      .getOrThrow()

    coVerify(exactly = 0) { store.put(any(), any(), any()) }
  }

  @Test
  fun loadFailsWhileTheBytesAreStillRemote() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        StorageEntity(
          "a",
          record("a", "2026-09-02T21:47:56Z"),
          Instant.DISTANT_PAST
        )
      ),
    )
    coEvery { blobs.get(blobId) } returns ref(RemoteState.RemoteOnly)

    assertThat(manager.load(thingId, DataLogId("a")).isFailure).isTrue()
    coVerify(exactly = 0) { filesystem.read(any()) }
  }

  @Test
  fun ensureLocalSchedulesADownloadForARemoteOnlyBlobAndReportsDone() =
    runTest {
      every { store.observeAll(scope) } returns flowOf(
        listOf(
          StorageEntity(
            "a",
            record("a", "2026-09-02T21:47:56Z"),
            Instant.DISTANT_PAST
          )
        ),
      )
      val blob = MutableStateFlow<BlobRef?>(ref(RemoteState.RemoteOnly))
      every { blobs.observe(blobId) } returns blob

      assertThat(
        manager.ensureLocal(thingId, DataLogId("a"))
          .first()
      ).isEqualTo(DownloadState.Downloading(0f))
      verify { scheduler.scheduleDownload(blobId) }

      blob.value = ref(RemoteState.Synced)
      assertThat(
        manager.ensureLocal(thingId, DataLogId("a"))
          .first()
      ).isEqualTo(DownloadState.Done)
      assertThat(
        manager.observeBlobState(thingId, DataLogId("a"))
          .first()
      ).isEqualTo(BlobSyncState.Synced)
    }

  @Test
  fun deleteTombstonesTheRecordInTheResolvedScope() = runTest {
    assertThat(manager.delete(thingId, DataLogId("a")).isSuccess).isTrue()
    coVerify { store.delete("a", scope) }
  }
}
