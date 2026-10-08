package dev.fanfly.wingslog.feature.tasks.suggestions.datamanager

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.ai.AiJobId
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.core.storage.AiJobDocumentStore
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.thing.Attachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one owner of the documents picked for an AI run (docs/ai/task_population_design.md §8.2),
 * from the moment a file is picked until a task holds it or it is let go. No record holds a picked
 * document until the user accepts, so each is noted here, first as picked and then against the
 * job it goes to, and released:
 *
 * - when the user drops it before a run starts (removed, the sheet closed, the run refused),
 *   through [letGo];
 * - on accept and on dismiss, through [release];
 * - at the start of each app session, for runs the server no longer has (closed elsewhere, or
 *   expired), runs another user started on this device, and files picked in a process that died
 *   before saying what became of them, through [releaseGone].
 *
 * Picking and letting go run on this object's own scope, so a screen closing part-way through
 * neither strands a stored file nor drops its release.
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

  /**
   * The picked documents this process is still holding for a screen, by attachment id. A picked
   * row not among them was left by a process that died, and nothing will let it go but
   * [releaseGone].
   */
  private val heldHere = MutableStateFlow(emptySet<String>())

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

  /**
   * Stores [file] on this device for a run on [thingId], queued to upload, and notes it as picked.
   * Runs to its end even if the caller is cancelled (the sheet closed): what it stored is then let
   * go of, since nothing will list it.
   */
  suspend fun pick(thingId: String, file: PickedFile, maxBytes: Long): Result<Attachment> {
    val storing = scope.async {
      runCatching {
        attachments().addPickedFile(thingId, file, displayName = file.name, maxBytes = maxBytes)
      }.onSuccess { hold(thingId, it) }
    }
    return try {
      storing.await()
    } catch (e: CancellationException) {
      scope.launch { storing.await().getOrNull()?.let { letGoNow(listOf(it)) } }
      throw e
    }
  }

  /**
   * Lets go of picked [documents] no run took: removed from the sheet, the sheet closed, or the
   * run refused. Returns at once; the release finishes on this object's scope.
   */
  fun letGo(documents: List<Attachment>) {
    if (documents.isEmpty()) return
    scope.launch { letGoNow(documents) }
  }

  /**
   * Notes [documents] against the signed-in user's run [jobId] on [thingId]: from here the run
   * lets them go, so they stop being merely picked.
   */
  suspend fun record(jobId: AiJobId, thingId: String, documents: List<Attachment>) {
    if (documents.isEmpty()) return
    val uid = currentUid() ?: return
    store.record(uid, jobId.value, thingId, documents)
    forgetPicked(uid, documents)
  }

  /** The documents the signed-in user's run [jobId] was started with. */
  suspend fun documentsOf(jobId: AiJobId): List<Attachment> {
    val uid = currentUid() ?: return emptyList()
    return store.forJob(uid, jobId.value).map { it.attachment }
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
        if (key.jobId == PICKED) {
          // No job to ask about: an orphan is one no screen in this process holds.
          val orphans = rows.map { it.attachment }
            .filterNot { it.id in heldHere.value }
          orphans.forEach { attachments().release(it, owner = null) }
          store.forget(key.uid, PICKED, orphans.map { it.id })
          return@forEach
        }
        val gone = key.uid != uid || client().isGone(AiJobId(key.jobId))
        if (gone) releaseJob(key.uid, key.jobId, rows.map { it.attachment })
      }
  }

  private suspend fun releaseJob(uid: String, jobId: String, documents: List<Attachment>) {
    documents.forEach { attachments().release(it, owner = null) }
    store.forget(uid, jobId)
  }

  private suspend fun hold(thingId: String, document: Attachment) {
    heldHere.update { it + document.id }
    val uid = currentUid() ?: return
    store.record(uid, PICKED, thingId, listOf(document))
  }

  private suspend fun letGoNow(documents: List<Attachment>) {
    try {
      documents.forEach { attachments().release(it, owner = null) }
      forgetPicked(currentUid(), documents)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Still noted as picked, so the next process's first session lets it go.
      logger.w(e) { "Letting go of a picked document failed" }
    }
  }

  private suspend fun forgetPicked(uid: String?, documents: List<Attachment>) {
    val ids = documents.map { it.id }
    heldHere.update { it - ids.toSet() }
    if (uid != null) store.forget(uid, PICKED, ids)
  }

  private data class JobKey(val uid: String, val jobId: String)

  private companion object {
    /** The job id picked documents are noted under until a run takes them; no real job's id. */
    const val PICKED = "picked"

    val logger = Logger.withTag("JobDocumentReleaser")
  }
}
