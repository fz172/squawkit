package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.file.GzipCodec
import dev.fanfly.wingslog.core.file.sha256Hex
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.StorageEntity
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.datalog.DataLogEncoding
import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.datalog.DataLogSource
import dev.fanfly.wingslog.feature.attachment.datamanager.FileByteReader
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.datamanager.Fixtures
import dev.fanfly.wingslog.feature.datalog.datamanager.HeaderSniffer
import dev.fanfly.wingslog.feature.datalog.datamanager.OtherThing
import dev.fanfly.wingslog.feature.datalog.datamanager.ThingIdentifierLookup
import dev.fanfly.wingslog.feature.datalog.datamanager.dynon.DynonParser
import dev.fanfly.wingslog.feature.datalog.datamanager.garmin.GarminParser
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

class DataLogImporterImplTest {

  private val thingId = ThingId("thing-1")
  private val scope = EntityScope.thingChildUnsafe("host-uid", "thing-1")
  private val picked =
    PickedFile("content://x", Fixtures.GROUND_RUN, "text/csv", 120_968)
  private val fixture = Fixtures.bytes(Fixtures.GROUND_RUN)

  private lateinit var reader: FileByteReader
  private lateinit var store: EntityStore<DataLog>
  private lateinit var blobs: LocalBlobStore
  private lateinit var scheduler: UploadScheduler
  private lateinit var auth: AuthManager
  private var identifier: String? = "N1234X"
  private var otherThing: OtherThing? = null
  private lateinit var importer: DataLogImporterImpl

  @Before
  fun setUp() {
    reader = mockk()
    every { reader.readBytes("content://x") } returns fixture
    store = mockk(relaxed = true)
    every { store.observeAll(scope) } returns flowOf(emptyList())
    blobs = mockk()
    coEvery { blobs.put(any(), any(), any(), any()) } answers {
      val bytes = secondArg<ByteArray>()
      // MockK unboxes the BlobId value class here, so the ref carries a fixed id; the record's blob
      // id is asserted against the record itself below.
      BlobRef(
        id = BlobId("stored-blob"),
        scope = scope,
        relativePath = "blobs/x.bin",
        sizeBytes = bytes.size.toLong(),
        sha256 = sha256Hex(bytes),
        contentType = thirdArg(),
        remoteState = RemoteState.LocalOnly,
        remotePath = null,
        uploadAttempts = 0,
        deleted = false,
        updatedAt = Instant.DISTANT_PAST,
      )
    }
    scheduler = mockk(relaxed = true)
    auth = mockk()
    val user = mockk<FirebaseUser>()
    every { user.uid } returns "member-uid"
    every { auth.getCurrentUser() } returns user
    val resolver = mockk<ThingScopeResolver>()
    coEvery { resolver.resolveNow("thing-1") } returns scope
    val clock = mockk<Clock>()
    every { clock.now() } returns Instant.parse("2026-09-03T00:00:00Z")
    importer = DataLogImporterImpl(
      fileByteReader = reader,
      sniffer = HeaderSniffer(listOf(GarminParser(), DynonParser())),
      scopeResolver = resolver,
      store = store,
      blobs = blobs,
      scheduler = scheduler,
      identifiers = ThingIdentifierLookup { identifier },
      auth = auth,
      otherThings = { _, _ -> otherThing },
      clock = clock,
    )
  }

  private suspend fun run(
    confirm: Boolean = false,
    keepIdentity: Boolean = false
  ) =
    importer.import(thingId, picked, confirm, keepIdentity)
      .toList()

  @Test
  fun aTailThatNamesAnotherThingOffersToFileItThereAndStoresNothing() =
    runTest {
      identifier = "N5678Y"
      otherThing = OtherThing(ThingId("thing-2"), "N1234X Volar T2i")

      val states = run()

      assertThat(states.last()).isEqualTo(
        ImportProgress.OtherThing(ThingId("thing-2"), "N1234X Volar T2i")
      )
      assertThat(states).doesNotContain(ImportProgress.Storing)
      coVerify(exactly = 0) { store.put(any(), any(), any()) }
      coVerify(exactly = 0) { blobs.put(any(), any(), any(), any()) }
    }

