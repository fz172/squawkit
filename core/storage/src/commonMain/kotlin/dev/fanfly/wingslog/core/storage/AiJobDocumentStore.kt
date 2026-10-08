package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.async.coroutines.awaitAsList
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.db.Ai_job_document
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.thing.Attachment

/** A document [uid]'s AI run [jobId] on [thingId] was started with. */
data class AiJobDocument(
  val uid: String,
  val jobId: String,
  val thingId: String,
  val attachment: Attachment,
)

/**
 * The documents each AI run was started with (docs/ai/task_population_design.md §8.2). A picked
 * document is held by no record until the user accepts, so the run's owner lets go of it from here:
 * on accept, on dismiss, and at app start for a job the server no longer has.
 *
 * Device-local and never synced, like `urgency_watermark`.
 */
class AiJobDocumentStore(
  private val db: WingsLogDatabase,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) {

  suspend fun record(uid: String, jobId: String, thingId: String, attachments: List<Attachment>) {
    writeLock.withLock {
      db.schemaQueries.transaction {
        attachments.forEach {
          db.schemaQueries.insertAiJobDocument(
            uid = uid,
            job_id = jobId,
            thing_id = thingId,
            attachment_id = it.id,
            attachment = it.encode(),
          )
        }
      }
    }
  }

  suspend fun forJob(uid: String, jobId: String): List<AiJobDocument> =
    db.schemaQueries.selectAiJobDocumentsForJob(uid, jobId)
      .awaitAsList()
      .mapNotNull { it.decode() }

  /** Every recorded document, every user's: the app-start cleanup sorts them. */
  suspend fun all(): List<AiJobDocument> =
    db.schemaQueries.selectAllAiJobDocuments()
      .awaitAsList()
      .mapNotNull { it.decode() }

  /** Drops [jobId]'s rows, once its documents are released or handed to a task. */
  suspend fun forget(uid: String, jobId: String) {
    writeLock.withLock { db.schemaQueries.deleteAiJobDocumentsForJob(uid, jobId) }
  }

  /** Drops [attachmentIds] from [jobId]'s rows, leaving its other documents. */
  suspend fun forget(uid: String, jobId: String, attachmentIds: List<String>) {
    if (attachmentIds.isEmpty()) return
    writeLock.withLock {
      db.schemaQueries.transaction {
        attachmentIds.forEach { db.schemaQueries.deleteAiJobDocument(uid, jobId, it) }
      }
    }
  }

  private fun Ai_job_document.decode(): AiJobDocument? =
    runCatching { Attachment.ADAPTER.decode(attachment) }
      .onFailure { logger.w(it) { "An AI job document did not decode" } }
      .getOrNull()
      ?.let { AiJobDocument(uid = uid, jobId = job_id, thingId = thing_id, attachment = it) }

  private companion object {
    val logger = Logger.withTag("AiJobDocumentStore")
  }
}
