import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminStorage } from "../config/firebaseAdmin.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { attachmentsEnabled, blobObjectPath, isShareMember, loadShare } from "./blobBroker.js";

/**
 * The write half of the attachment broker (design §9.2). Mints a resumable upload session into the
 * HOST's tree for a member of the shared aircraft, so a technician can attach files to someone
 * else's plane while `storage.rules` stay uid-scoped and closed.
 *
 * A session (not a proxy) is safe for writes: an upload is not a content leak, and a write by a
 * just-revoked member is inert — the object lands unreferenced, because making it visible needs a
 * Firestore record write the ACL denies instantly, and the orphan is reclaimed by the sweep (§9.6).
 * Membership and entitlement are still checked here at session-creation time.
 *
 * `hostUid` is routing only (#204): it names WHICH share doc to open, not authorization — the
 * caller's rights are checked against the ACL found at that path.
 */
type UploadSessionRequest = {
  hostUid: string;
  aircraftId: string;
  blobId: string;
  contentType?: string;
};

export const getBlobUploadSession = onCall<
  UploadSessionRequest,
  Promise<{ uploadUrl: string }>
>({ region: FUNCTION_REGION, enforceAppCheck: true }, async (request) => {
  const { uid } = requireAuthenticatedApp(request);
  const { hostUid, aircraftId, blobId, contentType } = parseRequest(request.data);

  const share = await loadShare(hostUid, aircraftId);
  if (share == null || !isShareMember(share, uid)) {
    throw new HttpsError("permission-denied", "Not a member of this shared aircraft.");
  }
  if (!attachmentsEnabled(share)) {
    throw new HttpsError("failed-precondition", "Attachments are not enabled for this aircraft.");
  }

  const file = adminStorage.bucket().file(blobObjectPath(hostUid, aircraftId, blobId));
  const [uploadUrl] = await file.createResumableUpload({
    // A resumable session created server-side has no browser origin bound to it, so GCS returns no
    // Access-Control-Allow-Origin on the client's PUT and the browser blocks it ("Failed to fetch")
    // — bucket CORS alone does NOT cover resumable session URLs. Binding the caller's Origin here is
    // exactly what this option is for. Native clients send no Origin (undefined), which is fine —
    // they don't do CORS. Origin is client-supplied but only governs which browser origins may read
    // the response; App Check + membership above remain the real authorization (P8.4 #245).
    origin: callerOrigin(request.rawRequest.headers.origin),
    metadata: {
      contentType: contentType ?? "application/octet-stream",
      // Authorship, attributable across accounts like `writer_uid` for records (§7.5).
      metadata: { writerUid: uid },
    },
  });

  return { uploadUrl };
});

/** The request's `Origin` header as a single string, or `undefined` for a non-browser caller. */
function callerOrigin(origin: string | string[] | undefined): string | undefined {
  if (Array.isArray(origin)) return origin[0];
  return origin;
}

function parseRequest(data: unknown): UploadSessionRequest {
  const obj = (data ?? {}) as Record<string, unknown>;
  const hostUid = typeof obj.hostUid === "string" ? obj.hostUid.trim() : "";
  const aircraftId = typeof obj.aircraftId === "string" ? obj.aircraftId.trim() : "";
  const blobId = typeof obj.blobId === "string" ? obj.blobId.trim() : "";
  const contentType = typeof obj.contentType === "string" ? obj.contentType.trim() : undefined;
  if (hostUid.length === 0) {
    throw new HttpsError("invalid-argument", "hostUid is required.");
  }
  if (aircraftId.length === 0 || blobId.length === 0) {
    throw new HttpsError("invalid-argument", "getBlobUploadSession requires aircraftId and blobId.");
  }
  return { hostUid, aircraftId, blobId, contentType };
}