  @Test
  fun keepingItHereStoresTheRecordWithTheMismatchFlagStillSet() = runTest {
    identifier = "N5678Y"
    otherThing = OtherThing(ThingId("thing-2"), "N1234X Volar T2i")

    val done = run(keepIdentity = true).last() as ImportProgress.Done

    val record = slot<DataLog>()
    coVerify { store.put(done.id.value_, capture(record), scope) }
    assertThat(record.captured.identity_mismatch).isTrue()
  }

  @Test
  fun noOtherThingCarriesThatTailSoTheImportJustProceeds() = runTest {
    identifier = "N5678Y"
    otherThing = null

    val done = run().last() as ImportProgress.Done

    val record = slot<DataLog>()
    coVerify { store.put(done.id.value_, capture(record), scope) }
    assertThat(record.captured.identity_mismatch).isTrue()
  }

  @Test
  fun aFreshFileBecomesAGzipBlobAndARecordInTheThingsScope() = runTest {
    val states = run()

    assertThat(states.first()).isEqualTo(ImportProgress.Reading)
    assertThat(states).contains(ImportProgress.Storing)
    val done = states.last() as ImportProgress.Done

    val stored = slot<ByteArray>()
    coVerify { blobs.put(any(), capture(stored), "application/gzip", scope) }
    assertThat(GzipCodec.decompress(stored.captured)).isEqualTo(fixture)
    verify { scheduler.scheduleUpload(any()) }

    val record = slot<DataLog>()
    coVerify { store.put(done.id.value_, capture(record), scope) }
    with(record.captured) {
      assertThat(id).isEqualTo(done.id)
      assertThat(format).isEqualTo(DataLogFormat.DATA_LOG_FORMAT_GARMIN_G3X)
      assertThat(encoding).isEqualTo(DataLogEncoding.DATA_LOG_ENCODING_GZIP)
      assertThat(raw_sha256).isEqualTo(sha256Hex(fixture))
      assertThat(raw_size_bytes).isEqualTo(fixture.size.toLong())
      assertThat(file_name).isEqualTo(Fixtures.GROUND_RUN)
      assertThat(start_location_ident).isEqualTo("XX1")
      assertThat(airborne).isFalse()
      assertThat(identity_mismatch).isFalse()
      assertThat(sample_count).isEqualTo(256)
      assertThat(series).isNotEmpty()
      assertThat(end_latitude).isWithin(1e-9)
        .of(39.0810252)
      assertThat(created_by?.value_).isEqualTo("member-uid")
      val raw = raw_file!!
      // The blob id is fresh — never the record id — and the attachment describes the STORED bytes.
      assertThat(raw.id).isNotEqualTo(done.id.value_)
      assertThat(raw.sha256).isEqualTo(sha256Hex(stored.captured))
      assertThat(raw.size_bytes).isEqualTo(stored.captured.size.toLong())
      assertThat(raw.storage_path).isEqualTo("users/host-uid/thing/thing-1/blobs/${raw.id}")
    }
  }

  @Test
  fun aTailNumberThatDiffersFromTheThingsIsFlagged() = runTest {
    identifier = "N5678Y"
    val done = run().last() as ImportProgress.Done
    val record = slot<DataLog>()
    coVerify { store.put(done.id.value_, capture(record), scope) }
    assertThat(record.captured.identity_mismatch).isTrue()
  }

