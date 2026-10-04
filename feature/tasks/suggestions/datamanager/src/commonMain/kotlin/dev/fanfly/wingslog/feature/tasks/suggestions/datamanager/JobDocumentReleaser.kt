package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.core.storage.AiJobDocumentStore
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.thing.Attachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lets go of the documents an AI run was started with (docs/ai/task_population_design.md §8.2).
 * No record holds a picked document until the user accepts, so each run's documents are noted
 * against the job, and released:
 *
 * - on accept and on dismiss, through [release];
 * - at the start of each app session, for runs the server no longer has (closed elsewhere, or
 *   expired) and runs another user started on this device, through [releaseGone].
 *
 * A release is reference-aware (§8.3): a document a task now holds, or one the user also attached
 * by hand, stays. Only the device's copy goes; the server collects the remote object.
 *
 * Built eagerly at Koin init, like `SessionBoundaryScanTrigger`, so no host has to start it; the
 * Firebase-backed parts are providers so nothing touches Firebase during startup (an NPE on iOS).
 */
class JobDocumentReleaser(
  private val store: AiJobDocumentStore,
  /** The signed-in user, null signed out. */
  private val currentUid: () -> String?,
  private val client: () -> AiJobClient,
  private val attachments: () -> AttachmentManager,
  private val foreground: AppForegroundObserver,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

  private var job: Job? = null

  /** Releases gone runs' documents at every new app session. Idempotent. */
  fun start() {
    if (job?.isActive == true) return
    job = scope.launch {
      // The current value is read rather than dropped, as in SessionBoundaryScanTrigger: the cold
      // start's session can begin before this collector attaches.
      var handled = 0L
      foreground.sessionId.collect { sessionId ->
        if (sessionId == 0L || sessionId == handled) return@collect
        handled = sessionId
        try {
          releaseGone()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          logger.w(e) { "Releasing finished runs' documents failed" }
        }
      }
    }
  }

  /** Notes [documents] against the signed-in user's run [jobId] on [thingId]. */
  suspend fun record(jobId: AiJobId, thingId: String, documents: List<Attachment>) {
    if (documents.isEmpty()) return
    val uid = currentUid() ?: return
    store.record(uid, jobId.value, thingId, documents)
  }

  /** Releases the signed-in user's run [jobId]'s documents and forgets them. */
  suspend fun release(jobId: AiJobId) {
    val uid = currentUid() ?: return
    releaseJob(uid, jobId.value, store.forJob(uid, jobId.value).map { it.attachment })
  }

  /**
   * Releases the documents of every run that is over: the signed-in user's when the server no
   * longer has the job, and every other user's (their jobs cannot be asked about, and their blobs
   * left with them). Signed out, does nothing.
   */
  suspend fun releaseGone() {
    val uid = currentUid() ?: return
    store.all()
      .groupBy { JobKey(it.uid, it.jobId) }
      .forEach { (key, rows) ->
        val gone = key.uid != uid || client().isGone(AiJobId(key.jobId))
        if (gone) releaseJob(key.uid, key.jobId, rows.map { it.attachment })
      }
  }

  private suspend fun releaseJob(uid: String, jobId: String, documents: List<Attachment>) {
    documents.forEach { attachments().release(it, owner = null) }
    store.forget(uid, jobId)
  }

  private data class JobKey(val uid: String, val jobId: String)

  private companion object {
    val logger = Logger.withTag("JobDocumentReleaser")
  }
}
