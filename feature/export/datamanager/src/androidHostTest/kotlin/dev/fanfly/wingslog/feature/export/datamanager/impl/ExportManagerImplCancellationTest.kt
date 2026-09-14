package dev.fanfly.wingslog.feature.export.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.impl.BakedInTemplateRegistry
import dev.fanfly.wingslog.feature.export.datamanager.ExportDateRange
import dev.fanfly.wingslog.feature.export.datamanager.ExportDisplayLocation
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgress
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.datamanager.ExportRequest
import dev.fanfly.wingslog.thing.Spec
import dev.fanfly.wingslog.thing.Thing
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * A cancelled export never leaves a finished-looking entry in history: nothing is on disk before
 * `SAVING_FILE`, and a cancellation after it (mid-upload) deletes the archive and record again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExportManagerImplCancellationTest {

  private val thing = Thing(
    id = "thing-1",
    spec = listOf(Spec(key = "tail_number", value_ = "N12345")),
  )
  private val bundle = ThingBundle(
    thing = thing,
    logs = emptyList(),
    tasks = emptyList(),
    dueByTaskId = emptyMap(),
    lastCompliedByTaskId = emptyMap(),
    squawks = emptyList(),
    tasksById = emptyMap(),
    squawksById = emptyMap(),
    techniciansById = emptyMap(),
  )
  private val request = ExportRequest(
    thingIds = listOf(thing.id),
    dateRange = ExportDateRange.AllTime,
    includeOpenSquawks = true,
  )

  private val aggregator: LogbookExportAggregator = mockk()
  private val resolver: AttachmentExportResolver = mockk()
  private val archiveBuilder: LogbookExportArchiveBuilder = mockk()
  private val zipFileWriter: ZipFileWriter = mockk()
  private val exportFileStore: ExportFileStore = mockk()
  private val remoteRepository: ExportHistoryRemoteRepository = mockk()
  private val auth: FirebaseAuth = mockk()

  private lateinit var manager: ExportManagerImpl

  @Before
  fun setUp() {
    val user: FirebaseUser = mockk { every { uid } returns "owner-1" }
    every { auth.currentUser } returns user
    coEvery { resolver.resolve(bundle) } returns
      AttachmentExportManifest(byAttachmentId = emptyMap(), notes = emptyList())
    every { archiveBuilder.buildEntries(any(), any(), any(), any(), any()) } returns emptyList()
    every { archiveBuilder.fileName(any(), any()) } returns "SquawkIt_Logs_N12345_20260911.zip"
    every { zipFileWriter.write(any()) } returns ByteArray(0)
    coEvery { exportFileStore.writeZip(any(), any()) } returns ExportedFile(
      filePath = "content://downloads/1",
      fileName = "SquawkIt_Logs_N12345_20260911.zip",
      displayLocationKind = ExportDisplayLocation.DOWNLOADS_SQUAWKIT,
      sizeBytes = 0L,
    )
    coEvery { exportFileStore.saveRecord(any(), any()) } returns Unit
    coEvery { exportFileStore.deleteExport(any(), any()) } returns true
    manager = ExportManagerImpl(
      aggregator = aggregator,
      attachmentExportResolver = resolver,
      archiveBuilder = archiveBuilder,
      templateRegistry = BakedInTemplateRegistry(appVersionCode = Int.MAX_VALUE),
      zipFileWriter = zipFileWriter,
      exportFileStore = exportFileStore,
      remoteRepository = remoteRepository,
      deliveryBackend = mockk(relaxed = true),
      auth = auth,
    )
  }

  @Test
  fun `cancel during upload discards the saved archive and record`() = runTest {
    coEvery { aggregator.collect(request, thing.id) } returns bundle
    coEvery { remoteRepository.uploadAndSync(any(), any()) } coAnswers { awaitCancellation() }
    val steps = mutableListOf<ExportProgressStep>()

    val job = launch {
      manager.exportLogs(request)
        .collect { if (it is ExportProgress.Running) steps += it.step }
    }
    advanceUntilIdle()
    assertThat(steps.last()).isEqualTo(ExportProgressStep.UPLOADING_ARCHIVE)

    job.cancel()
    advanceUntilIdle()

    coVerify(exactly = 1) { exportFileStore.saveRecord("owner-1", any()) }
    coVerify(exactly = 1) { exportFileStore.deleteExport("owner-1", any()) }
  }

  @Test
  fun `cancel before the archive is saved has nothing to discard`() = runTest {
    coEvery { aggregator.collect(request, thing.id) } coAnswers { awaitCancellation() }

    val job = launch { manager.exportLogs(request).collect {} }
    advanceUntilIdle()
    job.cancel()
    advanceUntilIdle()

    coVerify(exactly = 0) { exportFileStore.writeZip(any(), any()) }
    coVerify(exactly = 0) { exportFileStore.deleteExport(any(), any()) }
  }
}
