package dev.fanfly.wingslog.feature.sync.data.blob

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.functions.functions
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * GitLive-functions + ktor implementation of [AttachmentBroker] (design §9.2).
 */
class HttpsAttachmentBroker(
  private val auth: FirebaseAuth,
  private val httpClient: HttpClient,
  private val appCheck: AppCheckTokenProvider,
  private val functionsRegion: String = "us-central1",
) : AttachmentBroker {

  private val log = Logger.withTag(TAG)

  // Firebase access is deferred to first use — the broker is constructed EAGERLY in the Koin graph
  // (SyncEngine → UploadScheduler → drivers → broker), so eager access ran during app startup.
  private val functions by lazy { Firebase.functions(functionsRegion) }

  /** `https://{region}-{projectId}.cloudfunctions.net`, resolved on first `streamBlob` download. */
  private val functionsBaseUrl: String by lazy {
    // NOT `Firebase.app.options.projectId` — that getter NPEs in gitlive on iOS (it crashed startup
    // eagerly, then failed downloads once lazy). `firebaseProjectId()` reads a platform-safe source.
    val projectId = firebaseProjectId()
    checkNotNull(projectId) { "Firebase projectId is required to reach the attachment broker" }
    "https://$functionsRegion-$projectId.cloudfunctions.net"
  }

  override suspend fun upload(
    hostUid: String,
    aircraftId: String,
    blobId: String,
    contentType: String?,
    bytes: ByteArray,
  ) {
    // §9.2: the callable checks App Check + membership + entitlement and mints a resumable session
    // into the host's tree. App Check is attached by the native SDK, so no token plumbing here.
    val session = functions.httpsCallable("getBlobUploadSession")
      .invoke(
        UploadSessionRequest(
          hostUid = hostUid,
          aircraftId = aircraftId,
          blobId = blobId,
          contentType = contentType,
        )
      )
      .data<UploadSessionResponse>()
    val uploadUrl = session.uploadUrl
    check(uploadUrl.isNotBlank()) { "getBlobUploadSession returned no uploadUrl for $blobId" }

    // Single-request finalize of the GCS resumable session: PUT the whole body with a Content-Range
    // that both offsets from 0 and declares the total size, so the session completes in one call.
    val contentRange =
      if (bytes.isEmpty()) "bytes */0" else "bytes 0-${bytes.size - 1}/${bytes.size}"
    val response = httpClient.put(uploadUrl) {
      header(HttpHeaders.ContentRange, contentRange)
      setBody(bytes)
    }
    check(response.status.isSuccess()) {
      "brokered upload PUT for $blobId failed: ${response.status}"
    }
    log.i { "brokered upload of $blobId" }
  }

  override suspend fun download(
    hostUid: String,
    aircraftId: String,
    blobId: String,
  ): ByteArray {
    // §9.2.1: the proxy verifies App Check + ID token + the ACL on THIS request, then streams bytes.
    // Both headers are required; a missing App Check token would earn a flat 401, so fail early.
    log.d { "brokered download $blobId: fetching ID token" }
    val idToken = auth.currentUser?.getIdToken(false)
      ?: error("brokered download of $blobId needs a signed-in user")
    log.d { "brokered download $blobId: fetching App Check token" }
    val appCheckToken = appCheck.token()
      ?: error("brokered download of $blobId needs an App Check token (unavailable on this platform)")

    log.d { "brokered download $blobId: GET $functionsBaseUrl/streamBlob" }
    val response = httpClient.get("$functionsBaseUrl/streamBlob") {
      header(HttpHeaders.Authorization, "Bearer $idToken")
      header(APP_CHECK_HEADER, appCheckToken)
      parameter("hostUid", hostUid)
      parameter("acId", aircraftId)
      parameter("blobId", blobId)
    }
    check(response.status.isSuccess()) {
      "brokered download of $blobId failed: ${response.status}"
    }
    val bytes = response.readRawBytes()
    log.i { "brokered download of $blobId (${bytes.size}B)" }
    return bytes
  }

  companion object {
    private const val TAG = "HttpsAttachmentBroker"
    private const val APP_CHECK_HEADER = "X-Firebase-AppCheck"
  }
}

@Serializable
private data class UploadSessionRequest(
  val hostUid: String,
  val aircraftId: String,
  val blobId: String,
  val contentType: String? = null,
)

@Serializable
private data class UploadSessionResponse(val uploadUrl: String = "")