  @Test
  fun aSkyViewDownloadBecomesOneRecordPerPowerCycleSharingOneBlob() = runTest {
    // Four sessions in one file (design §6.2). Merged they would be one chart spanning a month.
    val dynon = Fixtures.dynonBytes(Fixtures.DYNON_SESSIONS)
    every { reader.readBytes("content://x") } returns dynon
    val skyView = PickedFile(
      "content://x",
      Fixtures.DYNON_SESSIONS,
      "text/csv",
      dynon.size.toLong()
    )

    val states = importer.import(
      thingId,
      skyView,
      confirmDuplicate = false,
      keepIdentity = true
    )
      .toList()

    val done = states.last() as ImportProgress.Done
    assertThat(done.sessionCount).isEqualTo(5)
    val records = mutableListOf<DataLog>()
    coVerify(exactly = 5) { store.put(any(), capture(records), scope) }
    assertThat(records.map { it.session_index }).containsExactly(0, 1, 2, 3, 4)
      .inOrder()
    assertThat(records.first().id).isEqualTo(done.id)
    // One file, one upload: every record names the same blob and the same original bytes.
    assertThat(records.map { it.raw_file?.id }
                 .toSet()).hasSize(1)
    assertThat(records.map { it.raw_sha256 }
                 .toSet()).containsExactly(sha256Hex(dynon))
    coVerify(exactly = 1) { blobs.put(any(), any(), any(), any()) }
    // The two sessions that never got a fix say their date was inferred.
    assertThat(records.map { it.start_approximate })
      .containsExactly(false, false, false, true, true)
      .inOrder()
    assertThat(records.map { it.format }
                 .toSet())
      .containsExactly(DataLogFormat.DATA_LOG_FORMAT_DYNON_SKYVIEW)
  }

  @Test
  fun theSameBytesAgainIsADuplicate() = runTest {
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        existing(
          rawSha256 = sha256Hex(
            fixture
          )
        )
      )
    )

    assertThat(run().last()).isEqualTo(ImportProgress.Failed(ImportFailure.DUPLICATE))
    coVerify(exactly = 0) { blobs.put(any(), any(), any(), any()) }
  }

  @Test
  fun sameRecorderAndStartNeedsConfirmationUntilConfirmed() = runTest {
    val start = Instant.parse("2026-09-02T21:47:56Z")
    every { store.observeAll(scope) } returns flowOf(
      listOf(
        existing(
          id = "older",
          systemId = "6000ABCD01234",
          start = start
        )
      )
    )

    assertThat(run().last()).isEqualTo(
      ImportProgress.NeedsConfirmation(
        DataLogId("older")
      )
    )
    coVerify(exactly = 0) { blobs.put(any(), any(), any(), any()) }

    assertThat(run(confirm = true).last()).isInstanceOf(ImportProgress.Done::class.java)
  }

  @Test
  fun unreadableUnrecognisedAndBrokenFilesFailWithTheirReason() = runTest {
    every { reader.readBytes("content://x") } returns null
    assertThat(run().last()).isEqualTo(ImportProgress.Failed(ImportFailure.UNREADABLE))

    every { reader.readBytes("content://x") } returns "a,b\n1,2\n".encodeToByteArray()
    assertThat(run().last()).isEqualTo(ImportProgress.Failed(ImportFailure.UNRECOGNIZED))

    every { reader.readBytes("content://x") } returns "#airframe_info,product=\"GDU 460\"\nSpeed\nSpd\n1\n".encodeToByteArray()
    assertThat(run().last()).isEqualTo(ImportProgress.Failed(ImportFailure.PARSE_ERROR))
    coVerify(exactly = 0) { store.put(any(), any(), any()) }
  }

  private fun existing(
    id: String = "existing",
    rawSha256: String = "other",
    systemId: String = "other-box",
    start: Instant = Instant.parse("2020-01-01T00:00:00Z"),
  ) = StorageEntity(
    id,
    DataLog(
      id = DataLogId(id),
      raw_sha256 = rawSha256,
      source = DataLogSource(system_id = systemId),
      start = start.toWireInstant(),
    ),
    Instant.DISTANT_PAST,
  )
}
