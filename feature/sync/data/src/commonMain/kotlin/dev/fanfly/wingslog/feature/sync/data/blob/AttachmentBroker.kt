package dev.fanfly.wingslog.feature.sync.data.blob

import dev.fanfly.wingslog.core.storage.blob.BlobRef

/**
 * Client half of the attachment broker (design §9.2). Blobs on a **foreign-hosted** (shared)
 * aircraft live under the host's tree at `users/{hostUid}/aircraft/{acId}/blobs/{blobId}`, which
 * `storage.rules` deny cross-account. The broker is the only door across trees:
 *
 * - **Upload** mints a resumable-upload session into the host's tree via the `getBlobUploadSession`
 *   callable (App Check + membership + entitlement checked at session-creation time), then PUTs the
 *   bytes to that session URL. A write by a just-revoked member is inert — the object lands
 *   unreferenced because making it visible needs a Firestore write the ACL denies (§9.6).
 * - **Download** streams bytes through the `streamBlob` authorizing proxy, which re-checks the ACL
 *   on every request and hands out no bearer URL, so a revoked member is refused on their very next
 *   request (§9.2.1).
 *
 * Own-tree blobs never touch the broker — the drivers keep the direct Firebase Storage path for
 * those. This is only invoked when a blob's owning uid differs from the signed-in user.
 */
interface AttachmentBroker {
  /**
   * Upload [bytes] for a foreign-hosted blob through a brokered resumable session. Throws on any
   * failure (membership/entitlement denied, session mint failed, transfer failed) so the caller can
   * treat it as a transient upload failure and retry.
   */
  suspend fun upload(
    hostUid: String,
    aircraftId: String,
    blobId: String,
    contentType: String?,
    bytes: ByteArray,
  )

  /**
   * Download the bytes of a foreign-hosted blob through the streaming proxy. Throws on any failure
   * (revoked, entitlement lapsed, App Check token unavailable, object gone) so the caller treats it
   * as a transient download failure and retries.
   */
  suspend fun download(
    hostUid: String,
    aircraftId: String,
    blobId: String,
  ): ByteArray
}

/**
 * Where a blob's bytes live, parsed from its scope path `["users", ownerUid, "aircraft", acId]`.
 * [ownerUid] is the tree the object sits in — the signed-in user for an owned aircraft, the host for
 * a shared one. Returns `null` when the scope is not an aircraft-child path (nothing to broker).
 */
data class BlobLocation(val ownerUid: String, val aircraftId: String) {
  /** A blob is foreign — and must go through the broker — when its owning tree isn't the caller's. */
  fun isForeign(currentUid: String?): Boolean = currentUid != null && ownerUid != currentUid

  companion object {
    fun of(ref: BlobRef): BlobLocation? {
      val segs = ref.scope.segments
      if (segs.size < 4 || segs[0] != "users" || segs[2] != "aircraft") return null
      val ownerUid = segs[1]
      val aircraftId = segs[3]
      if (ownerUid.isBlank() || aircraftId.isBlank()) return null
      return BlobLocation(ownerUid, aircraftId)
    }
  }
}
