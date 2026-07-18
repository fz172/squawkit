package dev.fanfly.wingslog.feature.attachment.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.aircraft.AttachmentType
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AttachmentFormControllerTest {

  private lateinit var attachmentManager: AttachmentManager
  private lateinit var controller: AttachmentFormController

  @Before
  fun setUp() {
    attachmentManager = mockk(relaxed = true)
    // Unconfined scope so discardUnsavedLocalBlobs' fire-and-forget launch runs synchronously.
    controller = AttachmentFormController(
      attachmentManager,
      AIRCRAFT_ID,
      CoroutineScope(UnconfinedTestDispatcher()),
    )
  }

  // ---- seedIfEmpty ----

  @Test
  fun seedIfEmpty_mapsToSaved() {
    controller.seedIfEmpty(listOf(fileAttachment("a1"), linkAttachment("a2")))

    val pending = controller.pendingAttachments.value
    assertThat(pending).hasSize(2)
    assertThat(pending.all { it is PendingAttachment.Saved }).isTrue()
  }

  @Test
  fun seedIfEmpty_whenAlreadyPopulated_isNoOp() {
    controller.seedIfEmpty(listOf(fileAttachment("a1")))
    controller.seedIfEmpty(listOf(fileAttachment("b1"), fileAttachment("b2")))

    val pending = controller.pendingAttachments.value
    assertThat(pending).hasSize(1)
    assertThat(pending.first().id).isEqualTo("a1")
  }

  // ---- addLocalFiles ----

  @Test
  fun addLocalFiles_appendsLocalAndReturnsTrue() = runTest {
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } returns fileAttachment("new")

    val anyAdded = controller.addLocalFiles(listOf(pickedFile())) { }

    assertThat(anyAdded).isTrue()
    assertThat(controller.pendingAttachments.value.single())
      .isInstanceOf(PendingAttachment.Local::class.java)
  }

  @Test
  fun addLocalFiles_oversizedNonPhoto_reportsFileTooLargeAndContinues() = runTest {
    // Non-photos are gated up front on their picked size, before we read them into memory.
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } returns fileAttachment("new")
    val errors = mutableListOf<AttachmentFormController.AddFileError>()

    val anyAdded = controller.addLocalFiles(
      listOf(
        pickedFile(
          sizeBytes = QuotaChecker.MAX_FILE_SIZE_BYTES + 1,
          mimeType = "application/pdf",
          name = "big.pdf",
        ),
        pickedFile(),
      )
    ) { errors.add(it) }

    assertThat(errors).containsExactly(AttachmentFormController.AddFileError.FileTooLarge)
    assertThat(anyAdded).isTrue()
    assertThat(controller.pendingAttachments.value).hasSize(1)
    // The oversized non-photo is never handed to the manager.
    coVerify(exactly = 1) { attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any()) }
  }

  @Test
  fun addLocalFiles_oversizedPhoto_isDeferredToManagerNotPreRejected() = runTest {
    // A photo over the cap is NOT pre-rejected — it goes to the manager, which compresses it and
    // decides. Here the manager admits it (post-compression).
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } returns fileAttachment("compressed")
    val errors = mutableListOf<AttachmentFormController.AddFileError>()

    val anyAdded = controller.addLocalFiles(
      listOf(pickedFile(sizeBytes = QuotaChecker.MAX_FILE_SIZE_BYTES + 1, mimeType = "image/jpeg"))
    ) { errors.add(it) }

    assertThat(errors).isEmpty()
    assertThat(anyAdded).isTrue()
    coVerify(exactly = 1) { attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any()) }
  }

  @Test
  fun addLocalFiles_whenManagerThrowsFileTooLarge_reportsFileTooLarge() = runTest {
    // Photo still over the cap after compression — the manager throws, the controller maps it.
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } throws FileTooLargeException(9_000_000L)
    val errors = mutableListOf<AttachmentFormController.AddFileError>()

    val anyAdded = controller.addLocalFiles(
      listOf(pickedFile(sizeBytes = QuotaChecker.MAX_FILE_SIZE_BYTES + 1, mimeType = "image/jpeg"))
    ) { errors.add(it) }

    assertThat(anyAdded).isFalse()
    assertThat(errors).containsExactly(AttachmentFormController.AddFileError.FileTooLarge)
    assertThat(controller.pendingAttachments.value).isEmpty()
  }

  @Test
  fun addLocalFiles_stopsAtFileCap() = runTest {
    var next = 0
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } answers { fileAttachment("new-${next++}") }

    controller.addLocalFiles(List(5) { pickedFile() }) { }

    assertThat(controller.pendingAttachments.value)
      .hasSize(QuotaChecker.MAX_FILE_ATTACHMENTS)
    assertThat(controller.filesAtLimit).isTrue()
  }

  @Test
  fun addLocalFiles_whenManagerThrows_reportsFailedWithMessage() = runTest {
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } throws RuntimeException("disk full")
    val errors = mutableListOf<AttachmentFormController.AddFileError>()

    val anyAdded = controller.addLocalFiles(listOf(pickedFile())) { errors.add(it) }

    assertThat(anyAdded).isFalse()
    assertThat(errors)
      .containsExactly(AttachmentFormController.AddFileError.Failed("disk full"))
    assertThat(controller.pendingAttachments.value).isEmpty()
  }

  @Test
  fun addLocalFiles_whenAlreadyAtCap_addsNothingAndReturnsFalse() = runTest {
    controller.seedIfEmpty(
      listOf(fileAttachment("a1"), fileAttachment("a2"), fileAttachment("a3"))
    )

    val anyAdded = controller.addLocalFiles(listOf(pickedFile())) { }

    assertThat(anyAdded).isFalse()
    coVerify(exactly = 0) { attachmentManager.addPickedFile(any(), any(), any()) }
  }

  // ---- picker visibility ----

  @Test
  fun showPicker_hidePicker_toggleState() {
    assertThat(controller.showPicker.value).isFalse()

    controller.showPicker()
    assertThat(controller.showPicker.value).isTrue()

    controller.hidePicker()
    assertThat(controller.showPicker.value).isFalse()
  }

  // ---- filesAtLimit ----

  @Test
  fun filesAtLimit_ignoresLinksAndPendingDeletes() = runTest {
    controller.seedIfEmpty(
      listOf(
        fileAttachment("f1"),
        fileAttachment("f2"),
        fileAttachment("f3"),
        linkAttachment("l1"),
      )
    )
    assertThat(controller.filesAtLimit).isTrue()

    controller.remove("f3")

    assertThat(controller.filesAtLimit).isFalse()
  }

  // ---- addLink ----

  @Test
  fun addLink_usesProvidedName() {
    every {
      attachmentManager.makeLink("https://example.com", "My link")
    } returns linkAttachment("l1")

    controller.addLink("https://example.com", "My link")

    assertThat(controller.pendingAttachments.value.single())
      .isInstanceOf(PendingAttachment.LocalLink::class.java)
  }

  @Test
  fun addLink_blankName_fallsBackToTruncatedUrl() {
    val url = "https://example.com/" + "x".repeat(60)
    every { attachmentManager.makeLink(url, url.take(40)) } returns linkAttachment("l1")

    controller.addLink(url, "  ")

    assertThat(controller.pendingAttachments.value.single())
      .isInstanceOf(PendingAttachment.LocalLink::class.java)
  }

  // ---- remove ----

  @Test
  fun remove_localAndLinkItems_disappear() = runTest {
    every { attachmentManager.makeLink(any(), any()) } returns linkAttachment("l1")
    controller.seedIfEmpty(listOf(linkAttachment("saved-link")))
    controller.addLink("https://example.com", "link")

    controller.remove("l1")
    controller.remove("saved-link")

    assertThat(controller.pendingAttachments.value).isEmpty()
    // Links carry no blob — nothing to tombstone.
    coVerify(exactly = 0) { attachmentManager.delete(any()) }
  }

  @Test
  fun remove_savedFile_becomesPendingDelete() = runTest {
    controller.seedIfEmpty(listOf(fileAttachment("a1")))

    controller.remove("a1")

    assertThat(controller.pendingAttachments.value.single())
      .isInstanceOf(PendingAttachment.PendingDelete::class.java)
    // Saved files are tombstoned at save time (resolveForSave), not on remove.
    coVerify(exactly = 0) { attachmentManager.delete(any()) }
  }

  @Test
  fun remove_freshlyAddedLocalFile_tombstonesItsBlob() = runTest {
    // A picked file already has a blob on disk + a scheduled upload; removing it before save must
    // tombstone that blob or it orphans (locally, and in gs:// once the upload lands).
    val local = fileAttachment("local-1")
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } returns local
    controller.addLocalFiles(listOf(pickedFile())) { }

    controller.remove("local-1")

    assertThat(controller.pendingAttachments.value).isEmpty()
    coVerify(exactly = 1) { attachmentManager.delete(local) }
  }

  // ---- resolveForSave ----

  @Test
  fun resolveForSave_tombstonesPendingDeletesAndReturnsRest() = runTest {
    val kept = fileAttachment("kept")
    val doomed = fileAttachment("doomed")
    controller.seedIfEmpty(listOf(kept, doomed))
    controller.remove("doomed")

    val resolved = controller.resolveForSave()

    coVerify(exactly = 1) { attachmentManager.delete(doomed) }
    assertThat(resolved).containsExactly(kept)
  }

  @Test
  fun resolveForSave_ordersSavedThenLocalThenLink() = runTest {
    val saved = fileAttachment("saved")
    val local = fileAttachment("local")
    val link = linkAttachment("link")
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } returns local
    every { attachmentManager.makeLink(any(), any()) } returns link

    controller.seedIfEmpty(listOf(saved))
    // Add the link before the file — resolve must still order Saved, Local, LocalLink
    controller.addLink("https://example.com", "link")
    controller.addLocalFiles(listOf(pickedFile())) { }

    assertThat(controller.resolveForSave())
      .containsExactly(saved, local, link)
      .inOrder()
  }

  // ---- discardUnsavedLocalBlobs ----

  @Test
  fun discardUnsavedLocalBlobs_whenNotSaved_tombstonesLocalFiles() = runTest {
    val local = fileAttachment("local-1")
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } returns local
    controller.addLocalFiles(listOf(pickedFile())) { }

    controller.discardUnsavedLocalBlobs()

    coVerify(exactly = 1) { attachmentManager.delete(local) }
  }

  @Test
  fun discardUnsavedLocalBlobs_afterResolveForSave_isNoOp() = runTest {
    // Once resolveForSave has run the Local blobs are owned by the saved record — abandoning
    // afterwards (e.g. onCleared firing post-save) must not delete them.
    val local = fileAttachment("local-1")
    coEvery {
      attachmentManager.addPickedFile(AIRCRAFT_ID, any(), any())
    } returns local
    controller.addLocalFiles(listOf(pickedFile())) { }
    controller.resolveForSave()

    controller.discardUnsavedLocalBlobs()

    coVerify(exactly = 0) { attachmentManager.delete(any()) }
  }

  @Test
  fun discardUnsavedLocalBlobs_leavesSavedAttachmentsUntouched() = runTest {
    // Editing an existing record: its already-saved attachments must survive an abandon.
    controller.seedIfEmpty(listOf(fileAttachment("saved-1")))

    controller.discardUnsavedLocalBlobs()

    coVerify(exactly = 0) { attachmentManager.delete(any()) }
  }

  // ---- deleteSavedFiles ----

  @Test
  fun deleteSavedFiles_skipsLinks() = runTest {
    val file = fileAttachment("f1")
    val link = linkAttachment("l1")
    controller.seedIfEmpty(listOf(file, link))

    controller.deleteSavedFiles()

    coVerify(exactly = 1) { attachmentManager.delete(file) }
    coVerify(exactly = 0) { attachmentManager.delete(link) }
  }

  // ---- helpers ----

  private fun fileAttachment(id: String) = Attachment(
    id = id,
    name = "$id.pdf",
    type = AttachmentType.ATTACHMENT_TYPE_FILE,
  )

  private fun linkAttachment(id: String) = Attachment(
    id = id,
    name = id,
    type = AttachmentType.ATTACHMENT_TYPE_LINK,
  )

  private fun pickedFile(
    sizeBytes: Long = 100L,
    mimeType: String = "image/jpeg",
    name: String = "photo.jpg",
  ) = PickedFile(
    uri = "uri",
    name = name,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
  )

  private companion object {
    const val AIRCRAFT_ID = "aircraft-1"
  }
}
