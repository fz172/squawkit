import { getAppCheck } from "firebase-admin/app-check";
import { logger } from "firebase-functions/v2";
import { onRequest, type Request } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { adminAuth, adminStorage } from "../config/firebaseAdmin.js";
import { authorizeBlobRead, blobObjectPath } from "./blobBroker.js";

/**
 * The read half of the attachment broker (design §9.2 / §9.2.1).
 *
 * Downloads must NOT leak and read-revocation must be instant, with the ACL checked immediately
 * before every download. A signed URL cannot meet that — it is a bearer credential checked once at
 * mint time. So this is an authorizing STREAMING PROXY: an authenticated request that re-checks the
 * `aircraft_shares` ACL on every call and, only if the caller is still a member, streams the bytes
 * through itself. Nothing bearer is ever handed to the client, so there is no URL to leak and the
 * first request after a revoke is refused.
 *
 * Contract: `GET streamBlob?hostUid=&acId=&blobId=` with headers
 *   `Authorization: Bearer <Firebase ID token>` and `X-Firebase-AppCheck: <App Check token>`.
 * Both are required — the App Check header matches the `enforceAppCheck: true` posture the callables
 * use; the client (P8.4 #245) attaches both.
 */
// `cors: true` so the web app (a different origin than cloudfunctions.net) can fetch the stream.
// It only governs which browser origins may READ the response — the actual gate is the required App
// Check and ID-token headers below, which a cross-origin site cannot forge.
export const streamBlob = onRequest(
  { region: FUNCTION_REGION, cors: true },
  async (req, res): Promise<void> => {
  if (req.method !== "GET") {
    res.status(405).end();
    return;
  }

  const callerUid = await verifyCaller(req);
  if (callerUid == null) {
    res.status(401).end();
    return;
  }

  const hostUid = strParam(req.query.hostUid);
  const acId = strParam(req.query.acId);
  const blobId = strParam(req.query.blobId);
  if (hostUid.length === 0 || acId.length === 0 || blobId.length === 0) {
    res.status(400).end();
    return;
  }

  // Re-checked here, on THIS request — the whole point of the proxy over a signed URL.
  if (!(await authorizeBlobRead(callerUid, hostUid, acId))) {
    res.status(403).end();
    return;
  }

  await pipeBlob(hostUid, acId, blobId, res);
});

/** The subset of an Express response `pipeBlob` needs — satisfied by the real response and by tests. */
export type BlobResponse = NodeJS.WritableStream & {
  setHeader(name: string, value: string | number): void;
  status(code: number): BlobResponse;
  destroy(error?: Error): void;
};

/**
 * Stream one blob's bytes from the host tree to [res]. `404` if the object is gone (deleted, or a
 * ref that arrived before its bytes). Exported so the emulator test can drive it directly without
 * standing up token verification.
 */
export async function pipeBlob(
  hostUid: string,
  acId: string,
  blobId: string,
  res: BlobResponse,
): Promise<void> {
  const file = adminStorage.bucket().file(blobObjectPath(hostUid, acId, blobId));

  let contentType = "application/octet-stream";
  let size: number | undefined;
  try {
    const [metadata] = await file.getMetadata();
    if (typeof metadata.contentType === "string") contentType = metadata.contentType;
    const parsed = Number(metadata.size);
    if (Number.isFinite(parsed)) size = parsed;
  } catch {
    res.status(404).end();
    return;
  }

  res.setHeader("Content-Type", contentType);
  if (size != null) res.setHeader("Content-Length", String(size));
  // The bytes are member-controlled (any uploader picks the content type), so never let a browser
  // sniff them into something executable or render them inline in this origin: force a download and
  // pin the declared type. This is the cross-account content boundary — treat every blob as hostile.
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Content-Disposition", "attachment");
  // Private bytes with immediate revocation — never let a shared cache serve them after a revoke.
  res.setHeader("Cache-Control", "private, no-store");
  res.status(200);

  await new Promise<void>((resolve) => {
    // A source error can strike after the head is flushed, so there is no clean status left to send —
    // `.pipe` also won't tear down the destination on its own. Log it, destroy the response so the
    // request fails loudly instead of hanging, and resolve so the handler never rejects unhandled.
    const onError = (err: Error) => {
      logger.error("streamBlob failed mid-stream", { hostUid, acId, blobId, err });
      res.destroy(err);
      resolve();
    };
    file
      .createReadStream()
      .on("error", onError)
      .pipe(res)
      .on("finish", resolve)
      .on("error", onError);
  });
}

/**
 * Resolve the caller from an HTTPS request: App Check, then a Firebase ID token. Returns `null` on
 * any failure so the handler answers a flat `401` and never distinguishes "bad token" from "no
 * token" to an unauthenticated caller. `checkRevoked` is on so a disabled or session-revoked account
 * is also refused, not just an ACL non-member.
 */
async function verifyCaller(req: Request): Promise<string | null> {
  const appCheckToken = req.header("X-Firebase-AppCheck");
  if (appCheckToken == null) return null;
  try {
    await getAppCheck().verifyToken(appCheckToken);
  } catch {
    return null;
  }

  const match = /^Bearer (.+)$/.exec(req.header("Authorization") ?? "");
  if (match == null) return null;
  try {
    const decoded = await adminAuth.verifyIdToken(match[1], true);
    return decoded.uid;
  } catch {
    return null;
  }
}

function strParam(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}
