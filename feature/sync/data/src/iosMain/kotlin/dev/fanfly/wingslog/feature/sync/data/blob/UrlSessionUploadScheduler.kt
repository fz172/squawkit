package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.storage.FirebaseStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue

/**
 * iOS [UploadScheduler] that uses a background [NSURLSession] so uploads survive process
 * suspension. Each upload is initiated via the Firebase Storage REST resumable upload API;
 * the session URI is persisted in `blob_object.resume_url` so it can be reused after a kill.
 *
 * A [BGProcessingTask] is registered for periodic scan-and-schedule ticks at OS-chosen moments
 * (typically charging + Wi-Fi). Register the task identifier in `Info.plist` under
 * `BGTaskSchedulerPermittedIdentifiers` and call [registerBgTasks] before
 * `application:didFinishLaunchingWithOptions:` returns.
 *
 * Downloads and deletes fall back to in-process coroutines (foreground-only); those operations
 * are user-triggered and short-lived.
 */
@OptIn(ExperimentalForeignApi::class)
class UrlSessionUploadScheduler(
  private val blobs: LocalBlobStore,
  private val fs: BlobFilesystem,
  private val auth: FirebaseAuth,
  private val storage: FirebaseStorage,
  private val db: WingsLogDatabase,
  private val httpClient: HttpClient,
  private val downloadDriver: BlobDownloadDriver,
  private val deleteDriver: BlobDeleteDriver,
  private val uploadDriver: BlobUploadDriver,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) : UploadScheduler {

  private val log = Logger.withTag(TAG)
  private var scope = newScope()

  private val delegate = BlobUploadDelegate(blobs, db, writeLock)

  // Background URLSession — iOS reconnects to this session automatically on relaunch using
  // the same identifier, and delivers pending completion events to the delegate.
  private var _urlSession: NSURLSession = createSession()

  // --- UploadScheduler ---

  override fun scheduleUpload(blobId: BlobId) {
    scope.launch { initiateUpload(blobId) }
  }

  override fun scheduleDownload(blobId: BlobId) {
    scope.launch { downloadDriver.runOnce(blobId) }
  }

  override fun scheduleDelete(blobId: BlobId) {
    scope.launch { deleteDriver.runOnce(blobId) }
  }

  override fun cancelAll() {
    _urlSession.getAllTasksWithCompletionHandler { tasks ->
      tasks?.forEach { (it as? platform.Foundation.NSURLSessionTask)?.cancel() }
    }
    scope.cancel()
    scope = newScope()
  }

  // --- BGProcessingTask ---

  fun registerBgTasks() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
      identifier = BG_SCAN_TASK_ID,
      usingQueue = null,
      launchHandler = { task ->
        if (task != null) handleBgScanTask(task as BGProcessingTask)
      },
    )
    log.d { "registered BGProcessingTask identifier $BG_SCAN_TASK_ID" }
  }

  private fun handleBgScanTask(task: BGProcessingTask) {
    task.expirationHandler = { task.setTaskCompletedWithSuccess(false) }
    scope.launch {
      val uid = auth.currentUser?.uid
      if (uid != null) {
        val prefix = "/users/$uid/%"
        db.schemaQueries
          .selectPendingUploads(scopePrefix = prefix, limit = 50)
          .executeAsList()
          .forEach { row -> initiateUpload(BlobId(row.id)) }
      }
      scheduleBgScanTask()
      task.setTaskCompletedWithSuccess(true)
    }
  }

  private fun scheduleBgScanTask() {
    val request = BGProcessingTaskRequest(identifier = BG_SCAN_TASK_ID).apply {
      requiresNetworkConnectivity = true
      requiresExternalPower = false
    }
    try {
      BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    } catch (e: Exception) {
      log.w(e) { "failed to schedule BGProcessingTask" }
    }
  }

  // --- Upload initiation ---

  private suspend fun initiateUpload(id: BlobId) {
    val row = db.schemaQueries.selectBlobById(id.value)
      .executeAsOneOrNull() ?: return
    if (row.deleted) return

    // Foreign-hosted (shared aircraft) blobs live in the HOST's tree, which storage.rules deny a
    // direct write to. Route them through the shared upload driver → broker (getBlobUploadSession
    // mints a session, App Check attached by the native SDK; the PUT runs in-process). Own-tree
    // blobs fall through to the background-URLSession path below, unchanged.
    val ownerUid = row.scope_path.trim('/')
      .split('/')
      .getOrNull(1)
    val currentUid = auth.currentUser?.uid
    if (ownerUid != null && currentUid != null && ownerUid != currentUid) {
      uploadDriver.runOnce(id)
      return
    }

    when (row.remote_state) {
      RemoteState.Synced, RemoteState.RemoteOnly -> return
      RemoteState.Uploading -> {
        val existingUri = row.resume_url
        if (existingUri != null) {
          // A background URLSession task already exists for this session URI.
          // iOS will deliver the completion event via the delegate; nothing to do here.
          log.d { "upload ${id.value} already in-flight via existing session URI; waiting for delegate" }
          return
        }
        // Stuck UPLOADING without a session URI (process killed between markUploading and
        // creating the URLSession task). Reset so we can start fresh.
        blobs.markFailedTransient(id)
      }

      RemoteState.LocalOnly -> {
        // Clear any stale resume_url left by a prior kill between setResumeUrl and markUploading.
        if (row.resume_url != null) writeLock.withLock {
          db.schemaQueries.clearResumeUrl(
            id.value
          )
        }
      }
    }

    val user = auth.currentUser
    if (user == null || user.isAnonymous) {
      log.d { "upload deferred for ${id.value}: no permanent auth user" }
      return
    }

    val idToken = try {
      user.getIdToken(forceRefresh = false) ?: return
    } catch (e: Exception) {
      log.w(e) { "getIdToken failed; deferring upload for ${id.value}" }
      return
    }

    val remotePath =
      row.remote_path ?: "${row.scope_path.trim('/')}/blobs/${id.value}"
    val bucket = storage.reference.bucket

    val sessionUri = createResumableSession(
      idToken = idToken,
      bucket = bucket,
      remotePath = remotePath,
      contentType = row.content_type,
      sizeBytes = row.size_bytes,
    ) ?: return

    writeLock.withLock {
      db.schemaQueries.setResumeUrl(
        resumeUrl = sessionUri,
        id = id.value
      )
    }
    blobs.markUploading(id)
    enqueueBackgroundUpload(
      id = id,
      sessionUri = sessionUri,
      contentType = row.content_type,
      sizeBytes = row.size_bytes,
      relativePath = row.relative_path,
    )
  }

  private fun enqueueBackgroundUpload(
    id: BlobId,
    sessionUri: String,
    contentType: String?,
    sizeBytes: Long,
    relativePath: String,
  ) {
    val fileUrl = NSURL.URLWithString(fs.uriFor(relativePath)) ?: run {
      log.e { "could not create NSURL for blob ${id.value}" }
      return
    }
    val sessionNsUrl = NSURL.URLWithString(sessionUri) ?: run {
      log.e { "invalid session URI for blob ${id.value}" }
      return
    }
    val request = NSMutableURLRequest.requestWithURL(sessionNsUrl)
    request.setHTTPMethod("PUT")
    request.setValue(
      sizeBytes.toString(),
      forHTTPHeaderField = "Content-Length"
    )
    request.setValue(
      contentType ?: "application/octet-stream",
      forHTTPHeaderField = "Content-Type",
    )
    // Firebase Storage GCS resumable upload — finalize in a single PUT.
    request.setValue(
      "upload, finalize",
      forHTTPHeaderField = "X-Goog-Upload-Command"
    )
    request.setValue("0", forHTTPHeaderField = "X-Goog-Upload-Offset")

    @Suppress("UNCHECKED_CAST")
    val task = _urlSession.uploadTaskWithRequest(
      request as platform.Foundation.NSURLRequest,
      fromFile = fileUrl,
    )
    task.taskDescription = id.value  // persisted across process kills
    task.resume()
    log.d { "enqueued background upload task for ${id.value}" }
  }

  private suspend fun createResumableSession(
    idToken: String,
    bucket: String,
    remotePath: String,
    contentType: String?,
    sizeBytes: Long,
  ): String? {
    return try {
      val response: HttpResponse = httpClient.post(
        "https://firebasestorage.googleapis.com/v0/b/$bucket/o",
      ) {
        parameter("uploadType", "resumable")
        parameter("name", remotePath)
        header("Authorization", "Firebase $idToken")
        header("X-Goog-Upload-Protocol", "resumable")
        header("X-Goog-Upload-Command", "start")
        header(
          "X-Goog-Upload-Header-Content-Type",
          contentType ?: "application/octet-stream"
        )
        header("X-Goog-Upload-Header-Content-Length", sizeBytes.toString())
        contentType(ContentType.Application.Json)
        setBody("{}")
      }
      val uri = response.headers["X-Goog-Upload-URL"]
      if (uri == null) log.w { "no X-Goog-Upload-URL in response for $remotePath" }
      uri
    } catch (e: Exception) {
      log.w(e) { "createResumableSession failed for $remotePath" }
      null
    }
  }

  private fun createSession(): NSURLSession {
    val config = NSURLSessionConfiguration
      .backgroundSessionConfigurationWithIdentifier(SESSION_ID)
    config.discretionary = true
    config.sessionSendsLaunchEvents = true
    return NSURLSession.sessionWithConfiguration(
      configuration = config,
      delegate = delegate,
      delegateQueue = null,
    )
  }

  private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  companion object {
    private const val TAG = "UrlSessionUploadScheduler"
    const val SESSION_ID = "dev.fanfly.wingslog.blob-upload"
    const val BG_SCAN_TASK_ID = "dev.fanfly.wingslog.blob-scan"
  }
}
