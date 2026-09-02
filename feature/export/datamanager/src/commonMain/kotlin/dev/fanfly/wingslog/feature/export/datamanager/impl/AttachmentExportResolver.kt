package dev.fanfly.wingslog.feature.export.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resolves non-link attachments into binary payloads that can be embedded in an export ZIP.
 */
class AttachmentExportResolver(
  private val attachmentManager: AttachmentManager,
  private val localBlobStore: LocalBlobStore,
  private val blobFilesystem: BlobFilesystem,
) {

  private val log = Logger.withTag("AttachmentExportResolver")

  /**
   * Downloads missing binaries when possible and returns the local payloads for [bundle].
   *
   * **An attachment that cannot be fetched never blocks the export** — it is noted and skipped, and
   * the ZIP ships with whatever resolved. That is a deliberate product call: a partial logbook is
   * useful, a spinner is not.
   *
   * Two things make that true, and both are load-bearing:
   *
   * **Concurrency.** These coroutines spend nearly all their time *waiting* on
   * `AttachmentManager.ensureLocal`, whose own timeout is per attachment. Resolved one at a time,
   * a thing with 11 unfetchable attachments cost 11 × that timeout — five and a half silent
   * minutes, which is exactly how #426 was first reported. Run together, the worst case is one
   * timeout regardless of count. The real network work is not done here anyway; it is scheduled
   * through WorkManager, which does its own throttling.
   *
   * **A budget for the whole phase.** Concurrency alone still degrades with enough attachments
   * (batches × timeout), so the phase gets an absolute cap. Whatever has not resolved when it
   * expires is noted and abandoned rather than extending the wait.
   */
  suspend fun resolve(bundle: ThingBundle): AttachmentExportManifest =
    coroutineScope {
      val notes = mutableListOf<String>()
      // Deduped, and in bundle order: the ZIP's entry order should not depend on which download
      // happened to finish first.
      val targets = bundle.exportedAttachments()
        .filter { it.type != AttachmentType.ATTACHMENT_TYPE_LINK && it.id.isNotBlank() }
        .distinctBy { it.id }

      val limit = Semaphore(MAX_CONCURRENT_ATTACHMENTS)
      val pending = targets.map { attachment ->
        attachment to async { limit.withPermit { resolveOne(attachment) } }
      }

      withTimeoutOrNull(RESOLVE_BUDGET_MS) { pending.forEach { (_, job) -> job.await() } }

      val payloads = linkedMapOf<String, AttachmentExportPayload>()
      for ((attachment, job) in pending) {
        if (!job.isCompleted || job.isCancelled) {
          // Still in flight when the budget expired. Cancel it: these are children of this scope,
          // so leaving them running would make coroutineScope wait for the very thing we gave up on.
          job.cancel()
          notes += "Attachment ${attachment.id} was not available in time."
          continue
        }
        when (val outcome = job.await()) {
          is Resolved.Payload -> payloads[attachment.id] = outcome.payload
          is Resolved.Missing -> notes += outcome.note
        }
      }

      // A partial export used to be silent — it shipped the notes inside the ZIP and said nothing
      // anywhere a developer would look, which is why #426 was diagnosed from a WorkManager trace
      // instead of from here. Counts at warn; the per-attachment reasons at debug, since the ids
      // are noise once the count tells you whether to care.
      if (notes.isEmpty()) {
        log.i { "Attachments resolved for ${bundle.thing.id}: ${payloads.size}/${targets.size}" }
      } else {
        log.w {
          "Attachments INCOMPLETE for ${bundle.thing.id}: " +
            "${payloads.size}/${targets.size} embedded, ${notes.size} unavailable"
        }
        notes.forEach { note -> log.d { note } }
      }

      AttachmentExportManifest(
        byAttachmentId = payloads,
        notes = notes,
      )
    }

  private suspend fun resolveOne(attachment: Attachment): Resolved {
    val result = runCatching {
      attachmentManager.ensureLocal(attachment)
        .first { state -> state !is DownloadState.Downloading }
    }
    val state = result.getOrNull()
    if (state is DownloadState.Failed || result.isFailure) {
      return Resolved.Missing("Attachment ${attachment.id} could not be downloaded.")
    }

    val ref = localBlobStore.get(BlobId(attachment.id))
      ?: return Resolved.Missing("Attachment ${attachment.id} has no local blob record.")

    val bytes = runCatching { blobFilesystem.read(ref.relativePath) }.getOrNull()
      ?: return Resolved.Missing("Attachment ${attachment.id} local file could not be read.")

    return Resolved.Payload(
      AttachmentExportPayload(
        attachmentId = attachment.id,
        relativePath = attachment.exportRelativePath(),
        bytes = bytes,
      )
    )
  }

  private sealed interface Resolved {
    data class Payload(val payload: AttachmentExportPayload) : Resolved
    data class Missing(val note: String) : Resolved
  }

  private fun ThingBundle.exportedAttachments(): List<Attachment> =
    logs.flatMap { it.attachments } +
      tasks.flatMap { it.attachments } +
      squawks.flatMap { it.attachments }

  private fun Attachment.exportRelativePath(): String {
    val shortId = id.take(4)
      .ifBlank { "file" }
    val fileName = name
      .ifBlank { "$shortId.${mime_type.extension()}" }
      .sanitizeAttachmentFileName()
    return "attachments/${shortId}_$fileName"
  }

  private fun String.extension(): String =
    when (substringAfter('/', "").substringBefore(';')) {
      "jpeg" -> "jpg"
      "png" -> "png"
      "pdf" -> "pdf"
      "plain" -> "txt"
      else -> "bin"
    }

  private fun String.sanitizeAttachmentFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
      .ifBlank { "attachment.bin" }

  private companion object {
    /**
     * These coroutines mostly wait, so this is not a throughput knob — it caps concurrent
     * `blobFilesystem.read` calls and keeps a huge export from opening everything at once.
     */
    private const val MAX_CONCURRENT_ATTACHMENTS = 8

    /**
     * Hard cap on the whole attachment phase. Past this the export ships partial rather than
     * making the pilot wait longer for files that are very likely gone.
     */
    private const val RESOLVE_BUDGET_MS = 60_000L
  }
}
